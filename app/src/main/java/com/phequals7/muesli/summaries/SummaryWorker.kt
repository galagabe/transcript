package com.phequals7.muesli.summaries

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.phequals7.muesli.BuildConfig
import com.phequals7.muesli.data.SharedStore
import com.phequals7.muesli.data.entity.RecordingSession
import com.phequals7.muesli.meetings.MeetingTemplate
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Deferred meeting summary (Clinitex). Recording never waits on the network:
 * the service enqueues this worker, WorkManager holds it until the phone is
 * online, then [SummaryRunner] calls the n8n webhook.
 *
 * summaryState: queued → generating → completed | failed
 * ("unavailable" when there is nothing to summarize or no webhook URL).
 */
class SummaryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SummaryWorker"
        private const val KEY_SESSION_ID = "session_id"
        private const val WORK_TAG = "clinitex-summary"

        /** Retryable failures (server down, 5xx) give up after this many runs. */
        const val MAX_ATTEMPTS = 8

        private fun uniqueWorkName(sessionId: String) = "summary-$sessionId"

        /**
         * Marks the session `queued` and schedules its summary for the next
         * time the network is available. Re-enqueuing the same session
         * replaces the pending request ("Relancer la synthèse").
         */
        suspend fun enqueue(context: Context, sessionId: String) {
            val appContext = context.applicationContext
            val store = SharedStore(appContext)
            store.getRecordingSessionById(sessionId)?.let {
                store.updateRecordingSession(it.copy(summaryState = SummaryRunner.STATE_QUEUED))
            }

            val request = OneTimeWorkRequestBuilder<SummaryWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_SESSION_ID to sessionId))
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(appContext)
                .enqueueUniqueWork(uniqueWorkName(sessionId), ExistingWorkPolicy.REPLACE, request)
        }
    }

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
        val runner = SummaryRunner(
            sessions = StoreSummarySessions(SharedStore(applicationContext)),
            client = ClinitexSummaryClient(
                endpointUrl = BuildConfig.CLINITEX_SUMMARY_URL,
                deviceToken = BuildConfig.CLINITEX_DEVICE_TOKEN,
            ),
            appVersion = BuildConfig.VERSION_NAME,
            log = { Log.w(TAG, it) },
        )
        return when (runner.run(sessionId, attempt = runAttemptCount, maxAttempts = MAX_ATTEMPTS)) {
            SummaryRunner.Outcome.COMPLETED, SummaryRunner.Outcome.SKIPPED -> Result.success()
            SummaryRunner.Outcome.RETRY -> Result.retry()
            SummaryRunner.Outcome.FAILED -> Result.failure()
        }
    }
}

/** Session access needed by [SummaryRunner]; faked in unit tests. */
interface SummarySessions {
    suspend fun session(id: String): RecordingSession?
    suspend fun transcript(sessionId: String): String?
    /** Speaker number → name given by the user (lot 3); empty when none. */
    suspend fun speakerNames(sessionId: String): Map<Int, String> = emptyMap()
    suspend fun update(session: RecordingSession)
}

private class StoreSummarySessions(private val store: SharedStore) : SummarySessions {
    override suspend fun session(id: String) = store.getRecordingSessionById(id)
    override suspend fun transcript(sessionId: String) = store.getTranscriptForSession(sessionId)?.text
    override suspend fun speakerNames(sessionId: String) = store.getSpeakerNames(sessionId)
    override suspend fun update(session: RecordingSession) = store.updateRecordingSession(session)
}

/**
 * One summary attempt and its summaryState transitions, free of WorkManager
 * so it can be unit-tested against MockWebServer.
 */
class SummaryRunner(
    private val sessions: SummarySessions,
    private val client: ClinitexSummaryClient,
    private val appVersion: String,
    private val log: (String) -> Unit = {},
) {

    companion object {
        const val STATE_QUEUED = "queued"
        const val STATE_GENERATING = "generating"
        const val STATE_COMPLETED = "completed"
        const val STATE_FAILED = "failed"
        const val STATE_UNAVAILABLE = "unavailable"
    }

    enum class Outcome { COMPLETED, RETRY, FAILED, SKIPPED }

    /** @param attempt 0-based run count (WorkManager runAttemptCount). */
    suspend fun run(sessionId: String, attempt: Int, maxAttempts: Int): Outcome {
        val session = sessions.session(sessionId) ?: return Outcome.SKIPPED
        val transcript = sessions.transcript(sessionId).orEmpty()

        if (transcript.isBlank() || !client.isConfigured()) {
            sessions.update(session.copy(summaryState = STATE_UNAVAILABLE))
            return Outcome.SKIPPED
        }

        sessions.update(session.copy(summaryState = STATE_GENERATING))
        val template = MeetingTemplate.fromId(session.templateId)
        val request = ClinitexSummaryClient.SummaryRequest(
            sessionId = session.id,
            title = session.title,
            templateId = template.id,
            templateInstructions = template.instructions,
            // n8n tells Claude "Intervenant N = name"; the transcript keeps its "Intervenant N" labels.
            speakerNames = speakerNamesPayload(sessions.speakerNames(sessionId)),
            manualNotes = session.manualNotes,
            transcript = transcript,
            durationSec = durationSec(session),
            appVersion = appVersion,
        )

        return try {
            val response = client.summarize(request)
            val latest = sessions.session(sessionId) ?: return Outcome.SKIPPED
            sessions.update(
                latest.copy(
                    summaryText = response.notes,
                    summaryState = STATE_COMPLETED,
                    title = response.title.ifBlank { latest.title },
                )
            )
            Outcome.COMPLETED
        } catch (e: CancellationException) {
            // Replaced by a newer enqueue or stopped by WorkManager: not a failure.
            throw e
        } catch (t: Throwable) {
            val latest = sessions.session(sessionId) ?: return Outcome.SKIPPED
            val retryable = t is ClinitexSummaryClient.SummaryException && t.retryable
            if (retryable && attempt + 1 < maxAttempts) {
                log("summary attempt ${attempt + 1}/$maxAttempts failed, retrying: ${t.message}")
                sessions.update(latest.copy(summaryState = STATE_QUEUED))
                Outcome.RETRY
            } else {
                log("summary failed for good: ${t.message}")
                sessions.update(
                    latest.copy(
                        summaryText = client.failureNotes(
                            transcript = transcript,
                            meetingTitle = latest.title,
                            error = t,
                            manualNotes = latest.manualNotes.ifBlank { null },
                        ),
                        summaryState = STATE_FAILED,
                    )
                )
                Outcome.FAILED
            }
        }
    }

    /** {"1": "Commercial Clinitex", "2": "Prospect"}, in speaker order, blank names left out. */
    private fun speakerNamesPayload(names: Map<Int, String>): Map<String, String> =
        names.entries
            .sortedBy { it.key }
            .mapNotNull { (speaker, name) -> name.trim().takeIf { it.isNotEmpty() }?.let { speaker.toString() to it } }
            .toMap()

    private fun durationSec(session: RecordingSession): Long {
        val start = session.startedAt ?: return 0
        val end = session.endedAt ?: return 0
        return ((end - start) / 1000L).coerceAtLeast(0)
    }
}
