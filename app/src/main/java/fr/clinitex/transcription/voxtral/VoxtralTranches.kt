package fr.clinitex.transcription.voxtral

import fr.clinitex.transcription.meetings.DiarizationWindows
import fr.clinitex.transcription.meetings.SpeakerLabeler
import fr.clinitex.transcription.meetings.SpeakerLabeler.SpeakerSegment
import fr.clinitex.transcription.meetings.WavSamples

/**
 * Pure logic of the Voxtral variant: the recording is sent to Mistral in
 * tranches of 10 minutes, each one starting 30 s before its own 10 minutes
 * (the end of the previous tranche), so a sentence or a speaker turn cut by
 * the tranche boundary is heard whole by one of the two requests.
 *
 * Putting the tranches back together:
 *  1. [cutPoint]: in each overlap, a point where neither tranche has a
 *     segment straddling it (a pause), closest to the middle of the overlap;
 *     each tranche keeps the segments whose middle falls between its cuts, so
 *     no sentence is lost or written twice;
 *  2. speakers: Voxtral's labels (speaker_1, speaker_2…) only mean something
 *     within one request. They are linked from tranche to tranche by
 *     [DiarizationWindows.link], as the on-device diarization windows (lot 4):
 *     agreement in the overlap first, then the voice embedding (TitaNet-S,
 *     computed on the phone), otherwise a new speaker.
 * Kept free of Android types so it is unit-testable.
 */
object VoxtralTranches {

    const val TRANCHE_SEC = 600
    const val OVERLAP_SEC = 30

    /** A last tranche with less new audio than this is not sent. */
    const val MIN_NEW_SEC = 1f

    /** A segment ending (or starting) this close to the edge of its tranche
     * was probably cut by it: never used as a cut point. */
    private const val EDGE_SEC = 1f

    /** Timestamps of two requests on the same audio differ slightly: a
     * segment straddles a cut only if it goes past it by more than this. */
    private const val STRADDLE_TOLERANCE_SEC = 0.3f

    /** Consecutive segments of the same speaker are joined into paragraphs of about this length. */
    const val PARAGRAPH_SEC = 60f

    /** Audio [startSample, startSample + sampleCount) sent as tranche [index];
     * its own 10 minutes start at [newStartSample]. */
    data class Tranche(
        val index: Int,
        val startSample: Long,
        val sampleCount: Int,
        val newStartSample: Long,
        val sampleRate: Int = WavSamples.SAMPLE_RATE,
    ) {
        val startSec: Float get() = startSample.toFloat() / sampleRate
        val endSec: Float get() = (startSample + sampleCount).toFloat() / sampleRate
    }

    /** One transcribed segment in seconds from the start of the recording,
     * with the speaker label Voxtral gave it in its tranche (null without diarization). */
    data class TimedSegment(val startSec: Float, val endSec: Float, val speakerId: String?, val text: String) {
        val middleSec: Float get() = (startSec + endSec) / 2
    }

    data class TrancheResult(val tranche: Tranche, val segments: List<TimedSegment>)

    /**
     * Tranches of a recording of [totalSamples]. While recording ([final] =
     * false) only the tranches whose 10 minutes are fully recorded; at the
     * end, all of them, the last one possibly shorter.
     */
    fun plan(
        totalSamples: Long,
        final: Boolean,
        sampleRate: Int = WavSamples.SAMPLE_RATE,
        trancheSec: Int = TRANCHE_SEC,
        overlapSec: Int = OVERLAP_SEC,
    ): List<Tranche> {
        val tranche = trancheSec.toLong() * sampleRate
        val overlap = overlapSec.toLong() * sampleRate
        require(overlap in 0 until tranche) { "overlap must be shorter than the tranche" }
        val out = mutableListOf<Tranche>()
        var index = 0
        while (true) {
            val newStart = index * tranche
            val newEnd = minOf(newStart + tranche, totalSamples)
            if (newEnd - newStart < (MIN_NEW_SEC * sampleRate).toLong()) break
            if (!final && newStart + tranche > totalSamples) break
            val start = maxOf(0L, newStart - overlap)
            out += Tranche(index, start, (newEnd - start).toInt(), newStart, sampleRate)
            index++
        }
        return out
    }

    /**
     * Where the tranche [next] takes over from [previous] in their overlap
     * [overlapStartSec, overlapEndSec): a pause in both transcriptions (no
     * segment straddles it), the closest to the middle of the overlap.
     * Without a common pause, the point with the fewest straddling segments;
     * those are then kept on both sides ([keeps]): a few words twice rather
     * than lost.
     */
    fun cutPoint(
        previous: List<TimedSegment>,
        next: List<TimedSegment>,
        overlapStartSec: Float,
        overlapEndSec: Float,
    ): Float {
        val middle = (overlapStartSec + overlapEndSec) / 2
        val from = overlapStartSec + EDGE_SEC
        val to = overlapEndSec - EDGE_SEC
        return (previous.map { it.endSec } + next.map { it.startSec } + middle)
            .filter { it in from..to }
            .ifEmpty { listOf(middle) }
            .minWith(
                compareBy<Float> { cut -> previous.count { straddles(it, cut) } + next.count { straddles(it, cut) } }
                    .thenBy { kotlin.math.abs(it - middle) }
            )
    }

