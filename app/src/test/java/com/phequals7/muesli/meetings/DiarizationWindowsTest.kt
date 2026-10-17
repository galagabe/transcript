package com.phequals7.muesli.meetings

import com.phequals7.muesli.meetings.DiarizationWindows.WindowResult
import com.phequals7.muesli.meetings.SpeakerLabeler.SpeakerSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiarizationWindowsTest {

    private val rate = 16_000

    @Test
    fun `up to ten minutes is a single window as before`() {
        val windows = DiarizationWindows.plan(600L * rate)
        assertEquals(1, windows.size)
        assertEquals(0L, windows[0].startSample)
        assertEquals(600 * rate, windows[0].sampleCount)
        assertTrue(DiarizationWindows.plan(0).isEmpty())
    }

    @Test
    fun `one hour splits into even overlapping windows of at most ten minutes`() {
        val total = 3600L * rate
        val windows = DiarizationWindows.plan(total)
        assertEquals(7, windows.size) // ceil((3600 - 30) / 570)
        assertEquals(0L, windows.first().startSample)
        assertEquals(total, windows.last().startSample + windows.last().sampleCount)
        for (w in windows) assertTrue(w.sampleCount <= 600 * rate)
        for ((a, b) in windows.zipWithNext()) {
            // Consecutive windows overlap by 30 s and own adjacent spans.
            assertEquals(30f, a.endSec - b.startSec, 0.01f)
            assertEquals(a.ownEndSec, b.ownStartSec, 0f)
            assertTrue(b.ownStartSec > b.startSec && b.ownStartSec < a.endSec)
        }
        // Even split: no tiny last window.
        val sizes = windows.map { it.sampleCount }
        assertTrue(sizes.max() - sizes.min() <= 30 * rate)
    }

    @Test
    fun `just over ten minutes gives two balanced windows`() {
        val windows = DiarizationWindows.plan(610L * rate)
        assertEquals(2, windows.size)
        assertEquals(320f, windows[0].endSec, 0.01f)
        assertEquals(290f, windows[1].startSec, 0.01f)
    }

    private fun windowsOf(totalSec: Long) = DiarizationWindows.plan(totalSec * rate, windowSec = 100f, overlapSec = 20f)

    private fun seg(start: Float, end: Float, speaker: Int) = SpeakerSegment(start, end, speaker)

    @Test
    fun `single window renumbers speakers by first appearance`() {
        val w = windowsOf(60).single()
        val merged = DiarizationWindows.merge(
            listOf(WindowResult(w, listOf(seg(0f, 5f, 3), seg(5f, 9f, 1), seg(9f, 12f, 3)), emptyMap()))
        )
        assertEquals(listOf(0, 1, 0), merged.map { it.speaker })
        assertEquals(listOf(0f, 5f, 9f), merged.map { it.startSec })
    }

    @Test
    fun `speakers are linked through the overlap even without embeddings`() {
        val (w0, w1) = windowsOf(180) // [0,100) and [80,180), owned split at 90
        val merged = DiarizationWindows.merge(
            listOf(
                WindowResult(w0, listOf(seg(10f, 50f, 0), seg(50f, 95f, 1)), emptyMap()),
                // Window 1 numbers the same people the other way round.
                WindowResult(w1, listOf(seg(80f, 95f, 0), seg(95f, 150f, 1)), emptyMap()),
            )
        )
        // 80–95 is speaker B in both windows; 95–150 is someone new.
        assertEquals(
            listOf(10f to 0, 50f to 1, 90f to 1, 95f to 2),
            merged.map { it.startSec to it.speaker },
        )
        // The overlap is not counted twice: B's turn is cut at the owned boundary (90).
        assertEquals(90f, merged[1].endSec, 0f)
    }

    @Test
    fun `a returning speaker silent in the overlap is linked by voice`() {
        val (w0, w1) = windowsOf(180)
        val voiceA = floatArrayOf(1f, 0.1f, 0f)
        val voiceB = floatArrayOf(0f, 1f, 0.2f)
        val voiceC = floatArrayOf(0f, 0f, 1f)
        val merged = DiarizationWindows.merge(
            listOf(
                WindowResult(w0, listOf(seg(0f, 40f, 0), seg(40f, 99f, 1)), mapOf(0 to voiceA, 1 to voiceB)),
                WindowResult(
                    w1,
                    listOf(seg(80f, 99f, 0), seg(120f, 140f, 1), seg(140f, 170f, 2)),
                    mapOf(0 to voiceB, 1 to floatArrayOf(0.9f, 0.2f, 0.1f), 2 to voiceC),
                ),
            )
        )
        val speakers = merged.associate { it.startSec to it.speaker }
        assertEquals(0, speakers[0f])   // A
        assertEquals(1, speakers[40f])  // B
        assertEquals(0, speakers[120f]) // A again, recognized by voice
        assertEquals(2, speakers[140f]) // new person
    }

    @Test
    fun `links are one-to-one within a window`() {
        val (w0, w1) = windowsOf(180)
        val voice = floatArrayOf(1f, 0f)
        val merged = DiarizationWindows.merge(
            listOf(
                WindowResult(w0, listOf(seg(0f, 50f, 0)), mapOf(0 to voice)),
                // Two local speakers that both look like A: only the closest is A.
                WindowResult(
                    w1,
                    listOf(seg(100f, 120f, 0), seg(120f, 140f, 1)),
                    mapOf(0 to floatArrayOf(0.8f, 0.6f), 1 to floatArrayOf(1f, 0.05f)),
                ),
            )
        )
        assertEquals(listOf(0, 1, 0), merged.map { it.speaker })
    }

    @Test
    fun `dissimilar voices stay distinct`() {
        val (w0, w1) = windowsOf(180)
        val merged = DiarizationWindows.merge(
            listOf(
                WindowResult(w0, listOf(seg(0f, 50f, 0)), mapOf(0 to floatArrayOf(1f, 0f))),
                WindowResult(w1, listOf(seg(100f, 150f, 0)), mapOf(0 to floatArrayOf(0f, 1f))),
            )
        )
        assertEquals(listOf(0, 1), merged.map { it.speaker })
    }

    @Test
    fun `merged segments feed SpeakerLabeler unchanged`() {
        val (w0, w1) = windowsOf(180)
        val voice = floatArrayOf(1f, 0f)
        val segments = DiarizationWindows.merge(
            listOf(
                WindowResult(w0, listOf(seg(0f, 60f, 0)), mapOf(0 to voice)),
                WindowResult(w1, listOf(seg(120f, 170f, 0)), mapOf(0 to voice)),
            )
        )
        val labeled = SpeakerLabeler.assign(
            listOf(SpeakerLabeler.TimedText(5f, 20f, "Bonjour"), SpeakerLabeler.TimedText(125f, 140f, "Merci")),
            segments,
        )
        assertEquals(listOf(1, 1), labeled.map { it.speaker })
    }

    @Test
    fun `minor speakers are folded into the closest real one`() {
        // One voice plus two complete-linkage leftovers (as on Pixel 8 Pro).
        val segments = listOf(
            seg(0f, 60f, 0),
            seg(60.5f, 61.5f, 11), // just after speaker 0
            seg(70f, 130f, 4),
            seg(129f, 131f, 27), // overlaps speaker 4 at its end
            seg(140f, 200f, 0),
        )
        val cleaned = DiarizationWindows.absorbMinorSpeakers(segments, minSpeechSec = 5f)
        assertEquals(listOf(0, 0, 4, 4, 0), cleaned.map { it.speaker })
        assertEquals(segments.map { it.startSec to it.endSec }, cleaned.map { it.startSec to it.endSec })
    }

    @Test
    fun `speakers above the minimum and the main speaker are kept`() {
        val segments = listOf(seg(0f, 6f, 2), seg(7f, 13f, 5), seg(14f, 30f, 7))
        assertEquals(segments, DiarizationWindows.absorbMinorSpeakers(segments, minSpeechSec = 5f))
        assertEquals(listOf(7, 7, 7), DiarizationWindows.absorbMinorSpeakers(segments, minSpeechSec = 8f).map { it.speaker })
        // Nothing reaches the minimum: everything goes to the main speaker.
        assertEquals(listOf(2, 2), DiarizationWindows.absorbMinorSpeakers(listOf(seg(0f, 3f, 2), seg(5f, 6f, 1)), 5f).map { it.speaker })
        assertTrue(DiarizationWindows.absorbMinorSpeakers(emptyList(), 5f).isEmpty())
    }

    @Test
    fun `absorbed speakers lose their embedding and never claim a link`() {
        val (w0, w1) = windowsOf(180)
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        val results = listOf(
            WindowResult(w0, listOf(seg(0f, 40f, 0), seg(40f, 80f, 1)), mapOf(0 to a, 1 to b)),
            // The 1.5 s leftover sounds like A and comes first: unfolded, it
            // would take A's slot from the real A (links are one-to-one).
            WindowResult(w1, listOf(seg(120f, 121.5f, 5), seg(125f, 150f, 3)), mapOf(5 to a, 3 to a)),
        ).map { DiarizationWindows.absorbMinorSpeakers(it, minSpeechSec = 5f) }
        assertEquals(setOf(3), results[1].embeddings.keys)
        val merged = DiarizationWindows.merge(results)
        assertEquals(2, merged.map { it.speaker }.toSet().size)
        assertEquals(listOf(0, 0), merged.filter { it.startSec >= 120f }.map { it.speaker })
    }

    @Test
    fun `cosine similarity`() {
        assertEquals(1f, DiarizationWindows.cosine(floatArrayOf(1f, 2f), floatArrayOf(2f, 4f)), 1e-6f)
        assertEquals(0f, DiarizationWindows.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 3f)), 1e-6f)
        assertEquals(-1f, DiarizationWindows.cosine(floatArrayOf(0f, 0f), floatArrayOf(1f, 0f)), 0f)
    }
}
