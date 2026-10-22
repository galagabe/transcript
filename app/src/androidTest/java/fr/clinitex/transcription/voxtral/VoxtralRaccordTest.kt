package fr.clinitex.transcription.voxtral

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.clinitex.transcription.BuildConfig
import fr.clinitex.transcription.data.SharedStore
import fr.clinitex.transcription.utils.TranscriptPostProcessor
import fr.clinitex.transcription.meetings.DiarizationWindows
import fr.clinitex.transcription.meetings.SpeakerDiarizer
import fr.clinitex.transcription.meetings.SpeakerLabeler
import fr.clinitex.transcription.meetings.WavSamples
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Voxtral variant on the phone: sends a WAV in tranches through the real n8n
 * webhook, computes each Voxtral speaker's TitaNet embedding as
 * [VoxtralFinisher] does, and assembles with and without embeddings, to check
 * that speakers stay the same across tranches. Skipped unless run explicitly
 * (sends audio to Mistral: synthetic test audio only):
 *
 *   adb shell run-as fr.clinitex.transcription cp /data/local/tmp/dialogue.wav files/voxtral-test/dialogue.wav
 *   adb shell am instrument -w -e class fr.clinitex.transcription.voxtral.VoxtralRaccordTest \
 *     -e wav voxtral-test/dialogue.wav -e tranche 60 -e overlap 15 \
 *     fr.clinitex.transcription.test/androidx.test.runner.AndroidJUnitRunner
 *
 * Report: files/voxtral-test/report.txt (and logcat ClindyVoxtral).
 */
@RunWith(AndroidJUnit4::class)
class VoxtralRaccordTest {

    private val tag = "ClindyVoxtral"

    @Test
    fun speakersStayTheSameAcrossTranches() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val wavArg = args.getString("wav")
        assumeTrue("no -e wav given", wavArg != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wav = File(context.filesDir, wavArg!!)
        assumeTrue("missing ${wav.path}", wav.exists())
        val trancheSec = args.getString("tranche")?.toInt() ?: 60
        val overlapSec = args.getString("overlap")?.toInt() ?: 15
        val report = StringBuilder()
        fun log(line: String) {
            Log.i(tag, line)
            report.append(line).append('\n')
        }

        val client = VoxtralClient(BuildConfig.CLINITEX_TRANSCRIPTION_URL, BuildConfig.CLINITEX_DEVICE_TOKEN)
        val total = WavSamples.sampleCount(wav)
        val tranches = VoxtralTranches.plan(total, final = true, trancheSec = trancheSec, overlapSec = overlapSec)
        log("${wav.name}: ${"%.1f".format(total / 16_000f)} s, ${tranches.size} tranches de $trancheSec s (recouvrement $overlapSec s)")

        val results = tranches.map { tranche ->
            val flac = FlacEncoder.encode(WavSamples.readPcm16(wav, tranche.startSample, tranche.sampleCount), WavSamples.SAMPLE_RATE)
            val response = client.transcribe(flac, "test-raccord", tranche.index, diarize = true, contextBias = listOf("Clinitex", "autolaveuse"))
            val offset = tranche.startSec
            log("Tranche ${tranche.index} [${"%.0f".format(tranche.startSec)}-${"%.0f".format(tranche.endSec)} s] : ${response.segments.size} segments, ${response.segments.mapNotNull { it.speakerId }.distinct()}")
            VoxtralTranches.TrancheResult(
                tranche,
                response.segments.map { VoxtralTranches.TimedSegment(offset + it.start, offset + it.end, it.speakerId, it.text) },
            )
        }

        // Same embeddings as VoxtralFinisher.
        val started = System.currentTimeMillis()
        val diarizer = SpeakerDiarizer(context)
        val embeddings = results.map { result ->
            val bySpeaker = result.segments.filter { it.speakerId != null }.groupBy { it.speakerId!! }
                .mapValues { (_, segs) -> segs.map { SpeakerLabeler.SpeakerSegment(it.startSec, it.endSec, 0) } }
            diarizer.speakerEmbeddings(wav, result.tranche.startSample, result.tranche.sampleCount, bySpeaker)
        }
        log("Empreintes TitaNet : ${System.currentTimeMillis() - started} ms")
        for (k in 1 until embeddings.size) {
            for ((label, e) in embeddings[k]) {
                if (e == null) continue
                val line = embeddings[k - 1].entries.joinToString(" ") { (other, o) ->
                    "$other=" + (o?.let { "%.2f".format(DiarizationWindows.cosine(e, it)) } ?: "-")
                }
                log("  tranche $k $label vs tranche ${k - 1} : $line")
            }
        }

        log("Coupes : ${VoxtralTranches.ownedSpans(results).drop(1).map { "%.1f".format(it.first) }}")
        val without = VoxtralTranches.paragraphs(VoxtralTranches.assemble(results))
        val with = VoxtralTranches.paragraphs(VoxtralTranches.assemble(results, embeddings))
        log("Sans empreintes : ${without.map { it.speaker }.toSet().size} intervenants ; avec : ${with.map { it.speaker }.toSet().size}")
        // Same clean-up as VoxtralFinisher: filler words, exact dictionary words only.
        val store = SharedStore(context)
        val cleaned = with.map { it.copy(text = TranscriptPostProcessor.apply(store, it.text, fuzzyDictionary = false)) }
            .filter { it.text.isNotBlank() }
        log("\n## Avec empreintes, après nettoyage (comme l'appli)\n\n" + SpeakerLabeler.render(cleaned))

        File(context.filesDir, "voxtral-test/report.txt").apply { parentFile?.mkdirs() }.writeText(report.toString())
    }
}
