package fr.clinitex.transcription.meetings

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.clinitex.transcription.data.SharedStore
import fr.clinitex.transcription.data.entity.RecordingSession
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Lot 5, Clinitex retention rule: recorded audio is kept 7 days after the
 * end of the recording, then deleted. Runs once a day and at every app
 * launch; the transcript and the summary stay in the app.
 */
class AudioPurgeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "AudioPurgeWorker"
        private const val DAILY_WORK = "audio-purge-daily"
        private const val LAUNCH_WORK = "audio-purge-launch"

        const val RETENTION_DAYS = 7
        val RETENTION_MS: Long = TimeUnit.DAYS.toMillis(RETENTION_DAYS.toLong())

        /** Daily purge (kept if already scheduled) + one immediate purge. Call at app launch. */
        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context.applicationContext)
            workManager.enqueueUniquePeriodicWork(
                DAILY_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AudioPurgeWorker>(1, TimeUnit.DAYS).build(),
            )
            workManager.enqueueUniqueWork(
                LAUNCH_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<AudioPurgeWorker>().build(),
            )
        }

        /** Date at which the session's audio is (or was) due for deletion; null without a recording end. */
        fun deletionDueAt(session: RecordingSession): Long? =
            AudioPurger.retentionStart(session)?.let { it + RETENTION_MS }
    }

    override suspend fun doWork(): Result {
        val store = SharedStore(applicationContext)
        val report = AudioPurger(
            sessions = object : AudioPurgeSessions {
                override suspend fun all() = store.getRecordingSessions()
                override suspend fun markAudioDeleted(sessionId: String, deletedAt: Long) =
                    store.markAudioDeleted(sessionId, deletedAt)
            },
            meetingsDir = File(applicationContext.filesDir, "meetings"),
        ).purge()
        if (report.total > 0) Log.i(TAG, "purged: $report")
        return Result.success()
    }
}

/** Session access needed by [AudioPurger]; faked in unit tests. */
interface AudioPurgeSessions {
    suspend fun all(): List<RecordingSession>
    suspend fun markAudioDeleted(sessionId: String, deletedAt: Long)
}

/**
 * Purge rules, free of WorkManager so they can be unit-tested:
 * - session WAV older than [AudioPurgeWorker.RETENTION_MS] since the end of the
 *   recording → file deleted, `audioFileName` = null, `audioDeletedAt` = now;
 * - `.tmp-*.wav` (temp audio of older versions) older than 24 h → deleted;
 * - any other WAV no session points to (capture interrupted by a crash)
 *   older than the retention period → deleted.
 * File ages come from the last write, so a recording in progress is never touched.
 */
class AudioPurger(
    private val sessions: AudioPurgeSessions,
    private val meetingsDir: File,
    private val now: () -> Long = System::currentTimeMillis,
) {

    companion object {
        val TEMP_MAX_AGE_MS: Long = TimeUnit.HOURS.toMillis(24)

        /** Retention counts from the end of the recording (its start as a fallback). */
        fun retentionStart(session: RecordingSession): Long? = session.endedAt ?: session.startedAt
    }

    data class Report(val sessionAudio: Int, val tempFiles: Int, val orphanFiles: Int) {
        val total: Int get() = sessionAudio + tempFiles + orphanFiles
    }

    suspend fun purge(): Report {
        val nowMs = now()
        val all = sessions.all()

        var sessionAudio = 0
        for (session in all) {
            val name = session.audioFileName ?: continue
            val start = retentionStart(session) ?: continue
            if (nowMs - start < AudioPurgeWorker.RETENTION_MS) continue
            val file = File(meetingsDir, name)
            // Missing file still clears the row (nothing left to play).
            if (file.exists() && !file.delete()) continue
            sessions.markAudioDeleted(session.id, nowMs)
            sessionAudio++
        }

        val referenced = all.mapNotNull { it.audioFileName }.toSet()
        var tempFiles = 0
        var orphanFiles = 0
        meetingsDir.listFiles()?.forEach { file ->
            if (!file.isFile || !file.name.endsWith(".wav")) return@forEach
            val age = nowMs - file.lastModified()
            when {
                file.name.startsWith(".tmp-") ->
                    if (age >= TEMP_MAX_AGE_MS && file.delete()) tempFiles++
                file.name !in referenced ->
                    if (age >= AudioPurgeWorker.RETENTION_MS && file.delete()) orphanFiles++
            }
        }
        return Report(sessionAudio, tempFiles, orphanFiles)
    }
}
