package fr.clinitex.transcription.meetings

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import java.io.File

/**
 * On-device speaker diarization (who spoke when) for finished meetings —
 * the Android counterpart of the FluidAudio diarizer in muesli-ios.
 *
 * Both models are bundled in assets (no download):
 *   - pyannote segmentation 3.0 (int8, ~1.5 MB) finds speech turns
 *   - NeMo TitaNet-S (~40 MB) extracts speaker embeddings for clustering
 *
 * Runs once at meeting stop on the retained WAV. Expected cost is roughly
 * 0.1× the meeting duration on a modern phone (RTF ≈ 0.11 per sherpa-onnx
 * benchmarks), i.e. ~30 s of processing for a 5-minute meeting.
 *
 * Lot 4: meetings longer than [DiarizationWindows.WINDOW_SEC] are diarized
 * window by window (≈38 MB of samples at a time instead of ≈230 MB for an
 * hour) and stitched by [DiarizationWindows.merge].
 */
class SpeakerDiarizer(
    private val context: Context,
    private val clusterThreshold: Float = CLUSTER_THRESHOLD,
) {

    companion object {
        private const val TAG = "SpeakerDiarizer"
        const val SEGMENTATION_MODEL = "models/pyannote_segmentation_3_0.int8.onnx"
        const val EMBEDDING_MODEL = "models/nemo_en_titanet_small.onnx"

        /** Fast-clustering threshold: a cosine distance (1 − similarity),
         * complete linkage, so a larger value gives fewer speakers
         * (sherpa-onnx v1.13.4, csrc/fast-clustering.cc). Calibrated on
         * Pixel 8 Pro (8 min, 1 voice; 2 × 13 min, 2 voices): 0.8 and 0.9
         * keep the two voices apart, 1.0 already merges them in one window.
         * The leftover tiny clusters are handled by
         * [DiarizationWindows.absorbMinorSpeakers], not by this threshold. */
        const val CLUSTER_THRESHOLD = 0.8f

        /** Speech per speaker and window fed to the embedding extractor. */
        private const val EMBEDDING_MAX_SEC = 30f
        /** Below this much speech, no embedding (too unreliable to link on). */
        private const val EMBEDDING_MIN_SEC = 1f
    }

    private val embeddingConfig = SpeakerEmbeddingExtractorConfig(
        model = EMBEDDING_MODEL,
        numThreads = 2,
        provider = "cpu",
        debug = false,
    )

    private val config = OfflineSpeakerDiarizationConfig(
        segmentation = OfflineSpeakerSegmentationModelConfig(
            pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(model = SEGMENTATION_MODEL),
            numThreads = 2,
            provider = "cpu",
            debug = false,
        ),
        embedding = embeddingConfig,
        // Unknown speaker count: let fast-clustering decide by threshold.
        clustering = FastClusteringConfig(numClusters = -1, threshold = clusterThreshold),
        minDurationOn = 0.3f,
        minDurationOff = 0.5f,
    )

    /** Diarizes a 16 kHz mono 16-bit WAV (as written by the recorder) into
     * speaker segments, streaming it window by window. */
    fun diarize(wav: File): List<SpeakerLabeler.SpeakerSegment> {
        val start = System.currentTimeMillis()
        // Minor clusters folded per window, before they can claim a link; a
        // single window goes through merge too, which numbers speakers by
        // first appearance (raw sherpa ids can be 0 and 11 for two speakers).
        val results = diarizeWindows(wav).map { DiarizationWindows.absorbMinorSpeakers(it) }
        val segments = DiarizationWindows.merge(results)
        Log.i(
            TAG,
            "Diarization produced ${segments.size} segments " +
                "(${segments.map { it.speaker }.toSet().size} speakers: ${speechPerSpeaker(segments)}) " +
                "in ${System.currentTimeMillis() - start} ms"
        )
        return segments
    }

    /** Raw per-window diarization, before minor speakers are folded and
     * windows stitched; a single window (short meeting) is processed in one
     * pass without embeddings, as before lot 4. Exposed so calibration can
     * re-merge one run with several thresholds. */
    internal fun diarizeWindows(wav: File): List<DiarizationWindows.WindowResult> {
        val totalSamples = WavSamples.sampleCount(wav)
        val windows = DiarizationWindows.plan(totalSamples)
        if (windows.isEmpty()) return emptyList()
        Log.i(
            TAG,
            "Diarizing ${"%.1f".format(totalSamples / 16000f)}s of audio in ${windows.size} window(s), " +
                "cluster threshold $clusterThreshold"
        )
        val start = System.currentTimeMillis()
        return if (windows.size == 1) {
            val segments = withModels(embeddings = false) { sd, _ -> process(sd, WavSamples.readMono16k(wav), offsetSec = 0f) }
            listOf(DiarizationWindows.WindowResult(windows.single(), segments, emptyMap()))
        } else {
            windows.mapIndexed { index, window ->
                // Fresh models per window: the native side keeps what a window
                // used until release (measured on Pixel 8 Pro: +318 MB, then
                // +394 MB more for the second window), so reusing one instance
                // would grow with the meeting length.
                withModels(embeddings = true) { sd, ex ->
                    val pcm = WavSamples.readMono16k(wav, window.startSample, window.sampleCount)
                    val local = process(sd, pcm, offsetSec = window.startSec)
                    val embeddings = local.map { it.speaker }.distinct()
                        .associateWith { speaker -> embed(ex!!, pcm, window.startSec, local.filter { it.speaker == speaker }) }
                    Log.i(
                        TAG,
                        "Window ${index + 1}/${windows.size}: ${local.size} segments, " +
                            "${embeddings.size} speakers (${speechPerSpeaker(local)}), " +
                            "${System.currentTimeMillis() - start} ms elapsed"
                    )
                    DiarizationWindows.WindowResult(window, local, embeddings)
                }
            }
        }
    }

    /**
     * Voice embedding of each speaker of one Voxtral tranche (segments in
     * seconds from the start of the recording), from the retained WAV: lets
     * [DiarizationWindows.link] recognize a speaker from one tranche to the
     * next. Only the embedding model is loaded.
     */
    fun speakerEmbeddings(
        wav: File,
        startSample: Long,
        sampleCount: Int,
        segmentsBySpeaker: Map<String, List<SpeakerLabeler.SpeakerSegment>>,
    ): Map<String, FloatArray?> {
        if (segmentsBySpeaker.isEmpty()) return emptyMap()
        val extractor = SpeakerEmbeddingExtractor(assetManager = context.assets, config = embeddingConfig)
        try {
            val pcm = WavSamples.readMono16k(wav, startSample, sampleCount)
            val startSec = startSample.toFloat() / WavSamples.SAMPLE_RATE
            return segmentsBySpeaker.mapValues { (_, segments) -> embed(extractor, pcm, startSec, segments) }
        } finally {
            extractor.release()
        }
    }

    /** "speaker=seconds" pairs, most speech first, for the logs. */
    internal fun speechPerSpeaker(segments: List<SpeakerLabeler.SpeakerSegment>): String =
        segments.groupBy { it.speaker }
            .mapValues { (_, s) -> s.sumOf { (it.endSec - it.startSec).toDouble() } }
            .entries.sortedByDescending { it.value }
            .joinToString(" ") { "${it.key}=${"%.1f".format(it.value)}s" }

    /** Runs [block] with freshly loaded models, released right after. */
    private fun <T> withModels(
        embeddings: Boolean,
        block: (OfflineSpeakerDiarization, SpeakerEmbeddingExtractor?) -> T,
    ): T {
        val sd = OfflineSpeakerDiarization(assetManager = context.assets, config = config)
        var extractor: SpeakerEmbeddingExtractor? = null
        try {
            if (embeddings) {
                extractor = SpeakerEmbeddingExtractor(assetManager = context.assets, config = embeddingConfig)
            }
            return block(sd, extractor)
        } finally {
            extractor?.release()
            sd.release()
        }
    }

    private fun process(sd: OfflineSpeakerDiarization, pcm: FloatArray, offsetSec: Float) =
        sd.process(pcm).map {
            SpeakerLabeler.SpeakerSegment(
                startSec = offsetSec + it.start,
                endSec = offsetSec + it.end,
                speaker = it.speaker,
            )
        }

    /** Voice embedding of one speaker from its longest turns in the window,
     * or null when it spoke less than [EMBEDDING_MIN_SEC]. */
    private fun embed(
        extractor: SpeakerEmbeddingExtractor,
        pcm: FloatArray,
        windowStartSec: Float,
        segments: List<SpeakerLabeler.SpeakerSegment>,
    ): FloatArray? {
        val maxSamples = (EMBEDDING_MAX_SEC * 16000).toInt()
        val pieces = mutableListOf<FloatArray>()
        var total = 0
        for (seg in segments.sortedByDescending { it.endSec - it.startSec }) {
            if (total >= maxSamples) break
            val from = ((seg.startSec - windowStartSec) * 16000).toInt().coerceIn(0, pcm.size)
            val to = ((seg.endSec - windowStartSec) * 16000).toInt().coerceIn(from, pcm.size)
            val take = minOf(to - from, maxSamples - total)
            if (take <= 0) continue
            pieces += pcm.copyOfRange(from, from + take)
            total += take
        }
        if (total < EMBEDDING_MIN_SEC * 16000) return null
        val audio = FloatArray(total)
        var offset = 0
        for (piece in pieces) {
            piece.copyInto(audio, offset)
            offset += piece.size
        }
        val stream = extractor.createStream()
        return try {
            stream.acceptWaveform(audio, 16000)
            stream.inputFinished()
            if (extractor.isReady(stream)) extractor.compute(stream) else null
        } finally {
            stream.release()
        }
    }
}
