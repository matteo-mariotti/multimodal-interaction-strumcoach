package com.example.strumcoach

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var playerPrepared = false
    private var currentOutputFile: File? = null

    fun startRecording(fileName: String, persistent: Boolean = false): File? {
        stopRecording()
        val dir = if (persistent) context.filesDir else context.cacheDir
        val file = File(dir, fileName)
        currentOutputFile = file
        Log.d("AudioManager", "Starting recording to: ${file.absolutePath}")

        try {
            recorder =
                MediaRecorder(context)
                    .apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            Log.d("AudioManager", "Recording started successfully")
        } catch (e: Exception) {
            Log.e("AudioManager", "Recording failed to start", e)
            recorder?.release()
            recorder = null
            return null
        }
        return file
    }

    fun stopRecording(): File? {
        Log.d("AudioManager", "Stopping recording...")
        try {
            recorder?.stop()
            Log.d("AudioManager", "Recording stopped. File size: ${currentOutputFile?.length() ?: 0} bytes")
        } catch (e: Exception) {
            Log.e("AudioManager", "Stop recording failed (might be too short)", e)
        } finally {
            recorder?.release()
            recorder = null
        }
        return currentOutputFile
    }

    fun playAudio(url: String, onComplete: () -> Unit = {}) {
        if (url.isEmpty()) {
            Log.e("AudioManager", "Cannot play audio: URL is empty")
            return
        }
        
        Log.d("AudioManager", "Playing audio from: $url")
        stopPlayback()
        playerPrepared = false

        player = MediaPlayer().apply {
            try {
                setDataSource(url)
                prepareAsync()
                setOnPreparedListener {
                    Log.d("AudioManager", "MediaPlayer prepared, starting...")
                    playerPrepared = true
                    start()
                }
                setOnCompletionListener {
                    Log.d("AudioManager", "Playback completed")
                    onComplete()
                    stopPlayback()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("AudioManager", "MediaPlayer error: what=$what, extra=$extra")
                    stopPlayback()
                    true
                }
            } catch (e: Exception) {
                Log.e("AudioManager", "Playback failed during setup", e)
            }
        }
    }

    fun stopPlayback() {
        playerPrepared = false
        try { player?.stop() } catch (e: Exception) { /* ignore if not started */ }
        player?.release()
        player = null
    }

    fun getCurrentPositionMs(): Int =
        if (playerPrepared) try { player?.currentPosition ?: 0 } catch (e: Exception) { 0 } else 0

    fun getDurationMs(): Int =
        if (playerPrepared) try { player?.duration?.takeIf { it > 0 } ?: 0 } catch (e: Exception) { 0 } else 0
}
