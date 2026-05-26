package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import com.example.service.AlarmSoundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.ACTION_TRIGGER_ALARM") {
            val alarmId = intent.getIntExtra("ALARM_ID", -1)
            Log.d(TAG, "Alarm triggered! ID: $alarmId")

            if (alarmId == -1) return

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context.applicationContext)
                    val alarmDao = db.alarmDao()
                    val alarm = alarmDao.getAlarmById(alarmId)

                    if (alarm != null && alarm.isEnabled) {
                        // Start the alarm sounding service
                        AlarmSoundService.start(context.applicationContext, alarmId)

                        // If the alarm has repeating days, schedule its next target day
                        val repeatDays = alarm.getRepeatDaysSet()
                        if (repeatDays.isNotEmpty()) {
                            AlarmScheduler.schedule(context.applicationContext, alarm)
                        } else {
                            // Non-repeating alarm, disable it now that it triggered
                            val updatedAlarm = alarm.copy(isEnabled = false)
                            alarmDao.updateAlarm(updatedAlarm)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling alarm broadcast", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
