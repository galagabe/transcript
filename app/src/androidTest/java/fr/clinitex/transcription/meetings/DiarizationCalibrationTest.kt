package fr.clinitex.transcription.meetings

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Calibration of the diarization thresholds on meetings already recorded on
 * the phone (the audio never leaves it). Skipped unless run explicitly:
 *
 *   adb shell am instrument -w \
 *     -e class fr.clinitex.transcription.meetings.DiarizationCalibrationTest \
 *     -e wavs <sessionId>,<sessionId> -e thresholds 0.7,0.8,0.9 -e links 0.4,0.5,0.6 -e mins 0,3,5 \
 *     fr.clinitex.transcription.test/androidx.test.runner.AndroidJUnitRunner
 *
 * Results go to logcat: `adb logcat SpeakerDiarizer:V ClindyCalib:V '*:S'`.
 */
@RunWith(AndroidJUnit4::class)
class DiarizationCalibrationTest {

    private val tag = "ClindyCalib"

    @Test
    fun sweepThresholds() {
        val args = InstrumentationRegistry.getArguments()
        val wavs = args.getString("wavs")?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        assumeTrue("no -e wavs given", wavs.isNotEmpty())
        val thresholds = floats(args.getString("thresholds")) ?: listOf(SpeakerDiarizer.CLUSTER_THRESHOLD)
        val links = floats(args.getString("links")) ?: listOf(DiarizationWindows.LINK_THRESHOLD)
        val mins = floats(args.getString("mins")) ?: listOf(DiarizationWindows.MIN_SPEAKER_SEC)
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        for (id in wavs) {
            val wav = File(context.filesDir, "meetings/$id.wav")
            if (!wav.exists()) {
                Log.w(tag, "$id: no WAV")
                continue
            }
            for (threshold in thresholds) {
                val diarizer = SpeakerDiarizer(context, threshold)
                val started = System.currentTimeMillis()
                val results = diarizer.diarizeWindows(wav)
                val elapsed = System.currentTimeMillis() - started
                results.forEachIndexed { k, r ->
                    Log.i(tag, "$id cluster=$threshold raw window ${k + 1}/${results.size}: ${count(r.segments)} speakers [${diarizer.speechPerSpeaker(r.segments)}] in $elapsed ms")
                }
                if (results.size > 1) logCrossWindowSimilarities(id, threshold, results)
                for (min in mins) {
                    val cleaned = results.map { DiarizationWindows.absorbMinorSpeakers(it, min) }
                    for (link in if (results.size > 1) links else links.take(1)) {
                        val merged = DiarizationWindows.merge(cleaned, linkThreshold = link)
                        Log.i(tag, "$id cluster=$threshold min=$min link=$link: ${count(merged)} speakers [${diarizer.speechPerSpeaker(merged)}]")
                    }
                }
            }
        }
    }

    /** Cosine between each speaker of a window and each speaker of the
     * previous one, to place LINK_THRESHOLD between same and other voices. */
    private fun logCrossWindowSimilarities(id: String, threshold: Float, results: List<DiarizationWindows.WindowResult>) {
        for (k in 1 until results.size) {
            for ((a, ea) in results[k].embeddings) {
                if (ea == null) continue
                val line = results[k - 1].embeddings.entries.joinToString(" ") { (b, eb) ->
                    "$b:" + (eb?.let { "%.2f".format(DiarizationWindows.cosine(ea, it)) } ?: "-")
                }
                Log.i(tag, "$id cluster=$threshold w${k + 1}.$a vs w$k: $line")
            }
        }
    }

    private fun count(segments: List<SpeakerLabeler.SpeakerSegment>) = segments.map { it.speaker }.toSet().size

    private fun floats(arg: String?) = arg?.split(',')?.mapNotNull { it.trim().toFloatOrNull() }?.takeIf { it.isNotEmpty() }
}
