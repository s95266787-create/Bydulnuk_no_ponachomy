package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class VoiceRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false

    fun startRecording(outputFile: File): Boolean {
        if (isRecording) return false

        try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder = recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            Log.d("VoiceRecorder", "Recording started successfully: ${outputFile.absolutePath}")
            return true
        } catch (e: IOException) {
            Log.e("VoiceRecorder", "prepare() failed", e)
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
            return false
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "start() failed", e)
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
            return false
        }
    }

    fun stopRecording(): String? {
        if (!isRecording) return null

        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            Log.d("VoiceRecorder", "Recording stopped.")
            "success"
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Failed to stop recording", e)
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            null
        }
    }

    fun isRecording() = isRecording
}
