package fr.clinitex.transcription.engine

import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import fr.clinitex.transcription.meetings.WavSamples
import fr.clinitex.transcription.model.ModelManager
import fr.clinitex.transcription.model.SpeechModels
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Side-by-side comparison of the catalog models (used on 20/10/2026 to compare
 * Parakeet v3 with Whisper large-v3-turbo, since removed; kept for future candidates)
 * on meetings already recorded on the phone (the audio never leaves it).
 * Each WAV is cut once with the same Silero VAD settings as
 * MeetingRecorderService, then every segment is decoded by each model.
 * Raw model output: no filler removal, no dictionary. Skipped unless run explicitly:
 *
 *   adb shell am instrument -w \
 *     -e class fr.clinitex.transcription.engine.ModelComparisonTest \
 *     -e wavs <sessionId>,<sessionId> [-e models <id>,<id>] [-e download 1] [-e vadonly 1] \
 *     fr.clinitex.transcription.test/androidx.test.runner.AndroidJUnitRunner
 *
 * `-e download 1` fetches a missing model first.
 * `-e vadonly 1` only logs the VAD segment lengths (no decoding).
 * Report: files/model-compare/comparatif-<date>.md (read with run-as), summary in
 * logcat: `adb logcat ClindyCompare:V SherpaRecognizer:V '*:S'`.
 */
@RunWith(AndroidJUnit4::class)
class ModelComparisonTest {

    private val tag = "ClindyCompare"
    private val sampleRate = 16000

    private class Segment(val startSec: Float, val endSec: Float, val samples: FloatArray)

    private class Run(
        val modelName: String,
        val loadMs: Long,
        val nativeMb: Long,
        val decodeMs: Long,
        val texts: List<String>,
    )

    @Test
    fun compareModels() {
        val args = InstrumentationRegistry.getArguments()
        val wavs = args.getString("wavs")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        assumeTrue("no -e wavs given", wavs.isNotEmpty())
        val models = args.getString("models")?.split(',')?.map { SpeechModels.byId(it.trim()) }
            ?: SpeechModels.all
        val download = args.getString("download") == "1"
        val vadOnly = args.getString("vadonly") == "1"
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // The app process may hold the selected model: free it before measuring.
        SherpaRecognizerHolder.reset()

        for (model in models) {
            val manager = ModelManager(context, model)
            if (manager.isDownloaded() || !download) continue
            Log.i(tag, "Downloading ${model.displayName}…")
            var lastPct = -1
            runBlocking {
                manager.download { p ->
                    val pct = (p.fraction * 100).toInt()
                    if (pct / 5 != lastPct / 5) {
                        lastPct = pct
                        Log.i(tag, "${model.shortName}: $pct %")
                    }
                }
            }
        }
        val ready = models.filter { ModelManager(context, it).isDownloaded() }
        models.filterNot { it in ready }.forEach { Log.w(tag, "${it.displayName} not downloaded, skipped") }
        assumeTrue("no downloaded model", ready.isNotEmpty())

        val report = StringBuilder("# Comparatif des modèles de transcription\n\n")
        report.append("Généré le ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())} ")
        report.append("sur le téléphone. Texte brut du modèle (sans suppression des « euh » ni dictionnaire).\n")

        val outDir = File(context.filesDir, "model-compare").apply { mkdirs() }
        val out = File(outDir, "comparatif-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.FRANCE).format(Date())}.md")

        for (id in wavs) {
            val wav = File(context.filesDir, "meetings/$id.wav")
            if (!wav.exists()) {
                Log.w(tag, "$id: no WAV")
                continue
            }
            val audio = WavSamples.readMono16k(wav)
            val audioSec = audio.size.toFloat() / sampleRate
            val segments = segment(context.assets, audio)
            Log.i(tag, "$id: ${"%.0f".format(audioSec)} s, ${segments.size} segments")
            segments.forEachIndexed { k, s ->
                Log.i(tag, "$id segment ${k + 1}: ${clock(s.startSec)}–${clock(s.endSec)} (${"%.1f".format(s.endSec - s.startSec)} s)")
            }
            if (vadOnly) continue

            val runs = ready.map { model -> decodeAll(ModelManager(context, model), segments) }

            report.append("\n## Enregistrement $id\n\n")
            report.append("Durée : ${clock(audioSec)} · ${segments.size} segments de parole (passages de plus de 30 s recoupés en tranches de 30 s)\n\n")
            report.append("| Modèle | Chargement | Mémoire native après chargement | Transcription | Vitesse |\n")
            report.append("|---|---|---|---|---|\n")
            for (r in runs) {
                val factor = r.decodeMs / 1000f / audioSec
                report.append("| ${r.modelName} | ${"%.1f".format(r.loadMs / 1000f)} s | ${r.nativeMb} Mo | ")
                report.append("${"%.1f".format(r.decodeMs / 1000f)} s | ${"%.2f".format(factor)}× la durée |\n")
                Log.i(tag, "$id ${r.modelName}: load ${r.loadMs} ms, native ${r.nativeMb} MB, decode ${r.decodeMs} ms (${"%.2f".format(factor)}x)")
            }
            report.append("\n| Temps | ").append(runs.joinToString(" | ") { it.modelName }).append(" |\n")
            report.append("|---|").append(runs.joinToString("") { "---|" }).append("\n")
            segments.forEachIndexed { k, s ->
                report.append("| ${clock(s.startSec)}–${clock(s.endSec)} | ")
                report.append(runs.joinToString(" | ") { cell(it.texts[k]) }).append(" |\n")
            }
            // Written after each recording so a later one that fails keeps the earlier results.
            out.writeText(report.toString())
            Log.i(tag, "Report: ${out.absolutePath}")
        }
    }

