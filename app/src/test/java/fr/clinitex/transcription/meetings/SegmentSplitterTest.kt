package fr.clinitex.transcription.meetings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class SegmentSplitterTest {

    private val rate = 16000

    /** Continuous "speech": a 220 Hz tone, with silent gaps at [gapsSec] (200 ms each). */
    private fun speech(seconds: Float, gapsSec: List<Float> = emptyList()): FloatArray {
        val out = FloatArray((seconds * rate).toInt()) { i -> 0.3f * sin(2 * Math.PI * 220 * i / rate).toFloat() }
        for (gap in gapsSec) {
            val from = (gap * rate).toInt()
            for (i in from until minOf(from + rate / 5, out.size)) out[i] = 0f
        }
        return out
    }

    private fun assertContiguous(pieces: List<SegmentSplitter.Piece>, startSec: Float, samples: FloatArray) {
        assertEquals(startSec, pieces.first().startSec, 1e-4f)
        assertEquals(startSec + samples.size.toFloat() / rate, pieces.last().endSec, 1e-3f)
        pieces.zipWithNext { a, b -> assertEquals(a.endSec, b.startSec, 1e-4f) }
        pieces.forEach { assertEquals(it.samples.size.toFloat() / rate, it.endSec - it.startSec, 1e-3f) }
        assertEquals(samples.size, pieces.sumOf { it.samples.size })
        var offset = 0
        for (p in pieces) {
            for (i in p.samples.indices step 997) assertEquals(samples[offset + i], p.samples[i], 0f)
            offset += p.samples.size
        }
    }

    @Test
    fun `a 29 s segment is left intact`() {
        val samples = speech(29f)
        val pieces = SegmentSplitter.split(12.5f, samples, rate)
        assertEquals(1, pieces.size)
        assertSame(samples, pieces[0].samples)
        assertEquals(12.5f, pieces[0].startSec, 1e-4f)
        assertEquals(41.5f, pieces[0].endSec, 1e-4f)
    }

    @Test
    fun `a 351 s segment is cut into pieces of 30 s at most, with continuous times`() {
        val samples = speech(351f)
        val start = 120.25f
        val pieces = SegmentSplitter.split(start, samples, rate)
        assertTrue("${pieces.size} pieces", pieces.size in 12..18)
        pieces.forEach { assertTrue("piece of ${it.endSec - it.startSec} s", it.samples.size <= 30 * rate) }
        pieces.dropLast(1).forEach { assertTrue(it.samples.size >= 20 * rate) }
        assertContiguous(pieces, start, samples)
    }

    @Test
    fun `cuts in the quietest spot between 20 and 30 s`() {
        // Gaps at 15 s (too early), 24 s, then 47 s (24 s + 23 s).
        val samples = speech(70f, listOf(15f, 24f, 47f))
        val pieces = SegmentSplitter.split(0f, samples, rate)
        assertEquals(3, pieces.size)
        assertTrue("first cut at ${pieces[0].endSec}", pieces[0].endSec in 24f..24.2f)
        assertTrue("second cut at ${pieces[1].endSec}", pieces[1].endSec in 47f..47.2f)
        assertContiguous(pieces, 0f, samples)
    }

    @Test
    fun `never leaves a tail too short to decode`() {
        val samples = speech(30.05f)
        val pieces = SegmentSplitter.split(0f, samples, rate)
        assertEquals(2, pieces.size)
        assertTrue(pieces.last().samples.size >= rate)
        assertContiguous(pieces, 0f, samples)
    }
}
