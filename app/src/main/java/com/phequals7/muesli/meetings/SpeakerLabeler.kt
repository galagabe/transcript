package com.phequals7.muesli.meetings

/**
 * Pure speaker-labeling logic for meeting transcripts: maps decoded text
 * chunks onto diarization segments by maximum time overlap and renders a
 * readable, speaker-grouped transcript. Kept free of Android/sherpa types so
 * it is unit-testable (iOS renders a similar speaker-grouped transcript in
 * the meeting detail view).
 */
object SpeakerLabeler {

    /** One decoded chunk of transcript with its position in the recording. */
    data class TimedText(val startSec: Float, val endSec: Float, val text: String)

    /** One diarization segment: who spoke when (speaker ids count from 0). */
    data class SpeakerSegment(val startSec: Float, val endSec: Float, val speaker: Int)

    /** A decoded chunk with its speaker number as displayed ("Intervenant N", from 1). */
    data class LabeledText(val startSec: Float, val endSec: Float, val speaker: Int, val text: String)

    /** Speaker with the largest time overlap with [startSec, endSec), or null
     * when there is no overlap at all. */
    fun dominantSpeaker(startSec: Float, endSec: Float, segments: List<SpeakerSegment>): Int? {
        var bestSpeaker: Int? = null
        var bestOverlap = 0f
        for (seg in segments) {
            val overlap = minOf(endSec, seg.endSec) - maxOf(startSec, seg.startSec)
            if (overlap > bestOverlap) {
                bestOverlap = overlap
                bestSpeaker = seg.speaker
            }
        }
        return bestSpeaker
    }

    /**
     * Gives each chunk the speaker it overlaps most. A chunk with no
     * overlapping diarization segment inherits the previous chunk's speaker;
     * leading chunks without overlap take the first speaker found later.
     * Numbers count from 1, as displayed.
     */
    fun assign(chunks: List<TimedText>, segments: List<SpeakerSegment>): List<LabeledText> {
        val dominant = chunks.map { dominantSpeaker(it.startSec, it.endSec, segments) }
        var current = dominant.firstOrNull { it != null } ?: 0
        return chunks.mapIndexed { index, chunk ->
            current = dominant[index] ?: current
            LabeledText(chunk.startSec, chunk.endSec, current + 1, chunk.text)
        }
    }

    /**
     * Renders chunks as a speaker-grouped transcript. Consecutive chunks from
     * the same speaker merge under one heading (speakers per [assign]).
     *
     * Example:
     *   Intervenant 1 · 0:04
     *   Let's review the launch plan…
     *
     *   Intervenant 2 · 0:31
     *   Sounds good, two things…
     */
    fun render(chunks: List<TimedText>, segments: List<SpeakerSegment>): String =
        render(assign(chunks, segments))

    fun render(labeled: List<LabeledText>): String {
        val out = StringBuilder()
        var currentSpeaker: Int? = null
        for (chunk in labeled) {
            if (chunk.speaker != currentSpeaker) {
                if (currentSpeaker != null) out.append("\n\n")
                out.append("Intervenant ")
                    .append(chunk.speaker)
                    .append(" · ")
                    .append(formatTime(chunk.startSec))
                    .append("\n")
                currentSpeaker = chunk.speaker
            } else {
                out.append("\n\n")
            }
            out.append(chunk.text)
        }
        return out.toString()
    }

    /** mm:ss (hours flatten into minutes, meeting-style). */
    fun formatTime(sec: Float): String {
        val total = sec.toInt().coerceAtLeast(0)
        return "%d:%02d".format(total / 60, total % 60)
    }
}
