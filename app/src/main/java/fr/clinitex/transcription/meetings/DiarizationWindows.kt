package fr.clinitex.transcription.meetings

import fr.clinitex.transcription.meetings.SpeakerLabeler.SpeakerSegment
import kotlin.math.sqrt

/**
 * Pure logic for diarizing long meetings window by window (lot 4): one hour
 * of 16 kHz float audio is ≈230 MB, too much for a 256 MB Java heap, so
 * [SpeakerDiarizer] processes overlapping windows of ~10 minutes and this
 * object stitches their speakers into one consistent numbering.
 *
 * Each window has its own local speaker ids. A local speaker is linked to a
 * global one by, in order:
 *  1. agreement in the overlap with the previous window (same audio labeled
 *     by both windows: the strongest evidence);
 *  2. cosine similarity between its voice embedding (TitaNet-S) and the
 *     running centroid of each global speaker, so someone silent for 20
 *     minutes is still recognized;
 *  3. otherwise it becomes a new speaker.
 * Links are one-to-one within a window. Each window then keeps only the
 * segments of its "owned" span (overlaps split in the middle), so nothing is
 * counted twice. Kept free of Android/sherpa types so it is unit-testable.
 */
object DiarizationWindows {

    const val WINDOW_SEC = 600f
    const val OVERLAP_SEC = 30f

    /** Minimum cosine similarity to link a speaker to an earlier one.
     * Pixel 8 Pro, 2 meetings of 13 min: same voice across windows 0.59 to
     * 0.93, different voices up to 0.50; set halfway. */
    const val LINK_THRESHOLD = 0.55f

    /** Minimum speech both windows attribute to the same pair of speakers in
     * their overlap before the overlap alone links them. */
    const val MIN_OVERLAP_AGREEMENT_SEC = 1f

    /** A speaker with less speech than this in a window is folded into
     * another one ([absorbMinorSpeakers]). Pixel 8 Pro: leftovers up to 3.8 s
     * on 1 voice and 4.8 s on 2 voices; the real voices had 78 s or more. */
    const val MIN_SPEAKER_SEC = 5f

    /** Segments shorter than this after clipping to the owned span are dropped. */
    private const val MIN_SEGMENT_SEC = 0.05f

    /** A window of samples [startSample, startSample + sampleCount) that keeps
     * the segments falling in [ownStartSec, ownEndSec). */
    data class Window(
        val startSample: Long,
        val sampleCount: Int,
        val ownStartSec: Float,
        val ownEndSec: Float,
        val sampleRate: Int,
    ) {
        val startSec: Float get() = startSample.toFloat() / sampleRate
        val endSec: Float get() = (startSample + sampleCount).toFloat() / sampleRate
    }

    /** Diarization of one window: segments in absolute seconds with local
     * speaker ids, and one voice embedding per local speaker (null when it
     * spoke too little to get a reliable one). */
    data class WindowResult(
        val window: Window,
        val segments: List<SpeakerSegment>,
        val embeddings: Map<Int, FloatArray?>,
    )

    /**
     * Splits [totalSamples] into windows of at most [windowSec] overlapping by
     * [overlapSec]. Windows are evened out so the last one is never a tiny
     * tail. Audio up to [windowSec] is a single window (same as before lot 4).
     */
    fun plan(
        totalSamples: Long,
        sampleRate: Int = WavSamples.SAMPLE_RATE,
        windowSec: Float = WINDOW_SEC,
        overlapSec: Float = OVERLAP_SEC,
    ): List<Window> {
        if (totalSamples <= 0) return emptyList()
        val windowSamples = (windowSec * sampleRate).toLong()
        val overlap = (overlapSec * sampleRate).toLong()
        require(overlap in 0 until windowSamples) { "overlap must be shorter than the window" }
        if (totalSamples <= windowSamples) {
            return listOf(Window(0, totalSamples.toInt(), 0f, Float.MAX_VALUE, sampleRate))
        }
        val maxStep = windowSamples - overlap
        val count = ((totalSamples - overlap + maxStep - 1) / maxStep).toInt()
        val step = (totalSamples - overlap) / count
        return (0 until count).map { k ->
            val start = k * step
            val end = if (k == count - 1) totalSamples else start + step + overlap
            val ownStart = if (k == 0) 0f else (start + overlap / 2).toFloat() / sampleRate
            val ownEnd = if (k == count - 1) Float.MAX_VALUE else (start + step + overlap / 2).toFloat() / sampleRate
            Window(start, (end - start).toInt(), ownStart, ownEnd, sampleRate)
        }
    }

