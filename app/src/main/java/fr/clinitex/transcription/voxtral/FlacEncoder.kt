package fr.clinitex.transcription.voxtral

import java.io.ByteArrayOutputStream

/**
 * Minimal lossless FLAC encoder for the 16-bit mono PCM of the recorder:
 * fixed predictors (order 0 to 4, the best one per block) and Rice-coded
 * residuals. A 10 min 30 s tranche is ≈20 MB as WAV, roughly half as FLAC,
 * which keeps it under the n8n webhook's request size limit (16 MB by
 * default) without losing anything the diarization relies on.
 *
 * Written here rather than through MediaCodec so it runs (and is tested)
 * on the JVM and does not depend on the phone's codecs.
 */
object FlacEncoder {

    const val BLOCK_SIZE = 4096
    private const val MAX_FIXED_ORDER = 4
    /** 4-bit Rice parameter; 15 is reserved for the escape code. */
    private const val MAX_RICE_PARAM = 14

    fun encode(samples: ShortArray, sampleRate: Int): ByteArray {
        val out = ByteArrayOutputStream(samples.size + 1024)
        out.write("fLaC".toByteArray(Charsets.US_ASCII))
        out.write(streamInfo(samples.size.toLong(), sampleRate))
        var frameNumber = 0L
        var offset = 0
        while (offset < samples.size) {
            val count = minOf(BLOCK_SIZE, samples.size - offset)
            out.write(frame(samples, offset, count, frameNumber, sampleRate))
            offset += count
            frameNumber++
        }
        return out.toByteArray()
    }

    /** Last-block metadata header + STREAMINFO (34 bytes, MD5 left at zero = unknown). */
    private fun streamInfo(totalSamples: Long, sampleRate: Int): ByteArray {
        val w = BitWriter()
        w.write(1, 1) // last metadata block
        w.write(0, 7) // STREAMINFO
        w.write(34, 24)
        val blockSize = if (totalSamples >= BLOCK_SIZE) BLOCK_SIZE else totalSamples.toInt().coerceAtLeast(16)
        w.write(blockSize.toLong(), 16) // min block size
        w.write(blockSize.toLong(), 16) // max block size
        w.write(0, 24) // min frame size: unknown
        w.write(0, 24) // max frame size: unknown
        w.write(sampleRate.toLong(), 20)
        w.write(0, 3) // channels - 1
        w.write(15, 5) // bits per sample - 1
        w.write(totalSamples ushr 32, 4)
        w.write(totalSamples and 0xFFFFFFFFL, 32)
        repeat(4) { w.write(0, 32) } // MD5
        return w.toByteArray()
    }

    private fun frame(samples: ShortArray, offset: Int, count: Int, frameNumber: Long, sampleRate: Int): ByteArray {
        val w = BitWriter()
        w.write(0b11111111111110, 14) // sync code
        w.write(0, 1) // reserved
        w.write(0, 1) // fixed block size stream
        val fullBlock = count == BLOCK_SIZE
        w.write(if (fullBlock) 0b1100L else 0b0111L, 4) // 4096, or 16-bit (size - 1) at the end of the header
        w.write(if (sampleRate == 16_000) 0b0101L else 0b0000L, 4) // 16 kHz, or "see STREAMINFO"
        w.write(0, 4) // mono
        w.write(0b100, 3) // 16 bits per sample
        w.write(0, 1) // reserved
        for (b in utf8(frameNumber)) w.write(b.toLong(), 8)
        if (!fullBlock) w.write((count - 1).toLong(), 16)
        w.write(crc8(w.toByteArray()).toLong(), 8)

        subframe(w, samples, offset, count)

        w.alignToByte()
        w.write(crc16(w.toByteArray()).toLong(), 16)
        return w.toByteArray()
    }

