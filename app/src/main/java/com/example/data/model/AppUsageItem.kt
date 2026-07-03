package com.example.data.model

import android.graphics.drawable.Drawable

data class AppUsageItem(
    val packageName: String,
    val appName: String,
    val screenTimeSinceChargeMs: Long,
    val todayUsageMs: Long,
    val dailyAverageMs: Long,
    val foregroundTimeMs: Long,
    val backgroundTimeMs: Long,
    val estimatedBatteryUsagePct: Double, // Calculated proportional battery usage estimate
    val percentageOfTotal: Double,
    val icon: Drawable? = null
)
