package fr.clinitex.transcription.voxtral

import fr.clinitex.transcription.meetings.SpeakerLabeler
import fr.clinitex.transcription.meetings.WavSamples
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Properties
import kotlin.time.Duration.Companion.minutes

/**
 * Live check of the Voxtral variant against the real n8n webhook and Mistral,
 * on a 16 kHz mono WAV: the same chain as the phone (tranches, FLAC, webhook,
 * stitching), with short tranches so a few minutes of audio cross several
 * boundaries. Ignored unless CLINDY_VOXTRAL_WAV points to a WAV; URL and
 * device token come from local.properties (never printed). The report goes
 * to CLINDY_VOXTRAL_OUT when set.
 *
 *   CLINDY_VOXTRAL_WAV=…/dialogue.wav gradlew :app:testDebugUnitTest --tests '*VoxtralWebhookTest*'
 */
class VoxtralWebhookTest {

    private val wavPath: String? = System.getenv("CLINDY_VOXTRAL_WAV")

    private fun localProperty(name: String): String {
        val file = listOf(File("local.properties"), File("../local.properties")).first { it.exists() }
        return Properties().apply { file.inputStream().use { load(it) } }.getProperty(name, "")
    }

    @Test
    fun `the webhook transcribes and stitches tranches end to end`() = runTest(timeout = 15.minutes) {
        assumeTrue("CLINDY_VOXTRAL_WAV not set", wavPath != null)
        val wav = File(wavPath!!)
        val url = localProperty("clinitex.transcriptionUrl")
        val token = localProperty("clinitex.deviceToken")
        assumeTrue(url.isNotBlank() && token.isNotBlank())
        val report = StringBuilder()

        // 1. No token: refused by the webhook.
        val refused = OkHttpClient().newCall(
            Request.Builder().url(url).post("x".toRequestBody()).build()
        ).execute().use { it.code }
        report.append("Sans jeton : HTTP $refused\n")
        assertEquals(403, refused)

        val client = VoxtralClient(url, token)
        val bias = listOf("Clinitex", "autolaveuse", "Les Tilleuls", "écolabellisés")
        val total = WavSamples.sampleCount(wav)

        // 2. Whole file in one request (reference), unless CLINDY_VOXTRAL_SKIP_WHOLE is set.
        if (System.getenv("CLINDY_VOXTRAL_SKIP_WHOLE") == null) {
            val whole = client.transcribe(FlacEncoder.encode(WavSamples.readPcm16(wav, 0, total.toInt()), 16_000), "test-whole", 0, true, bias)
            report.append("\n## Fichier entier (${"%.0f".format(total / 16_000f)} s, une requête)\n")
            report.append("Intervenants : ${whole.segments.mapNotNull { it.speakerId }.distinct()}\n")
            for (s in whole.segments) report.append("[${"%.1f".format(s.start)}-${"%.1f".format(s.end)}] ${s.speakerId}: ${s.text.trim()}\n")
        }

        // 3. Short tranches (60 s overlapping by 15 s by default), as the phone does with 600 s / 30 s.
        val trancheSec = System.getenv("CLINDY_VOXTRAL_TRANCHE_SEC")?.toInt() ?: 60
        val tranches = VoxtralTranches.plan(total, final = true, trancheSec = trancheSec, overlapSec = minOf(15, trancheSec / 4))
        val results = tranches.map { tranche ->
            val flac = FlacEncoder.encode(WavSamples.readPcm16(wav, tranche.startSample, tranche.sampleCount), 16_000)
            val started = System.currentTimeMillis()
            val response = client.transcribe(flac, "test-tranches", tranche.index, true, bias)
            report.append("\nTranche ${tranche.index} [${"%.0f".format(tranche.startSec)}-${"%.0f".format(tranche.endSec)} s] : " +
                "${flac.size / 1024} Ko FLAC (${tranche.sampleCount * 2 / 1024} Ko en WAV), ${System.currentTimeMillis() - started} ms, " +
                "${response.segments.size} segments, ${response.segments.mapNotNull { it.speakerId }.distinct()}\n")
            val offset = tranche.startSec
            VoxtralTranches.TrancheResult(
                tranche,
                response.segments.map { VoxtralTranches.TimedSegment(offset + it.start, offset + it.end, it.speakerId, it.text) },
            )
        }
        val spans = VoxtralTranches.ownedSpans(results)
        report.append("Coupes : ${spans.drop(1).map { "%.1f".format(it.first) }}\n")
        val labeled = VoxtralTranches.paragraphs(VoxtralTranches.assemble(results))
        report.append("\n## Transcription assemblée (${tranches.size} tranches)\n\n").append(SpeakerLabeler.render(labeled)).append('\n')

        System.getenv("CLINDY_VOXTRAL_OUT")?.let { File(it).writeText(report.toString()) }
        println(report)
        assertTrue(labeled.isNotEmpty())
    }
}
