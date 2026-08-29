package com.example.strumcoach

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object  SpectralFluxExtractor {

    private const val TAG = "SpectralFluxExtractor"
    private const val WINDOW_MS = 25
    private const val HOP_MS = 10

    fun extractFlux(audioFile: File): List<Float> {
        if (!audioFile.exists()) return emptyList()

        val extractor = MediaExtractor()
        try {
            // File audio registrato, sampling
            extractor.setDataSource(audioFile.absolutePath)
            val trackIndex = selectAudioTrack(extractor)
            if (trackIndex < 0) return emptyList()

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return emptyList()
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var isEOS = false
            val samples = ArrayList<Float>()

            while (!isEOS) {
                val inIndex = codec.dequeueInputBuffer(10000)
                if (inIndex >= 0) {
                    val buffer = codec.getInputBuffer(inIndex)!!
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEOS = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, 10000)
                if (outIndex >= 0) {
                    val buffer = codec.getOutputBuffer(outIndex)!!
                    while (buffer.remaining() >= 2) {
                        samples.add(buffer.short / 32768f)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

            return computeFlux(samples, sampleRate)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract spectral flux", e)
            return emptyList()
        }
    }

    private fun computeFlux(samples: List<Float>, sampleRate: Int): List<Float> {
        val winLen = sampleRate * WINDOW_MS / 1000
        val hop = sampleRate * HOP_MS / 1000
        if (winLen < 2 || samples.size < winLen) return emptyList()

        val fftSize = nextPowerOfTwo(winLen)
        val window = FloatArray(winLen) { i ->
            (0.5 - 0.5 * cos(2.0 * PI * i / (winLen - 1))).toFloat()
        }

        val flux = mutableListOf<Float>()
        var prevMagnitudes: FloatArray? = null
        var start = 0

        while (start + winLen <= samples.size) {
            val re = FloatArray(fftSize)
            val im = FloatArray(fftSize)
            for (i in 0 until winLen) {
                re[i] = samples[start + i] * window[i]
            }
            trasformata_fourier(re, im)

            val magnitudes = FloatArray(fftSize / 2) { i -> sqrt(re[i] * re[i] + im[i] * im[i]) }

            val prev = prevMagnitudes
            if (prev != null) {
                var sum = 0f
                for (i in magnitudes.indices) {
                    val diff = magnitudes[i] - prev[i]
                    if (diff > 0f) sum += diff
                }
                flux.add(sum)
            } else {
                flux.add(0f)
            }
            prevMagnitudes = magnitudes
            start += hop
        }

        val maxFlux = flux.maxOrNull() ?: 1f
        return if (maxFlux > 0f) flux.map { (it / maxFlux).coerceIn(0f, 1f) } else flux
    }

    private fun nextPowerOfTwo(n: Int): Int {
        var p = 1
        while (p < n) p = p shl 1
        return p
    }

    // Algoritmo di Cooley-Tukey per il calcolo della trasformata di fourier
    private fun trasformata_fourier(re: FloatArray, im: FloatArray) {
        val n = re.size

        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var tmp = re[i]; re[i] = re[j]; re[j] = tmp
                tmp = im[i]; im[i] = im[j]; im[j] = tmp
            }
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wR = cos(angle).toFloat()
            val wI = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var curR = 1f
                var curI = 0f
                for (k in 0 until len / 2) {
                    val uR = re[i + k]
                    val uI = im[i + k]
                    val vR = re[i + k + len / 2] * curR - im[i + k + len / 2] * curI
                    val vI = re[i + k + len / 2] * curI + im[i + k + len / 2] * curR

                    re[i + k] = uR + vR
                    im[i + k] = uI + vI
                    re[i + k + len / 2] = uR - vR
                    im[i + k + len / 2] = uI - vI

                    val nextR = curR * wR - curI * wI
                    val nextI = curR * wI + curI * wR
                    curR = nextR
                    curI = nextI
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) return i
        }
        return -1
    }
}
