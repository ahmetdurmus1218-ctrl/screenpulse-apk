package com.example.data.model

data class BatteryInfo(
    val percentage: Int,
    val isCharging: Boolean,
    val chargingStatus: String,
    val voltage: Float, // Volts
    val temperature: Float, // Celsius
    val health: String,
    val cycleCount: Int, // Our health-based estimate: -1 if truly unknown (no charge history yet)
    val cycleCountIsEstimate: Boolean, // always true — see UsageRepository for why
    val cycleProgressPct: Int, // 0-99: how far into the *next* estimated cycle we are, so the count doesn't look "stuck"
    val hardwareCycleCount: Int, // Real value read from the device's own battery hardware, if it exposes one. -1 if unsupported/unavailable on this device — kept fully separate from cycleCount above so an unreliable OEM value never gets mixed into our own estimate.
    val plugInCount: Int, // Raw count of "times plugged in to charge" — a different, simpler metric than the health-based cycle estimate; not folded into it.
    val batteryUsedSinceCharge: Int,
    val lastChargeTimeMs: Long, // when the device was last plugged IN (charging started)
    val lastUnpluggedTimeMs: Long // when the device was last unplugged FROM the charger
)
