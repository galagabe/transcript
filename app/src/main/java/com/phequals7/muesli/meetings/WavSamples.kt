package com.phequals7.muesli.meetings

import java.io.File
import java.io.RandomAccessFile

/**
 * Streams the meeting WAV (16 kHz mono 16-bit PCM, 44-byte header, as written
 * by [MeetingRecorderService]) back into float samples for diarization.
 *
 * Reads in small buffered chunks and only allocates the output FloatArray:
 * the old `file.readBytes()` held the whole byte array next to the floats
 * (≈115 MB + 230 MB for one hour of audio). The ranged overload reads one
 * diarization window at a time, so long meetings never need the full hour
 * in memory (see [DiarizationWindows]).
 */
object WavSamples {

    const val SAMPLE_RATE = 16_000
    const val HEADER_BYTES = 44
    private const val BUFFER_BYTES = 64 * 1024

    /** Number of whole 16-bit samples after the header (a trailing odd byte
     * is ignored). Based on the file length rather than the header, so a WAV
     * whose header was never patched (crash mid-recording) still reads. */
    fun sampleCount(file: File): Long =
        ((file.length() - HEADER_BYTES).coerceAtLeast(0L)) / 2

    /** Whole file as float samples in [-1, 1). */
    fun readMono16k(file: File): FloatArray = readMono16k(file, 0L, Int.MAX_VALUE)

    /** Up to [maxSamples] samples starting at sample [fromSample]. */
    fun readMono16k(file: File, fromSample: Long, maxSamples: Int): FloatArray {
        val available = sampleCount(file) - fromSample.coerceAtLeast(0L)
        val count = minOf(available, maxSamples.toLong()).toInt()
        if (count <= 0) return FloatArray(0)
        val out = FloatArray(count)
        val buffer = ByteArray(BUFFER_BYTES)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(HEADER_BYTES + fromSample * 2)
            var written = 0
            while (written < count) {
                val samples = minOf(count - written, BUFFER_BYTES / 2)
                raf.readFully(buffer, 0, samples * 2)
                for (i in 0 until samples) {
                    val lo = buffer[i * 2].toInt() and 0xFF
                    val hi = buffer[i * 2 + 1].toInt()
                    out[written + i] = ((hi shl 8) or lo) / 32768f
                }
                written += samples
            }
        }
        return out
    }
}
