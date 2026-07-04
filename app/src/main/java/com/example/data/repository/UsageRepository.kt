package com.example.data.repository

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.Build
import com.example.data.database.BatteryLogEntity
import com.example.data.database.UsageDao
import com.example.data.database.UsageHistoryEntity
import com.example.data.datastore.SettingsManager
import com.example.data.model.AppUsageItem
import com.example.data.model.BatteryInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.*

class UsageRepository(
    private val context: Context,
    private val usageDao: UsageDao,
    private val settingsManager: SettingsManager
) {

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    suspend fun getBatteryInfo(): BatteryInfo {
        val batteryStatusIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentage = if (level >= 0 && scale > 0) (level * 100f / scale.toFloat()).toInt() else 100

        val status = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val statusStr = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Şarj Oluyor"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Boşalıyor"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Şarj Olmuyor"
            BatteryManager.BATTERY_STATUS_FULL -> "Dolu"
            else -> "Boşalıyor"
        }

        val voltage = (batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f
        val temperature = (batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0f

        val health = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val healthStr = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "İyi"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Aşırı Isınmış"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Ölü"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Aşırı Voltaj"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Arızalı"
            BatteryManager.BATTERY_HEALTH_COLD -> "Soğuk"
            else -> "İyi" // Default to good if unknown but percentage is fine
        }

        val cycleCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.getIntProperty(4) // 4 is BATTERY_PROPERTY_CYCLE_COUNT
        } else {
            -1
        }

        val lastUnpluggedBatteryLevel = settingsManager.lastUnpluggedBattery.first()
        val batteryUsedSinceCharge = if (lastUnpluggedBatteryLevel > percentage) {
            lastUnpluggedBatteryLevel - percentage
        } else {
            0
        }

        val lastChargeTime = settingsManager.lastChargeTime.first()

        return BatteryInfo(
            percentage = percentage,
            isCharging = isCharging,
            chargingStatus = statusStr,
            voltage = voltage,
            temperature = temperature,
            health = healthStr,
            cycleCount = cycleCount,
            batteryUsedSinceCharge = batteryUsedSinceCharge,
            lastChargeTimeMs = lastChargeTime
        )
    }

    /**
     * Lightweight real screen-on-time sum for an arbitrary time window, using the same
     * Android UsageStatsManager data as everything else (no icon/label loading overhead).
     * Used to build genuinely accurate hourly buckets instead of splitting a daily total
     * by a fixed made-up ratio.
     */
    /**
     * Real unlock count since a given time, read from Android's own historical usage
     * events log (UsageEvents.Event type 18 = KEYGUARD_HIDDEN, i.e. the lock screen was
     * dismissed). This is system-maintained data — no broadcast receiver or service needed,
     * and no extra permission beyond the usage-access permission we already require.
     * Returns 0 on API < 28 or if usage access isn't granted (the event type doesn't exist
     * on older Android versions, so it will naturally just never match).
     */
    fun getUnlockCount(startTime: Long, endTime: Long): Int {
        if (!hasUsageStatsPermission()) return 0
        if (endTime <= startTime) return 0
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usageStatsManager.queryEvents(startTime, endTime)
        var count = 0
        val event = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == 18) { // UsageEvents.Event.KEYGUARD_HIDDEN
                count++
            }
        }
        return count
    }

    fun getScreenOnTimeForRange(startTime: Long, endTime: Long): Long {
        if (!hasUsageStatsPermission()) return 0L
        if (endTime <= startTime) return 0L
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        return stats.values.sumOf { it.totalTimeInForeground }
    }

    fun getAppUsageList(startTime: Long, endTime: Long = System.currentTimeMillis()): List<AppUsageItem> {
        if (!hasUsageStatsPermission()) return emptyList()

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)

        // Today's Usage
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = calendar.timeInMillis
        val todayStats = usageStatsManager.queryAndAggregateUsageStats(startOfToday, endTime)

        // Last 7 days usage for daily average
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 3600 * 1000L
        val weeklyStats = usageStatsManager.queryAndAggregateUsageStats(sevenDaysAgo, endTime)

        val pm = context.packageManager
        val list = mutableListOf<AppUsageItem>()
        var totalForegroundSinceChargeMs = 0L

        val filteredStats = stats.filter { it.value.totalTimeInForeground > 0 }
        for (stat in filteredStats.values) {
            totalForegroundSinceChargeMs += stat.totalTimeInForeground
        }

        for ((packageName, stat) in filteredStats) {
            val screenTimeMs = stat.totalTimeInForeground
            val todayUsageMs = todayStats[packageName]?.totalTimeInForeground ?: 0L
            val weeklyUsageMs = weeklyStats[packageName]?.totalTimeInForeground ?: 0L
            val dailyAverageMs = weeklyUsageMs / 7

            var appLabel = packageName
            var icon: Drawable? = null
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                appLabel = pm.getApplicationLabel(appInfo).toString()
                icon = pm.getApplicationIcon(appInfo)
            } catch (e: Exception) {
                // Ignore and use packageName as label
            }

            // Filter out system launchers and other packages that don't represent apps with launcher icons if needed.
            // But standard behavior is to show any app that recorded screen time.
            val pctOfTotal = if (totalForegroundSinceChargeMs > 0) {
                (screenTimeMs.toDouble() / totalForegroundSinceChargeMs) * 100
            } else {
                0.0
            }

            list.add(
                AppUsageItem(
                    packageName = packageName,
                    appName = appLabel,
                    screenTimeSinceChargeMs = screenTimeMs,
                    todayUsageMs = todayUsageMs,
                    dailyAverageMs = dailyAverageMs,
                    foregroundTimeMs = screenTimeMs,
                    backgroundTimeMs = 0L, // Standard Android does not expose package background time via UsageStats
                    estimatedBatteryUsagePct = pctOfTotal, // Estimated based on proportion of screen activity
                    percentageOfTotal = pctOfTotal,
                    icon = icon
                )
            )
        }

        return list.sortedByDescending { it.screenTimeSinceChargeMs }
    }

    // Database Actions
    fun getAllUsageHistory(): Flow<List<UsageHistoryEntity>> = usageDao.getAllUsageHistory()

    suspend fun saveUsageHistory(history: UsageHistoryEntity) = usageDao.insertUsageHistory(history)

    fun getBatteryLogs(since: Long): Flow<List<BatteryLogEntity>> = usageDao.getBatteryLogs(since)

    suspend fun logCurrentBatteryState() {
        val batteryInfo = getBatteryInfo()
        val log = BatteryLogEntity(
            timestamp = System.currentTimeMillis(),
            batteryLevel = batteryInfo.percentage,
            isCharging = batteryInfo.isCharging
        )
        usageDao.insertBatteryLog(log)
        // Clean up logs older than 7 days to keep db lightweight
        val oneWeekAgo = System.currentTimeMillis() - 7 * 24 * 3600 * 1000L
        usageDao.deleteOldBatteryLogs(oneWeekAgo)
    }

    suspend fun triggerUnplugEvent(batteryLevel: Int) {
        val now = System.currentTimeMillis()
        settingsManager.saveUnpluggedState(now, batteryLevel)
    }

    suspend fun triggerPlugEvent() {
        settingsManager.saveLastChargeTime(System.currentTimeMillis())
    }
}