    private fun subframe(w: BitWriter, samples: ShortArray, offset: Int, count: Int) {
        // Best fixed predictor: smallest sum of absolute residuals.
        var bestOrder = 0
        var bestResiduals = IntArray(0)
        var bestSum = Long.MAX_VALUE
        for (order in 0..minOf(MAX_FIXED_ORDER, count - 1).coerceAtLeast(0)) {
            val residuals = IntArray(count - order) { residual(samples, offset + order + it, order) }
            val sum = residuals.sumOf { kotlin.math.abs(it.toLong()) }
            if (sum < bestSum) {
                bestSum = sum
                bestOrder = order
                bestResiduals = residuals
            }
        }

        val (param, riceBits) = bestRiceParameter(bestResiduals)
        val fixedBits = 6L + bestOrder * 16L + riceBits
        if (fixedBits >= count * 16L) {
            // Noise-like block: stored as is.
            w.write(0, 1)
            w.write(0b000001, 6) // VERBATIM
            w.write(0, 1) // no wasted bits
            for (i in 0 until count) w.write(samples[offset + i].toLong(), 16)
            return
        }

        w.write(0, 1)
        w.write((0b001000 or bestOrder).toLong(), 6) // FIXED, order
        w.write(0, 1) // no wasted bits
        for (i in 0 until bestOrder) w.write(samples[offset + i].toLong(), 16) // warm-up samples
        w.write(0, 2) // Rice coding, 4-bit parameter
        w.write(0, 4) // partition order 0: one partition
        w.write(param.toLong(), 4)
        val mask = (1L shl param) - 1
        for (r in bestResiduals) {
            val u = zigzag(r)
            w.writeUnary((u ushr param).toInt())
            if (param > 0) w.write(u and mask, param)
        }
    }

    private fun residual(s: ShortArray, i: Int, order: Int): Int = when (order) {
        0 -> s[i].toInt()
        1 -> s[i] - s[i - 1]
        2 -> s[i] - 2 * s[i - 1] + s[i - 2]
        3 -> s[i] - 3 * s[i - 1] + 3 * s[i - 2] - s[i - 3]
        else -> s[i] - 4 * s[i - 1] + 6 * s[i - 2] - 4 * s[i - 3] + s[i - 4]
    }

    private fun zigzag(r: Int): Long = ((r.toLong() shl 1) xor (r.toLong() shr 63)) and 0xFFFFFFFFL

    /** Rice parameter giving the fewest bits for these residuals, and that size in bits (with the 6-bit header). */
    private fun bestRiceParameter(residuals: IntArray): Pair<Int, Long> {
        var best = 0
        var bestBits = Long.MAX_VALUE
        for (k in 0..MAX_RICE_PARAM) {
            var bits = 6L + residuals.size.toLong() * (k + 1)
            for (r in residuals) bits += zigzag(r) ushr k
            if (bits < bestBits) {
                bestBits = bits
                best = k
            }
        }
        return best to bestBits
    }

    /** FLAC's "UTF-8" coding of the frame number. */
    private fun utf8(value: Long): ByteArray {
        if (value < 0x80) return byteArrayOf(value.toByte())
        var bytes = 2
        while (value >= (1L shl (5 * bytes + 1))) bytes++
        val out = ByteArray(bytes)
        var v = value
        for (i in bytes - 1 downTo 1) {
            out[i] = (0x80L or (v and 0x3F)).toByte()
            v = v ushr 6
        }
        val lead = (0xFF shl (8 - bytes)) and 0xFF
        out[0] = (lead.toLong() or v).toByte()
        return out
    }

    internal fun crc8(data: ByteArray): Int {
        var crc = 0
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            repeat(8) { crc = if (crc and 0x80 != 0) ((crc shl 1) xor 0x07) and 0xFF else (crc shl 1) and 0xFF }
        }
        return crc
    }

    internal fun crc16(data: ByteArray): Int {
        var crc = 0
        for (b in data) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) ((crc shl 1) xor 0x8005) and 0xFFFF else (crc shl 1) and 0xFFFF }
        }
        return crc
    }

    /** Big-endian bit writer (FLAC is MSB first). */
    private class BitWriter {
        private val out = ByteArrayOutputStream()
        private var acc = 0L
        private var bits = 0

        /** Writes the low [n] bits of [value] (n ≤ 32). */
        fun write(value: Long, n: Int) {
            if (n == 0) return
            acc = (acc shl n) or (value and ((1L shl n) - 1))
            bits += n
            while (bits >= 8) {
                bits -= 8
                out.write(((acc ushr bits) and 0xFF).toInt())
            }
            acc = acc and ((1L shl bits) - 1)
        }

        /** [q] zeros then a one. */
        fun writeUnary(q: Int) {
            var rest = q
            while (rest >= 32) {
                write(0, 32)
                rest -= 32
            }
            write(1, rest + 1)
        }

        fun alignToByte() {
            if (bits > 0) write(0, 8 - bits)
        }

        /** Bytes written so far (call when byte-aligned). */
        fun toByteArray(): ByteArray = out.toByteArray()
    }
}
