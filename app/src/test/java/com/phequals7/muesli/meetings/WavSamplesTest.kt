package com.phequals7.muesli.meetings

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WavSamplesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** 44-byte header (content irrelevant to the reader) + little-endian samples. */
    private fun wav(samples: ShortArray, trailing: ByteArray = ByteArray(0)): File {
        val bytes = ByteArray(44 + samples.size * 2 + trailing.size)
        "RIFF".toByteArray().copyInto(bytes, 0) // header must be skipped, not decoded
        samples.forEachIndexed { i, s ->
            bytes[44 + i * 2] = (s.toInt() and 0xFF).toByte()
            bytes[44 + i * 2 + 1] = (s.toInt() shr 8 and 0xFF).toByte()
        }
        trailing.copyInto(bytes, 44 + samples.size * 2)
        return tmp.newFile().apply { writeBytes(bytes) }
    }

    @Test
    fun `skips the header and converts little-endian 16-bit samples`() {
        val file = wav(shortArrayOf(0, 16384, -16384, Short.MAX_VALUE, Short.MIN_VALUE, 1, -1))
        val expected = floatArrayOf(0f, 0.5f, -0.5f, 32767 / 32768f, -1f, 1 / 32768f, -1 / 32768f)
        assertArrayEquals(expected, WavSamples.readMono16k(file), 0f)
        assertEquals(7L, WavSamples.sampleCount(file))
    }

    @Test
    fun `ignores an odd trailing byte`() {
        val file = wav(shortArrayOf(100, -200), trailing = byteArrayOf(0x7F))
        assertEquals(2L, WavSamples.sampleCount(file))
        assertArrayEquals(floatArrayOf(100 / 32768f, -200 / 32768f), WavSamples.readMono16k(file), 0f)
    }

    @Test
    fun `empty file, header only or truncated header give no samples`() {
        assertEquals(0, WavSamples.readMono16k(tmp.newFile()).size)
        assertEquals(0, WavSamples.readMono16k(wav(ShortArray(0))).size)
        val truncated = tmp.newFile().apply { writeBytes(ByteArray(20)) }
        assertEquals(0, WavSamples.readMono16k(truncated).size)
        assertEquals(0L, WavSamples.sampleCount(truncated))
    }

    @Test
    fun `reads across buffer boundaries`() {
        // 100 000 samples > the 32 768 samples of one read buffer.
        val samples = ShortArray(100_000) { ((it * 7) % 65536 - 32768).toShort() }
        val out = WavSamples.readMono16k(wav(samples))
        assertEquals(samples.size, out.size)
        for (i in samples.indices) assertEquals(samples[i] / 32768f, out[i], 0f)
    }

    @Test
    fun `ranged read returns one window and clamps at the end`() {
        val samples = ShortArray(50_000) { (it % 30_000).toShort() }
        val file = wav(samples)
        val window = WavSamples.readMono16k(file, fromSample = 40_000, maxSamples = 5_000)
        assertEquals(5_000, window.size)
        assertEquals(samples[40_000] / 32768f, window[0], 0f)
        assertEquals(samples[44_999] / 32768f, window[4_999], 0f)
        assertEquals(2_000, WavSamples.readMono16k(file, 48_000, 10_000).size)
        assertEquals(0, WavSamples.readMono16k(file, 60_000, 10_000).size)
    }

    /**
     * Lot 4 measurement on a synthetic one-hour WAV (115 MB written to a temp
     * folder, so opt-in): run with CLINDY_BENCH=1. Prints the bytes allocated
     * by the old readBytes() approach, by readMono16k on the whole file, and
     * by one 10-minute diarization window.
     */
    @Test
    fun `benchmark one hour of audio`() {
        assumeTrue(System.getenv("CLINDY_BENCH") != null)
        val total = 3600L * WavSamples.SAMPLE_RATE
        val file = tmp.newFile("hour.wav")
        file.outputStream().buffered().use { out ->
            out.write(ByteArray(44))
            val block = ByteArray(32_000) { (it % 251).toByte() }
            repeat((total * 2 / block.size).toInt()) { out.write(block) }
        }
        // java.lang.management is not in android.jar: reach the JVM's per-thread
        // allocation counter by reflection.
        val threads = Class.forName("java.lang.management.ManagementFactory")
            .getMethod("getThreadMXBean").invoke(null)
        val counter = Class.forName("com.sun.management.ThreadMXBean")
            .getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
        val id = Thread.currentThread().id
        fun allocated(block: () -> Any): Long {
            val before = counter.invoke(threads, id) as Long
            block()
            return counter.invoke(threads, id) as Long - before
        }
        val old = allocated {
            val bytes = file.readBytes()
            val out = FloatArray((bytes.size - 44) / 2)
            for (i in out.indices) {
                out[i] = ((bytes[44 + i * 2 + 1].toInt() shl 8) or (bytes[44 + i * 2].toInt() and 0xFF)) / 32768f
            }
            out
        }
        val streamed = allocated { WavSamples.readMono16k(file) }
        val window = DiarizationWindows.plan(total).first()
        val windowed = allocated { WavSamples.readMono16k(file, window.startSample, window.sampleCount) }
        val mb = { b: Long -> "%.1f MB".format(b / 1_000_000.0) }
        println("BENCH 1 h WAV (${mb(file.length())}): readBytes+FloatArray=${mb(old)}, " +
            "readMono16k=${mb(streamed)}, one window (of ${DiarizationWindows.plan(total).size})=${mb(windowed)}")
    }
}
