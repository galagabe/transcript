package fr.clinitex.transcription.summaries

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.clinitex.transcription.data.SharedStore
import fr.clinitex.transcription.data.entity.RecordingSession
import java.util.concurrent.TimeUnit

/**
 * Lot 6b: summaries that never came back. [SummaryWorker] gives up after
 * [SummaryWorker.MAX_ATTEMPTS] runs (≈ 2 h), and a summary left `generating`
 * when the app was killed has no work behind it any more. This worker checks
 * every 30 min (when online) and at every app launch and re-enqueues them;
 * the "Forcer la reconnexion" banner runs the same pass in [Mode.FORCE].
 */
class SummaryRecoveryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SummaryRecovery"
        private const val PERIODIC_WORK = "summary-recovery-periodic"
        private const val LAUNCH_WORK = "summary-recovery-launch"
        const val INTERVAL_MINUTES = 30L

        /** Check every 30 min (kept if already scheduled) + one check at launch. Call at app launch. */
        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context.applicationContext)
            val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SummaryRecoveryWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                    .setConstraints(online)
                    .build(),
            )
            workManager.enqueueUniqueWork(
                LAUNCH_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SummaryRecoveryWorker>().setConstraints(online).build(),
            )
        }

        /**
         * "Forcer la reconnexion": every summary waiting or failed (permanent
         * failures included) is sent again right away, as expedited work.
         * @return number of summaries relaunched.
         */
        suspend fun forceAll(context: Context): Int = recover(context.applicationContext, SummaryRecovery.Mode.FORCE)

        private suspend fun recover(context: Context, mode: SummaryRecovery.Mode): Int {
            val store = SharedStore(context)
            val candidates = store.getRecordingSessions().filter(SummaryRecovery::isCandidate)
            val work = candidates.associate { it.id to SummaryWorker.workState(context, it.id) }
            val picked = SummaryRecovery.select(
                sessions = candidates,
                workState = { work[it] ?: SummaryWorkState.NONE },
                failure = store::summaryFailure,
                mode = mode,
            )
            for (session in picked) {
                when (mode) {
                    SummaryRecovery.Mode.AUTO -> {
                        if (session.summaryState == SummaryRunner.STATE_FAILED) store.recordSummaryAutoRetry(session.id)
                        SummaryWorker.enqueue(context, session.id, keepPending = true)
                    }
                    SummaryRecovery.Mode.FORCE -> SummaryWorker.enqueue(context, session.id, expedited = true)
                }
            }
            if (picked.isNotEmpty()) Log.i(TAG, "$mode: relaunched ${picked.size} summaries")
            return picked.size
        }
    }

    override suspend fun doWork(): Result {
        recover(applicationContext, SummaryRecovery.Mode.AUTO)
        return Result.success()
    }
}

/** What WorkManager holds for one session's summary. */
enum class SummaryWorkState {
    /** No pending work (never enqueued, finished, or lost when the app was killed). */
    NONE,
    /** Enqueued: waiting for the network or for its retry delay. */
    WAITING,
    /** Calling n8n right now: never interrupted. */
    RUNNING,
}

/**
 * Failure memory of a summary, kept in SharedPreferences (no Room change).
 * @param permanent the last failure cannot succeed by retrying as is (4xx, token
 *   refused, unreadable answer): only the user relaunches it.
 * @param autoRetries automatic relaunches since the user last asked for one.
 */
data class SummaryFailureInfo(val permanent: Boolean = false, val autoRetries: Int = 0)

/** Which summaries to relaunch, free of WorkManager so it can be unit-tested. */
object SummaryRecovery {

    enum class Mode { AUTO, FORCE }

    /** A failed-for-network summary is relaunched automatically this many times at most. */
    const val MAX_AUTO_RETRIES = 3

    private val RECOVERABLE_STATES = setOf(
        SummaryRunner.STATE_QUEUED,
        SummaryRunner.STATE_GENERATING,
        SummaryRunner.STATE_FAILED,
    )

    /** Transcribed session whose summary is waiting, in progress or failed. */
    fun isCandidate(session: RecordingSession): Boolean =
        session.phase == "completed" && session.summaryState in RECOVERABLE_STATES

    /**
     * - running work is never touched;
     * - waiting / in progress with no work behind it → relaunched;
     * - waiting with work enqueued → left alone in [Mode.AUTO], sent now in [Mode.FORCE];
     * - failed → [Mode.FORCE] always; [Mode.AUTO] only a network / server failure,
     *   at most [MAX_AUTO_RETRIES] times, so n8n and Claude are never called in a loop.
     */
    fun select(
        sessions: List<RecordingSession>,
        workState: (String) -> SummaryWorkState,
        failure: (String) -> SummaryFailureInfo,
        mode: Mode,
    ): List<RecordingSession> = sessions.filter { session ->
        if (!isCandidate(session)) return@filter false
        when (workState(session.id)) {
            SummaryWorkState.RUNNING -> false
            SummaryWorkState.WAITING -> mode == Mode.FORCE
            SummaryWorkState.NONE -> when {
                session.summaryState != SummaryRunner.STATE_FAILED -> true
                mode == Mode.FORCE -> true
                else -> failure(session.id).let { !it.permanent && it.autoRetries < MAX_AUTO_RETRIES }
            }
        }
    }
}
