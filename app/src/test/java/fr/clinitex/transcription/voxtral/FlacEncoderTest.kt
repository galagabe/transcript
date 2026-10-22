package fr.clinitex.transcription.voxtral

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * [FlacEncoder] round trip through a small decoder of the same FLAC subset
 * (fixed predictors, verbatim, one Rice partition), checking sync codes and
 * both CRCs on every frame.
 */
class FlacEncoderTest {

    @Test
    fun `speech-like audio decodes to the same samples and is much smaller than WAV`() {
        val rate = 16_000
        val random = Random(7)
        val samples = ShortArray(rate * 20) { i ->
            val t = i.toDouble() / rate
            val envelope = 0.5 + 0.5 * sin(2 * PI * 3 * t)
            (envelope * (6000 * sin(2 * PI * 180 * t) + 2500 * sin(2 * PI * 720 * t)) + random.nextInt(-200, 200)).toInt().toShort()
        }
        val flac = FlacEncoder.encode(samples, rate)
        val decoded = MiniFlacDecoder(flac)
        assertEquals(rate, decoded.sampleRate)
        assertEquals(samples.size.toLong(), decoded.totalSamples)
        assertArrayEquals(samples, decoded.samples)
        assertTrue("FLAC ${flac.size} bytes for ${samples.size * 2} bytes of PCM", flac.size < samples.size * 2 * 0.7)
    }

    @Test
    fun `noise, extremes, silence and a short last block survive`() {
        val random = Random(42)
        val samples = ShortArray(FlacEncoder.BLOCK_SIZE * 3 + 123) { i ->
            when {
                i < FlacEncoder.BLOCK_SIZE -> random.nextInt(Short.MIN_VALUE.toInt(), Short.MAX_VALUE + 1).toShort() // verbatim
                i < 2 * FlacEncoder.BLOCK_SIZE -> if (i % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE
                i < 3 * FlacEncoder.BLOCK_SIZE -> 0
                else -> (i * 37).toShort()
            }
        }
        assertArrayEquals(samples, MiniFlacDecoder(FlacEncoder.encode(samples, 16_000)).samples)
    }

    @Test
    fun `tiny inputs`() {
        for (size in listOf(0, 1, 3, 17)) {
            val samples = ShortArray(size) { (it * 1000 - 500).toShort() }
            assertArrayEquals(samples, MiniFlacDecoder(FlacEncoder.encode(samples, 16_000)).samples)
        }
    }

    /** Decoder for exactly what FlacEncoder writes (mono, 16-bit). */
    private class MiniFlacDecoder(private val data: ByteArray) {
        var pos = 0L // in bits
        var sampleRate = 0
        var totalSamples = 0L
        val samples: ShortArray

        init {
            assertEquals("fLaC", String(data, 0, 4, Charsets.US_ASCII))
            pos = 32
            assertEquals(1L, bits(1)) // last block
            assertEquals(0L, bits(7)) // STREAMINFO
            assertEquals(34L, bits(24))
            bits(16); bits(16); bits(24); bits(24)
            sampleRate = bits(20).toInt()
            assertEquals(0L, bits(3))
            assertEquals(15L, bits(5))
            totalSamples = (bits(4) shl 32) or bits(32)
            pos += 128 // MD5
            val out = ShortArray(totalSamples.toInt())
            var written = 0
            var frameNumber = 0L
            while (written < out.size) {
                val frameStart = (pos / 8).toInt()
                assertEquals(0b11111111111110L, bits(14))
                bits(2)
                val sizeCode = bits(4).toInt()
                bits(4) // sample rate code
                assertEquals(0L, bits(4))
                assertEquals(0b100L, bits(3))
                bits(1)
                assertEquals(frameNumber, utf8())
                val blockSize = when (sizeCode) {
                    0b1100 -> 4096
                    0b0111 -> bits(16).toInt() + 1
                    else -> error("block size code $sizeCode")
                }
                val headerEnd = (pos / 8).toInt()
                assertEquals(FlacEncoder.crc8(data.copyOfRange(frameStart, headerEnd)).toLong(), bits(8))

                assertEquals(0L, bits(1))
                val type = bits(6).toInt()
                assertEquals(0L, bits(1))
                val block = IntArray(blockSize)
                if (type == 0b000001) {
                    for (i in 0 until blockSize) block[i] = signed(bits(16), 16)
                } else {
                    assertEquals(0b001000, type and 0b111000)
                    val order = type and 0b111
                    for (i in 0 until order) block[i] = signed(bits(16), 16)
                    assertEquals(0L, bits(2))
                    assertEquals(0L, bits(4))
                    val k = bits(4).toInt()
                    for (i in order until blockSize) {
                        var q = 0L
                        while (bits(1) == 0L) q++
                        val u = (q shl k) or (if (k > 0) bits(k) else 0L)
                        val r = ((u ushr 1) xor -(u and 1)).toInt()
                        block[i] = r + when (order) {
                            0 -> 0
                            1 -> block[i - 1]
                            2 -> 2 * block[i - 1] - block[i - 2]
                            3 -> 3 * block[i - 1] - 3 * block[i - 2] + block[i - 3]
                            else -> 4 * block[i - 1] - 6 * block[i - 2] + 4 * block[i - 3] - block[i - 4]
                        }
                    }
                }
                if (pos % 8 != 0L) pos += 8 - pos % 8
                val frameEnd = (pos / 8).toInt()
                assertEquals(FlacEncoder.crc16(data.copyOfRange(frameStart, frameEnd)).toLong(), bits(16))
                for (v in block) out[written++] = v.toShort()
                frameNumber++
            }
            assertEquals(data.size * 8L, pos)
            samples = out
        }

        fun bits(n: Int): Long {
            var v = 0L
            repeat(n) {
                val byte = data[(pos / 8).toInt()].toInt()
                v = (v shl 1) or ((byte shr (7 - (pos % 8).toInt())) and 1).toLong()
                pos++
            }
            return v
        }

        fun utf8(): Long {
            val first = bits(8)
            if (first and 0x80 == 0L) return first
            var count = 0
            var mask = 0x80L
            while (first and mask != 0L) {
                count++
                mask = mask shr 1
            }
            var v = first and (mask - 1)
            repeat(count - 1) { v = (v shl 6) or (bits(8) and 0x3F) }
            return v
        }

        fun signed(v: Long, n: Int): Int = if (v and (1L shl (n - 1)) != 0L) (v - (1L shl n)).toInt() else v.toInt()
    }
}