    /** Stitches per-window results into one segment list with global speaker
     * ids numbered from 0 in order of first appearance. */
    fun merge(
        results: List<WindowResult>,
        linkThreshold: Float = LINK_THRESHOLD,
        minOverlapAgreementSec: Float = MIN_OVERLAP_AGREEMENT_SEC,
    ): List<SpeakerSegment> {
        val centroids = mutableListOf<FloatArray?>() // duration-weighted sums of unit embeddings
        val merged = mutableListOf<SpeakerSegment>()
        var previous: Pair<Window, List<SpeakerSegment>>? = null // previous window, global ids, unclipped

        for (result in results) {
            val locals = result.segments.sortedBy { it.startSec }.map { it.speaker }.distinct()
            val mapping = HashMap<Int, Int>()
            val usedGlobals = HashSet<Int>()

            // 1. Overlap agreement with the previous window.
            previous?.let { (prevWindow, prevSegments) ->
                val from = result.window.startSec
                val to = prevWindow.endSec
                val pairs = mutableListOf<Triple<Int, Int, Float>>()
                for (local in locals) {
                    val mine = result.segments.filter { it.speaker == local }
                    for (global in prevSegments.map { it.speaker }.distinct()) {
                        val theirs = prevSegments.filter { it.speaker == global }
                        val agreement = sharedSeconds(mine, theirs, from, to)
                        if (agreement >= minOverlapAgreementSec) pairs += Triple(local, global, agreement)
                    }
                }
                for ((local, global, _) in pairs.sortedByDescending { it.third }) {
                    if (local in mapping || global in usedGlobals) continue
                    mapping[local] = global
                    usedGlobals += global
                }
            }

            // 2. Voice embedding against earlier speakers' centroids.
            val candidates = mutableListOf<Triple<Int, Int, Float>>()
            for (local in locals) {
                if (local in mapping) continue
                val embedding = result.embeddings[local] ?: continue
                centroids.forEachIndexed { global, centroid ->
                    if (centroid != null && global !in usedGlobals) {
                        val similarity = cosine(embedding, centroid)
                        if (similarity >= linkThreshold) candidates += Triple(local, global, similarity)
                    }
                }
            }
            for ((local, global, _) in candidates.sortedByDescending { it.third }) {
                if (local in mapping || global in usedGlobals) continue
                mapping[local] = global
                usedGlobals += global
            }

            // 3. New speakers.
            for (local in locals) {
                if (local !in mapping) {
                    mapping[local] = centroids.size
                    centroids += null
                }
            }

            // Fold this window's voices into the centroids.
            for (local in locals) {
                val embedding = result.embeddings[local] ?: continue
                val global = mapping.getValue(local)
                val speech = result.segments.filter { it.speaker == local }.sumOf { (it.endSec - it.startSec).toDouble() }.toFloat()
                val unit = normalized(embedding) ?: continue
                val sum = centroids[global] ?: FloatArray(unit.size).also { centroids[global] = it }
                if (sum.size != unit.size) continue
                for (i in unit.indices) sum[i] += unit[i] * speech.coerceAtLeast(0.1f)
            }

            val globalSegments = result.segments.map { it.copy(speaker = mapping.getValue(it.speaker)) }
            for (seg in globalSegments) {
                val start = maxOf(seg.startSec, result.window.ownStartSec)
                val end = minOf(seg.endSec, result.window.ownEndSec)
                if (end - start >= MIN_SEGMENT_SEC) merged += seg.copy(startSec = start, endSec = end)
            }
            previous = result.window to globalSegments
        }

        merged.sortBy { it.startSec }
        val order = HashMap<Int, Int>()
        for (seg in merged) order.getOrPut(seg.speaker) { order.size }
        return merged.map { it.copy(speaker = order.getValue(it.speaker)) }
    }

    /**
     * Folds "speakers" with less than [minSpeechSec] of speech into a real
     * one: fast-clustering uses complete linkage, so a single odd embedding
     * (a cough, a few syllables, noise) becomes its own cluster. Measured on
     * Pixel 8 Pro: 1 voice over 8 min gave one 421 s speaker plus 6 to 8
     * clusters of 0.3 to 3.8 s, whatever the clustering threshold. Each minor
     * segment goes to the speaker closest in time (most overlapping first);
     * the speaker with the most speech is always kept.
     */
    fun absorbMinorSpeakers(segments: List<SpeakerSegment>, minSpeechSec: Float = MIN_SPEAKER_SEC): List<SpeakerSegment> {
        val speech = segments.groupBy { it.speaker }.mapValues { (_, s) -> s.sumOf { (it.endSec - it.startSec).toDouble() } }
        val main = speech.maxByOrNull { it.value }?.key ?: return segments
        val kept = speech.filter { it.value >= minSpeechSec || it.key == main }.keys
        if (kept.size == speech.size) return segments
        val keptSegments = segments.filter { it.speaker in kept }.sortedBy { it.startSec }
        return segments.map { seg ->
            if (seg.speaker in kept) seg
            else seg.copy(speaker = keptSegments.minBy { distance(seg, it) }.speaker)
        }
    }

    /** [absorbMinorSpeakers] on one window, dropping the absorbed speakers' embeddings. */
    fun absorbMinorSpeakers(result: WindowResult, minSpeechSec: Float = MIN_SPEAKER_SEC): WindowResult {
        val segments = absorbMinorSpeakers(result.segments, minSpeechSec)
        val speakers = segments.map { it.speaker }.toSet()
        return result.copy(segments = segments, embeddings = result.embeddings.filterKeys { it in speakers })
    }

    /** Gap in seconds between two segments; minus their overlap when they overlap. */
    private fun distance(a: SpeakerSegment, b: SpeakerSegment): Float =
        maxOf(a.startSec, b.startSec) - minOf(a.endSec, b.endSec)

    /** Seconds within [from, to) where both lists have speech. */
    private fun sharedSeconds(a: List<SpeakerSegment>, b: List<SpeakerSegment>, from: Float, to: Float): Float {
        var total = 0f
        for (x in a) for (y in b) {
            val start = maxOf(x.startSec, y.startSec, from)
            val end = minOf(x.endSec, y.endSec, to)
            if (end > start) total += end - start
        }
        return total
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return -1f
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na == 0.0 || nb == 0.0) return -1f
        return (dot / (sqrt(na) * sqrt(nb))).toFloat()
    }

    private fun normalized(v: FloatArray): FloatArray? {
        val norm = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
        if (norm == 0f) return null
        return FloatArray(v.size) { v[it] / norm }
    }
}
