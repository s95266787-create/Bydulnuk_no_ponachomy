package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Log.d(TAG, "Device boot completed, rescheduling all enabled alarms")
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context.applicationContext)
                    val alarms = db.alarmDao().getAllAlarms().first()
                    
                    for (alarm in alarms) {
                        if (alarm.isEnabled) {
                            AlarmScheduler.schedule(context.applicationContext, alarm)
                            Log.d(TAG, "Rescheduled alarm ${alarm.id} at ${alarm.hour}:${alarm.minute}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reschedule alarms on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