    private fun straddles(seg: TimedSegment, cut: Float): Boolean =
        seg.startSec < cut - STRADDLE_TOLERANCE_SEC && seg.endSec > cut + STRADDLE_TOLERANCE_SEC

    /** Whether a tranche owning [from, to) keeps [seg]: its middle is in the
     * span, or it straddles one of the cuts (no common pause there). */
    fun keeps(seg: TimedSegment, from: Float, to: Float): Boolean =
        (seg.middleSec >= from && seg.middleSec < to) ||
            (from > 0f && straddles(seg, from)) ||
            (to < Float.MAX_VALUE && straddles(seg, to))

    /** Owned span of each tranche [from, to): between its cut with the previous and with the next tranche. */
    fun ownedSpans(results: List<TrancheResult>): List<Pair<Float, Float>> {
        val cuts = results.zipWithNext { a, b ->
            cutPoint(a.segments, b.segments, b.tranche.startSec, a.tranche.endSec)
        }
        return results.indices.map { i ->
            (if (i == 0) 0f else cuts[i - 1]) to (if (i == results.lastIndex) Float.MAX_VALUE else cuts[i])
        }
    }

    /**
     * Puts the tranches back together: each segment kept once, with its
     * speaker numbered from 1 in order of first speech across the whole
     * recording (as "Intervenant N"). [embeddings]: per tranche, the voice
     * embedding of each Voxtral speaker label (may be empty). Without
     * diarization every segment is speaker 1.
     */
    fun assemble(
        results: List<TrancheResult>,
        embeddings: List<Map<String, FloatArray?>> = emptyList(),
        linkThreshold: Float = DiarizationWindows.LINK_THRESHOLD,
    ): List<SpeakerLabeler.LabeledText> {
        val sorted = results.sortedBy { it.tranche.index }
        val spans = ownedSpans(sorted)

        // Voxtral labels → local ints per tranche, then global ids across tranches.
        val localIds = sorted.map { result ->
            result.segments.sortedBy { it.startSec }.mapNotNull { it.speakerId }.distinct()
                .withIndex().associate { (i, label) -> label to i }
        }
        val windowResults = sorted.mapIndexed { i, result ->
            DiarizationWindows.WindowResult(
                window = DiarizationWindows.Window(
                    startSample = result.tranche.startSample,
                    sampleCount = result.tranche.sampleCount,
                    ownStartSec = spans[i].first,
                    ownEndSec = spans[i].second,
                    sampleRate = result.tranche.sampleRate,
                ),
                segments = result.segments.mapNotNull { seg ->
                    seg.speakerId?.let { SpeakerSegment(seg.startSec, seg.endSec, localIds[i].getValue(it)) }
                },
                embeddings = embeddings.getOrNull(i).orEmpty()
                    .mapNotNull { (label, vector) -> localIds[i][label]?.let { it to vector } }.toMap(),
            )
        }
        val mappings = DiarizationWindows.link(windowResults, linkThreshold)

        val kept = mutableListOf<Pair<TimedSegment, Int?>>()
        sorted.forEachIndexed { i, result ->
            val (from, to) = spans[i]
            for (seg in result.segments) {
                if (seg.text.isBlank() || !keeps(seg, from, to)) continue
                kept += seg to seg.speakerId?.let { mappings[i][localIds[i].getValue(it)] }
            }
        }
        kept.sortBy { it.first.startSec }

        val order = HashMap<Int, Int>()
        return kept.map { (seg, global) ->
            val speaker = if (global == null) 1 else order.getOrPut(global) { order.size } + 1
            SpeakerLabeler.LabeledText(seg.startSec, seg.endSec, speaker, seg.text.trim())
        }
    }

    /** Joins consecutive segments of the same speaker into paragraphs of about [maxSec]. */
    fun paragraphs(labeled: List<SpeakerLabeler.LabeledText>, maxSec: Float = PARAGRAPH_SEC): List<SpeakerLabeler.LabeledText> {
        val out = mutableListOf<SpeakerLabeler.LabeledText>()
        for (seg in labeled) {
            val last = out.lastOrNull()
            if (last != null && last.speaker == seg.speaker && seg.endSec - last.startSec <= maxSec) {
                out[out.lastIndex] = last.copy(endSec = seg.endSec, text = last.text + " " + seg.text)
            } else {
                out += seg
            }
        }
        return out
    }
}
