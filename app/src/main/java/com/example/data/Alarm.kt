package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true,
    val selectedSoundType: Int = 0, // 0 = Built-in, 1 = Recorded Voice, 2 = System Alarm/Ringtone
    val selectedBuiltInSound: String = "bell", // "bell", "digital", "rooster", "melodic"
    val customSoundPath: String? = null, // path to recorded voice file or picked uri string
    val repeatDays: String = "", // Comma-separated: "2,3,4" (Monday=2...Sunday=1 as in Calendar)
    val label: String = "" // Optional memo/label
) {
    // Check if alarm repeats on a particular day (Calendar constants Monday=2..Sunday=1)
    fun repeatsOn(day: Int): Boolean {
        if (repeatDays.isEmpty()) return false
        val days = repeatDays.split(",").mapNotNull { it.toIntOrNull() }.toSet()
        return days.contains(day)
    }

    fun getRepeatDaysSet(): Set<Int> {
        if (repeatDays.isEmpty()) return emptySet()
        return repeatDays.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    }
}
