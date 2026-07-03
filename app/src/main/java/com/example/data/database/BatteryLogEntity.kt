package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_logs")
data class BatteryLogEntity(
    @PrimaryKey val timestamp: Long, // Epoch ms
    val batteryLevel: Int, // 0-100
    val isCharging: Boolean
)
