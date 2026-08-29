package com.example.strumcoach

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.sqrt

object AudioEnvelopeExtractor {

    private const val TAG = "AudioExtractor"
    private const val WINDOW_MS = 25

    fun extractEnvelope(audioFile: File): List<Float> {
        if (!audioFile.exists()) return emptyList()

        val extractor = MediaExtractor()
        val envelope = mutableListOf<Float>()

        try {
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
            
            // PCM processing variables
            val samplesPerWindow = (sampleRate * WINDOW_MS / 1000)
            var currentWindowSum = 0.0
            var currentWindowCount = 0

            while (!isEOS) {
                if (!isEOS) {
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
                }

                val outIndex = codec.dequeueOutputBuffer(info, 10000)
                if (outIndex >= 0) {
                    val buffer = codec.getOutputBuffer(outIndex)!!
                    
                    // Process PCM data (Shorts - 16 bit)
                    while (buffer.remaining() >= 2) {
                        val sample = buffer.short.toInt()
                        currentWindowSum += (sample * sample).toDouble()
                        currentWindowCount++

                        if (currentWindowCount >= samplesPerWindow) {
                            val rms = sqrt(currentWindowSum / currentWindowCount).toFloat()
                            envelope.add(rms)
                            currentWindowSum = 0.0
                            currentWindowCount = 0
                        }
                    }
                    
                    codec.releaseOutputBuffer(outIndex, false)
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract audio envelope", e)
        }

        // Normalize envelope to 0..1 range for easier fusion
        val maxVal = if (envelope.isNotEmpty()) envelope.maxOrNull() ?: 1f else 1f
        return if (maxVal > 0) envelope.map { (it / maxVal).coerceIn(0f, 1f) } else envelope
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
