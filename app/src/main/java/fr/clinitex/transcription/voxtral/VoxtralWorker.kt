package fr.clinitex.transcription.voxtral

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
import fr.clinitex.transcription.BuildConfig
import fr.clinitex.transcription.data.SharedStore
import fr.clinitex.transcription.data.entity.Transcript
import fr.clinitex.transcription.data.entity.TranscriptSegment
import fr.clinitex.transcription.meetings.SpeakerDiarizer
import fr.clinitex.transcription.meetings.SpeakerLabeler
import fr.clinitex.transcription.meetings.WavSamples
import fr.clinitex.transcription.summaries.SummaryWorker
import fr.clinitex.transcription.utils.TranscriptPostProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Voxtral variant: transcribes one 10-minute tranche of a recording
 * ([VoxtralTranches.plan]). The recorder enqueues each tranche as soon as its
 * 10 minutes are recorded, and the last ones at stop; WorkManager holds them
 * until the phone is online. The tranche is read back from the retained WAV,
 * encoded as FLAC and sent through [VoxtralClient]; its segments are saved in
 * [VoxtralTrancheStore], then [VoxtralFinisher] assembles the transcript once
 * every tranche is there.
 *
 * Network errors and 5xx retry with backoff, with no limit: the WAV is kept
 * while the session is "transcribing" (AudioPurger skips it). A 4xx or an
 * unreadable answer fails the session.
 */
class VoxtralWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "VoxtralWorker"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_INDEX = "index"
        private const val KEY_START_SAMPLE = "start_sample"
        private const val KEY_SAMPLE_COUNT = "sample_count"
        private const val KEY_DIARIZE = "diarize"

        fun trancheStore(context: Context, sessionId: String) =
            VoxtralTrancheStore(File(context.applicationContext.filesDir, "voxtral/$sessionId"))

        private fun sessionTag(sessionId: String) = "voxtral-$sessionId"

        /** Schedules the tranches not yet scheduled (KEEP: enqueuing the same tranche twice is harmless). */
        fun enqueue(context: Context, sessionId: String, tranches: List<VoxtralTranches.Tranche>, diarize: Boolean) {
            val workManager = WorkManager.getInstance(context.applicationContext)
            for (tranche in tranches) {
                val request = OneTimeWorkRequestBuilder<VoxtralWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                    .setInputData(
                        workDataOf(
                            KEY_SESSION_ID to sessionId,
                            KEY_INDEX to tranche.index,
                            KEY_START_SAMPLE to tranche.startSample,
                            KEY_SAMPLE_COUNT to tranche.sampleCount,
                            KEY_DIARIZE to diarize,
                        )
                    )
                    .addTag(sessionTag(sessionId))
                    .build()
                workManager.enqueueUniqueWork("voxtral-$sessionId-${tranche.index}", ExistingWorkPolicy.KEEP, request)
            }
        }

        /** Recording abandoned: tranches already sent are dropped. */
        fun cancel(context: Context, sessionId: String) {
            WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(sessionTag(sessionId))
            trancheStore(context, sessionId).delete()
        }
    }

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
        val index = inputData.getInt(KEY_INDEX, -1)
        val startSample = inputData.getLong(KEY_START_SAMPLE, 0L)
        val sampleCount = inputData.getInt(KEY_SAMPLE_COUNT, 0)
        val diarize = inputData.getBoolean(KEY_DIARIZE, false)

        val store = SharedStore(applicationContext)
        val session = store.getRecordingSessionById(sessionId) ?: return Result.success() // deleted
        if (session.phase == "cancelled") return Result.success()
        val tranches = trancheStore(applicationContext, sessionId)

        if (!tranches.hasTranche(index)) {
            val wav = File(applicationContext.filesDir, "meetings/$sessionId.wav")
            if (!wav.exists()) {
                tranches.saveFailure(index, "l’enregistrement audio est introuvable sur le téléphone.")
                VoxtralFinisher.tryFinish(applicationContext, sessionId)
                return Result.failure()
            }
            val flac = withContext(Dispatchers.Default) {
                FlacEncoder.encode(WavSamples.readPcm16(wav, startSample, sampleCount), WavSamples.SAMPLE_RATE)
            }
            val client = VoxtralClient(BuildConfig.CLINITEX_TRANSCRIPTION_URL, BuildConfig.CLINITEX_DEVICE_TOKEN)
            val response = try {
                client.transcribe(flac, sessionId, index, diarize, contextBias(store))
            } catch (e: VoxtralClient.VoxtralException) {
                if (e.retryable) {
                    Log.w(TAG, "tranche $index of $sessionId, attempt ${runAttemptCount + 1}: ${e.message}")
                    return Result.retry()
                }
                Log.w(TAG, "tranche $index of $sessionId failed for good: ${e.message}")
                tranches.saveFailure(index, e.message ?: "erreur inconnue")
                VoxtralFinisher.tryFinish(applicationContext, sessionId)
                return Result.failure()
            }

            val offset = startSample.toFloat() / WavSamples.SAMPLE_RATE
            val segments = response.segments.map {
                VoxtralTrancheStore.StoredSegment(offset + it.start, offset + it.end, it.speakerId?.takeIf { diarize }, it.text)
            }.ifEmpty {
                // Text without timestamps: one segment for the whole tranche.
                if (response.text.isBlank()) emptyList()
                else listOf(VoxtralTrancheStore.StoredSegment(offset, offset + sampleCount.toFloat() / WavSamples.SAMPLE_RATE, null, response.text))
            }
            tranches.saveTranche(VoxtralTrancheStore.StoredTranche(index, segments))
            Log.i(TAG, "tranche $index of $sessionId: ${segments.size} segments, ${flac.size / 1024} KB sent")
        }

        VoxtralFinisher.tryFinish(applicationContext, sessionId)
        return Result.success()
    }

    /** Own and common dictionary words, as spelled in the transcript, to bias recognition. */
    private suspend fun contextBias(store: SharedStore): List<String> {
        if (!store.isCustomDictionaryEnabled) return emptyList()
        return store.getDictionaryWords().filter { it.isEnabled }.map { it.targetWord.trim() }
            .filter { it.isNotEmpty() }.distinct()
    }
}

