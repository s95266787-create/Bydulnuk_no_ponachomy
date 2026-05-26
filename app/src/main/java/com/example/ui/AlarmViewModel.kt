package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.VoiceRecorder
import com.example.data.Alarm
import com.example.data.AlarmRepository
import com.example.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class AlarmViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AlarmRepository
    val alarms: StateFlow<List<Alarm>>

    // Voice recorder state
    private val voiceRecorder = VoiceRecorder(application)
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordedFilePath = MutableStateFlow<String?>(null)
    val recordedFilePath: StateFlow<String?> = _recordedFilePath.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = AlarmRepository(application, database.alarmDao())
        alarms = repository.allAlarms
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    fun addAlarm(
        hour: Int,
        minute: Int,
        soundType: Int,
        builtInSoundName: String,
        customPath: String?,
        repeatDays: Set<Int>,
        label: String
    ) {
        viewModelScope.launch {
            val repeatString = repeatDays.sorted().joinToString(",")
            val alarm = Alarm(
                hour = hour,
                minute = minute,
                isEnabled = true,
                selectedSoundType = soundType,
                selectedBuiltInSound = builtInSoundName,
                customSoundPath = customPath,
                repeatDays = repeatString,
                label = label
            )
            repository.insertAlarm(alarm)
        }
    }

    fun toggleAlarm(alarm: Alarm) {
        viewModelScope.launch {
            repository.toggleAlarm(alarm)
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            repository.deleteAlarm(alarm)
        }
    }

    // Voice recorder integration
    fun startRecording() {
        if (_isRecording.value) return

        val context = getApplication<Application>()
        val recordsDir = File(context.filesDir, "voice_alarms").apply {
            if (!exists()) mkdirs()
        }
        val filename = "voice_${System.currentTimeMillis()}.mp4"
        val file = File(recordsDir, filename)

        if (voiceRecorder.startRecording(file)) {
            _isRecording.value = true
            _recordedFilePath.value = file.absolutePath
        }
    }

    fun stopRecording() {
        if (!_isRecording.value) return
        val result = voiceRecorder.stopRecording()
        _isRecording.value = false
        if (result == null) {
            _recordedFilePath.value = null
        }
    }

    fun clearRecording() {
        _recordedFilePath.value = null
    }

    // Picked sound copy helper to make URIs resilient and forever persistent
    fun savePickedUriToInternalFiles(uri: Uri, fileName: String = "picked_sound_${UUID.randomUUID()}.mp3"): String? {
        val context = getApplication<Application>()
        try {
            val contentResolver = context.contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val destDir = File(context.filesDir, "custom_sounds").apply {
                    if (!exists()) mkdirs()
                }
                val destFile = File(destDir, fileName)
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                Log.d("AlarmViewModel", "Successfully persisted external audio to internal path: ${destFile.absolutePath}")
                return destFile.absolutePath
            }
        } catch (e: Exception) {
            Log.e("AlarmViewModel", "Failed to save picked external sound file", e)
        }
        return null
    }
}
