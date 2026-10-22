package fr.clinitex.transcription.meetings

/**
 * Re-cuts the speech segments coming out of Silero VAD so none is longer than
 * [MAX_SEC]. Silero's `maxSpeechDuration` only raises its threshold: on
 * continuous speech it let 170 s and 351 s segments through (Pixel 8 Pro,
 * 20/10/2026). Parakeet then decodes them in one block and passed 3 GB, and
 * Whisper reads 30 s at most.
 *
 * A long segment is cut at its quietest point (lowest energy over
 * [WINDOW_SEC] windows) between [MIN_SEC] and [MAX_SEC] after the previous
 * cut, so a word is not cut in two. The cut never leaves a tail shorter than
 * [MIN_TAIL_SEC] (the decoder skips pieces under 0.5 s). Pieces are
 * contiguous: each starts where the previous one ends. Kept free of
 * Android/sherpa types so it is unit-testable.
 */
object SegmentSplitter {

    const val MAX_SEC = 30f
    const val MIN_SEC = 20f
    const val WINDOW_SEC = 0.1f
    const val MIN_TAIL_SEC = 1f

    /** A piece of a segment, with its position in the recording. */
    class Piece(val startSec: Float, val endSec: Float, val samples: FloatArray)

    /** Splits [samples] (starting at [startSec] in the recording); a segment of
     * at most [MAX_SEC] comes back as a single piece, uncopied. */
    fun split(startSec: Float, samples: FloatArray, sampleRate: Int): List<Piece> {
        val max = (MAX_SEC * sampleRate).toInt()
        if (samples.size <= max) {
            return listOf(Piece(startSec, startSec + samples.size.toFloat() / sampleRate, samples))
        }
        val min = (MIN_SEC * sampleRate).toInt()
        val window = (WINDOW_SEC * sampleRate).toInt()
        val tail = (MIN_TAIL_SEC * sampleRate).toInt()

        val out = mutableListOf<Piece>()
        var pos = 0
        while (samples.size - pos > max) {
            val cut = quietestCut(samples, pos + min, minOf(pos + max, samples.size - tail), window)
            out += piece(startSec, samples, pos, cut, sampleRate)
            pos = cut
        }
        out += piece(startSec, samples, pos, samples.size, sampleRate)
        return out
    }

    /** Middle of the lowest-energy window lying in [from, to]; the latest one
     * on ties, so pieces stay as long as possible. */
    private fun quietestCut(samples: FloatArray, from: Int, to: Int, window: Int): Int {
        var best = to
        var bestEnergy = Double.MAX_VALUE
        var w = from
        while (w + window <= to) {
            var energy = 0.0
            for (i in w until w + window) energy += samples[i] * samples[i]
            if (energy <= bestEnergy) {
                bestEnergy = energy
                best = w + window / 2
            }
            w += window
        }
        return best
    }

    private fun piece(startSec: Float, samples: FloatArray, from: Int, to: Int, sampleRate: Int) =
        Piece(
            startSec + from.toFloat() / sampleRate,
            startSec + to.toFloat() / sampleRate,
            samples.copyOfRange(from, to),
        )
}