    /** Same VAD settings and 100 ms feeding as MeetingRecorderService. */
    private fun segment(assets: android.content.res.AssetManager, audio: FloatArray): List<Segment> {
        val vad = Vad(
            assetManager = assets,
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = "models/silero_vad.onnx",
                    threshold = 0.5f,
                    minSilenceDuration = 0.5f,
                    minSpeechDuration = 0.5f,
                    windowSize = 512,
                    maxSpeechDuration = 30f,
                ),
                sampleRate = sampleRate,
                numThreads = 1,
                provider = "cpu",
            ),
        )
        val out = mutableListOf<Segment>()
        // Silero's maxSpeechDuration only raises its threshold: on continuous speech it let
        // 170 s and 351 s segments through (Pixel, 20/10/2026). Whisper reads 30 s at most and
        // Parakeet hit 3 GB on them, so long segments are hard-cut into 30 s pieces here.
        fun add(start: Float, samples: FloatArray) {
            val max = sampleRate * 30
            var i = 0
            while (i < samples.size) {
                val piece = samples.copyOfRange(i, minOf(i + max, samples.size))
                val s = start + i.toFloat() / sampleRate
                out += Segment(s, s + piece.size.toFloat() / sampleRate, piece)
                i += max
            }
        }
        fun drain() {
            while (!vad.empty()) {
                val s = vad.front()
                vad.pop()
                if (s.samples.size < sampleRate / 2) continue
                add(s.start.toFloat() / sampleRate, s.samples)
            }
        }
        try {
            val frame = sampleRate / 10
            var i = 0
            while (i < audio.size) {
                vad.acceptWaveform(audio.copyOfRange(i, minOf(i + frame, audio.size)))
                drain()
                i += frame
            }
            vad.flush()
            drain()
        } finally {
            vad.release()
        }
        return out
    }

    private fun decodeAll(manager: ModelManager, segments: List<Segment>): Run {
        val loadStart = System.currentTimeMillis()
        val recognizer = SherpaRecognizerHolder.create(manager)
        val loadMs = System.currentTimeMillis() - loadStart
        val nativeMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
        try {
            val decodeStart = System.currentTimeMillis()
            val texts = segments.map { s ->
                val stream = recognizer.createStream()
                try {
                    stream.acceptWaveform(s.samples, sampleRate)
                    recognizer.decode(stream)
                    recognizer.getResult(stream).text.trim()
                } finally {
                    stream.release()
                }
            }
            return Run(manager.model.shortName, loadMs, nativeMb, System.currentTimeMillis() - decodeStart, texts)
        } finally {
            // One model in memory at a time, as in the app.
            recognizer.release()
        }
    }

    private fun clock(sec: Float): String {
        val t = sec.toInt()
        return "%d:%02d".format(t / 60, t % 60)
    }

    private fun cell(text: String) = text.ifBlank { "—" }.replace("|", "\\|").replace("\n", " ")
}