/**
 * Turns the transcribed tranches of a stopped recording into its transcript,
 * once all are there: stitching and speakers by [VoxtralTranches.assemble]
 * (voice embeddings computed on the phone when there are several tranches),
 * same text clean-up as Parakeet, then the summary is queued as usual.
 */
object VoxtralFinisher {

    private const val TAG = "VoxtralFinisher"
    private val mutex = Mutex()

    suspend fun tryFinish(context: Context, sessionId: String) = mutex.withLock {
        val appContext = context.applicationContext
        val store = SharedStore(appContext)
        val tranches = VoxtralWorker.trancheStore(appContext, sessionId)
        val final = tranches.final() ?: return@withLock // still recording
        val session = store.getRecordingSessionById(sessionId) ?: return@withLock
        if (session.phase != "transcribing") return@withLock

        tranches.failure()?.let { message ->
            store.updateRecordingSession(
                session.copy(phase = "failed", errorMessage = "La transcription en ligne (Voxtral) a échoué : $message")
            )
            return@withLock
        }

        val planned = VoxtralTranches.plan(final.totalSamples, final = true)
        val done = tranches.tranches()
        if (planned.any { it.index !in done }) return@withLock

        val results = planned.map { tranche ->
            VoxtralTranches.TrancheResult(
                tranche,
                done.getValue(tranche.index).segments.map { VoxtralTranches.TimedSegment(it.start, it.end, it.speakerId, it.text) },
            )
        }
        val wav = File(appContext.filesDir, "meetings/$sessionId.wav")
        val embeddings = if (final.diarize && results.size > 1 && wav.exists()) {
            withContext(Dispatchers.Default) {
                try {
                    val diarizer = SpeakerDiarizer(appContext)
                    results.map { result ->
                        val bySpeaker = result.segments.filter { it.speakerId != null }.groupBy { it.speakerId!! }
                            .mapValues { (_, segs) -> segs.map { SpeakerLabeler.SpeakerSegment(it.startSec, it.endSec, 0) } }
                        diarizer.speakerEmbeddings(wav, result.tranche.startSample, result.tranche.sampleCount, bySpeaker)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "embeddings failed, linking speakers on the overlaps only: ${t.message}")
                    emptyList()
                }
            }
        } else {
            emptyList()
        }

        val labeled = VoxtralTranches.paragraphs(VoxtralTranches.assemble(results, embeddings))
            // Exact dictionary words only: Mistral already received the list (context_bias).
            .map { it.copy(text = TranscriptPostProcessor.apply(store, it.text, fuzzyDictionary = false)) }
            .filter { it.text.isNotBlank() }
        val text = if (final.diarize) SpeakerLabeler.render(labeled) else labeled.joinToString("\n\n") { it.text }

        val transcript = Transcript(
            sessionID = sessionId,
            text = text,
            engineIdentifier = session.engineIdentifier ?: VoxtralClient.MODEL_ID,
        )
        store.insertTranscript(transcript)
        if (final.diarize) {
            store.replaceTranscriptSegments(
                sessionId,
                labeled.map { TranscriptSegment(sessionId = sessionId, startSec = it.startSec, endSec = it.endSec, speaker = it.speaker, text = it.text) },
            )
        }
        val latest = store.getRecordingSessionById(sessionId) ?: session
        store.updateRecordingSession(latest.copy(phase = "completed", transcriptID = transcript.id))
        tranches.delete()
        Log.i(TAG, "$sessionId assembled from ${results.size} tranche(s): ${labeled.map { it.speaker }.toSet().size} speaker(s)")
        SummaryWorker.enqueue(appContext, sessionId)
    }
}
