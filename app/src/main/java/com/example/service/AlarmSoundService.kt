package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.MainActivity.*
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

object AlarmStateHolder {
    val isRinging = kotlinx.coroutines.flow.MutableStateFlow(false)
    val activeAlarmId = kotlinx.coroutines.flow.MutableStateFlow(-1)
}

class AlarmSoundService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    companion object {
        const val CHANNEL_ID = "alarm_sound_channel"
        const val NOTIFICATION_ID = 9999
        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val EXTRA_ALARM_ID = "extra_alarm_id"

        fun start(context: Context, alarmId: Int) {
            val intent = Intent(context, AlarmSoundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ALARM_ID, alarmId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AlarmSoundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopPlaybackAndService()
            return START_NOT_STICKY
        }

        val alarmId = intent?.getIntExtra(EXTRA_ALARM_ID, -1) ?: -1
        showForegroundNotification(alarmId)

        AlarmStateHolder.isRinging.value = true
        AlarmStateHolder.activeAlarmId.value = alarmId

        if (alarmId != -1) {
            serviceScope.launch {
                val db = AppDatabase.getDatabase(applicationContext)
                val alarm = db.alarmDao().getAlarmById(alarmId)
                if (alarm != null) {
                    playAlarmSound(alarm.selectedSoundType, alarm.selectedBuiltInSound, alarm.customSoundPath)
                } else {
                    playDefaultSound()
                }
            }
        } else {
            playDefaultSound()
        }

        return START_STICKY
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoiceAlarm::WakeLock").apply {
            acquire(10 * 60 * 1000L) // 10 minutes max lock
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Звук будильника",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Канал для відтворення звуку будильника"
                setSound(null, null) // Play sound programmatically via service
                enableVibration(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun showForegroundNotification(alarmId: Int) {
        val stopIntent = Intent(this, AlarmSoundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("LAUNCH_ALARM_ACTIVE", true)
            putExtra("ALARM_ID", alarmId)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            102,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Будильник спрацював!")
            .setContentText("Натисніть для вимкнення або відкриття")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Вимкнути",
                stopPendingIntent
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun playAlarmSound(soundType: Int, builtInSound: String, customPath: String?) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer()

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            mediaPlayer?.setAudioAttributes(attributes)
            mediaPlayer?.isLooping = true

            when (soundType) {
                1 -> { // Custom Recorded Voice
                    if (customPath != null) {
                        val file = File(customPath)
                        if (file.exists()) {
                            mediaPlayer?.setDataSource(customPath)
                            mediaPlayer?.prepare()
                            mediaPlayer?.start()
                            Log.d("AlarmSoundService", "Playing recorded voice from: $customPath")
                        } else {
                            Log.e("AlarmSoundService", "Recorded file not found, playing default.")
                            playDefaultSound()
                        }
                    } else {
                        playDefaultSound()
                    }
                }
                2 -> { // System Ringtone/Picked File
                    if (customPath != null) {
                        try {
                            mediaPlayer?.setDataSource(this, Uri.parse(customPath))
                            mediaPlayer?.prepare()
                            mediaPlayer?.start()
                            Log.d("AlarmSoundService", "Playing picked system/external sound: $customPath")
                        } catch (e: Exception) {
                            Log.e("AlarmSoundService", "Failed to play picked URI, playing default.", e)
                            playDefaultSound()
                        }
                    } else {
                        playDefaultSound()
                    }
                }
                else -> { // Built-in (0)
                    val soundUri = getBuiltInSoundUri(builtInSound)
                    if (soundUri != null) {
                        mediaPlayer?.setDataSource(this, soundUri)
                        mediaPlayer?.prepare()
                        mediaPlayer?.start()
                    } else {
                        playDefaultSound()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmSoundService", "Error in playAlarmSound", e)
            playDefaultSound()
        }
    }

    private fun getBuiltInSoundUri(builtInSound: String): Uri? {
        return when (builtInSound) {
            "bell" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            "melodic" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            "digital" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }
    }

    private fun playDefaultSound() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer()

            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mediaPlayer?.setDataSource(this, alarmUri)
            mediaPlayer?.isLooping = true
            mediaPlayer?.prepare()
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e("AlarmSoundService", "Failed to play default system sound", e)
        }
    }

    private fun stopPlaybackAndService() {
        AlarmStateHolder.isRinging.value = false
        AlarmStateHolder.activeAlarmId.value = -1
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("AlarmSoundService", "Error while stopping player", e)
        }
        releaseWakeLock()
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlaybackAndService()
    }
}
