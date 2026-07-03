package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_history")
data class UsageHistoryEntity(
    @PrimaryKey val date: String, // Format: YYYY-MM-DD
    val screenOnTimeMs: Long,
    val screenOffTimeMs: Long,
    val batteryUsedPct: Int,
    val totalTimeSinceChargeMs: Long
)
