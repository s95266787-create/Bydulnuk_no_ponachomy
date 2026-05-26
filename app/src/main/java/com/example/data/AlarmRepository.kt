package com.example.data

import android.content.Context
import com.example.receiver.AlarmScheduler
import kotlinx.coroutines.flow.Flow

class AlarmRepository(
    private val context: Context,
    private val alarmDao: AlarmDao
) {
    val allAlarms: Flow<List<Alarm>> = alarmDao.getAllAlarms()

    suspend fun getAlarmById(id: Int): Alarm? {
        return alarmDao.getAlarmById(id)
    }

    suspend fun insertAlarm(alarm: Alarm): Long {
        val id = alarmDao.insertAlarm(alarm)
        val savedAlarm = alarm.copy(id = id.toInt())
        if (savedAlarm.isEnabled) {
            AlarmScheduler.schedule(context, savedAlarm)
        } else {
            AlarmScheduler.cancel(context, savedAlarm)
        }
        return id
    }

    suspend fun updateAlarm(alarm: Alarm) {
        alarmDao.updateAlarm(alarm)
        if (alarm.isEnabled) {
            AlarmScheduler.schedule(context, alarm)
        } else {
            AlarmScheduler.cancel(context, alarm)
        }
    }

    suspend fun deleteAlarm(alarm: Alarm) {
        AlarmScheduler.cancel(context, alarm)
        alarmDao.deleteAlarm(alarm)
    }

    suspend fun toggleAlarm(alarm: Alarm) {
        val updated = alarm.copy(isEnabled = !alarm.isEnabled)
        updateAlarm(updated)
    }
}
