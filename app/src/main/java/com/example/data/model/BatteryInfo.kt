package com.example.data.model

data class BatteryInfo(
    val percentage: Int,
    val isCharging: Boolean,
    val chargingStatus: String,
    val voltage: Float, // Volts
    val temperature: Float, // Celsius
    val health: String,
    val cycleCount: Int, // -1 if unavailable
    val batteryUsedSinceCharge: Int,
    val lastChargeTimeMs: Long, // when the device was last plugged IN (charging started)
    val lastUnpluggedTimeMs: Long // when the device was last unplugged FROM the charger
)
