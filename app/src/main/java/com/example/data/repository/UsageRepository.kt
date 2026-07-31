package com.example.data.repository

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.Build
import com.example.data.database.BackgroundMediaLogEntity
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
    val context: Context,
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

        // Our own health-based estimate, computed purely from real charging sessions this
        // app has observed (same method apps like AccuBattery use): every time a charge
        // session ends with a higher battery % than it started, we add the gained
        // percentage to a running total; every 100 accumulated points = one full
        // equivalent cycle. This starts counting from whenever the app was installed,
        // so it will read low at first no matter what, but it's fully transparent and
        // predictable — unlike the OEM hardware value below.
        val cumulative = settingsManager.cumulativeChargePercent.first()
        val finalCycleCount = if (cumulative > 0f) (cumulative / 100f).toInt() else 0
        val cycleCountIsEstimate = true
        val cycleProgressPct = (cumulative % 100f).toInt()

        // Separately, try to read the device's own hardware cycle-count property
        // (BATTERY_PROPERTY_CYCLE_COUNT, raw value 6, API 34+). Kept entirely apart from
        // our own estimate above — on several OEM ROMs this either isn't implemented or
        // reports a number that doesn't reflect reality, so it's shown as its own
        // distinct metric (or "Desteklenmiyor") rather than ever being blended into or
        // substituted for our estimate.
        val hardwareCycleCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val cycles = batteryManager.getIntProperty(6)
                if (cycles >= 0) cycles else -1
            } catch (e: Exception) {
                -1
            }
        } else {
            -1
        }

        // Raw "how many times plugged in" count — a different, simpler metric again,
        // not folded into either of the two above.
        val plugInCount = settingsManager.plugInCount.first()

        val lastUnpluggedBatteryLevel = settingsManager.lastUnpluggedBattery.first()
        val batteryUsedSinceCharge = if (lastUnpluggedBatteryLevel > percentage) {
            lastUnpluggedBatteryLevel - percentage
        } else {
            0
        }

        val lastChargeTime = settingsManager.lastChargeTime.first()
        val lastUnpluggedTime = settingsManager.lastUnpluggedTime.first()

        return BatteryInfo(
            percentage = percentage,
            isCharging = isCharging,
            chargingStatus = statusStr,
            voltage = voltage,
            temperature = temperature,
            health = healthStr,
            cycleCount = finalCycleCount,
            cycleCountIsEstimate = cycleCountIsEstimate,
            cycleProgressPct = cycleProgressPct,
            hardwareCycleCount = hardwareCycleCount,
            plugInCount = plugInCount,
            batteryUsedSinceCharge = batteryUsedSinceCharge,
            lastChargeTimeMs = lastChargeTime,
            lastUnpluggedTimeMs = lastUnpluggedTime
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

    /**
     * Real screen ON/OFF durations for a time range, derived from Android's own
     * SCREEN_INTERACTIVE / SCREEN_NON_INTERACTIVE usage events (system-maintained,
     * works even if the app wasn't running). This replaces summing per-app
     * foreground time, which can overcount (apps' reported foreground windows can
     * overlap in Android's usage stats) — that overcounting is exactly why
     * "Ekran Kapalı Süresi" could get stuck near 0 even after a screen-off day:
     * the inflated "on" sum was hitting the elapsed-time cap every time.
     */
    fun getScreenOnOffFromEvents(startTime: Long, endTime: Long): Pair<Long, Long> {
        if (!hasUsageStatsPermission() || endTime <= startTime) {
            return 0L to 0L
        }
        // Defensive cap: never query more than 7 days of history, no matter what a caller
        // passes in. Protects against any uninitialized/epoch (0L / 1970) timestamp slipping
        // through and forcing UsageStatsManager to enumerate the device's entire event
        // history, which can hang for a very long time on an older, heavily-used phone.
        val cappedStartTime = maxOf(startTime, endTime - 7L * 24 * 3600 * 1000L)
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usageStatsManager.queryEvents(cappedStartTime, endTime)
        val event = android.app.usage.UsageEvents.Event()

        var onMs = 0L
        var offMs = 0L
        var cursor = cappedStartTime
        var screenOnNow: Boolean? = null // unknown until the first event tells us

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val ts = event.timeStamp.coerceIn(startTime, endTime)
            when (event.eventType) {
                15 -> { // UsageEvents.Event.SCREEN_INTERACTIVE
                    if (screenOnNow == null) {
                        // Screen was OFF from range-start until this ON event.
                        offMs += (ts - cursor).coerceAtLeast(0L)
                    } else if (screenOnNow == false) {
                        offMs += (ts - cursor).coerceAtLeast(0L)
                    }
                    cursor = ts
                    screenOnNow = true
                }
                16 -> { // UsageEvents.Event.SCREEN_NON_INTERACTIVE
                    if (screenOnNow == null) {
                        // Screen was ON from range-start until this OFF event.
                        onMs += (ts - cursor).coerceAtLeast(0L)
                    } else if (screenOnNow == true) {
                        onMs += (ts - cursor).coerceAtLeast(0L)
                    }
                    cursor = ts
                    screenOnNow = false
                }
            }
        }

        // Tail: whatever the last known state was, it continued until endTime.
        when (screenOnNow) {
            true -> onMs += (endTime - cursor).coerceAtLeast(0L)
            false -> offMs += (endTime - cursor).coerceAtLeast(0L)
            null -> {
                // No screen events at all in range (e.g. very short range) —
                // fall back to treating it as ON, since that's the far more common case.
                onMs += (endTime - cursor).coerceAtLeast(0L)
            }
        }

        return onMs to offMs
    }

    /**
     * Same charging-transition detection as BatteryStateWorker, but callable directly
     * from the app (ViewModel) so that while the app is open, transitions are caught
     * within seconds instead of waiting for the next 15-minute background work run.
     * The worker remains the safety net for when the app isn't open at all.
     */
    suspend fun checkAndUpdateChargeTransition() {
        val info = getBatteryInfo()
        val wasCharging = settingsManager.wasCharging.first()
        val isChargingNow = info.isCharging

        if (wasCharging && !isChargingNow) {
            settingsManager.saveUnpluggedState(System.currentTimeMillis(), info.percentage)
            settingsManager.onChargingSessionEnd(info.percentage)
        } else if (!wasCharging && isChargingNow) {
            settingsManager.saveLastChargeTime(System.currentTimeMillis())
            settingsManager.onChargingSessionStart(info.percentage)
        }
        settingsManager.setWasCharging(isChargingNow)
    }

    suspend fun getAppUsageList(startTime: Long, endTime: Long = System.currentTimeMillis()): List<AppUsageItem> {
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

        // Real background playback time (e.g. YouTube/Spotify with the screen off),
        // detected via MediaSession — requires Notification Access; empty map if not granted.
        val mediaTotals = try {
            usageDao.getBackgroundMediaTotals(startTime, endTime).associate { it.packageName to it.totalMs }
        } catch (e: Exception) {
            emptyMap()
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
                    backgroundTimeMs = mediaTotals[packageName] ?: 0L,
                    estimatedBatteryUsagePct = pctOfTotal, // Estimated based on proportion of screen activity
                    percentageOfTotal = pctOfTotal,
                    icon = icon
                )
            )
        }

        // Apps that only ever played media in the background (screen off the whole time,
        // so zero foreground time) would otherwise never appear in this list at all.
        val alreadyListedPackages = list.map { it.packageName }.toSet()
        for ((packageName, mediaMs) in mediaTotals) {
            if (packageName in alreadyListedPackages || mediaMs <= 0L) continue
            var appLabel = packageName
            var icon: Drawable? = null
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                appLabel = pm.getApplicationLabel(appInfo).toString()
                icon = pm.getApplicationIcon(appInfo)
            } catch (e: Exception) {
            }
            list.add(
                AppUsageItem(
                    packageName = packageName,
                    appName = appLabel,
                    screenTimeSinceChargeMs = 0L,
                    todayUsageMs = 0L,
                    dailyAverageMs = 0L,
                    foregroundTimeMs = 0L,
                    backgroundTimeMs = mediaMs,
                    estimatedBatteryUsagePct = 0.0,
                    percentageOfTotal = 0.0,
                    icon = icon
                )
            )
        }

        // Sort by total real usage (foreground + background) so apps that mostly ran in
        // the background — like a music app — still rank sensibly, not just by screen time.
        return list.sortedByDescending { it.foregroundTimeMs + it.backgroundTimeMs }
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

    // --- Background media playback tracking (requires Notification Access) ---

    suspend fun openBackgroundMediaSession(packageName: String, startTime: Long): Long {
        return usageDao.insertBackgroundMediaLog(
            BackgroundMediaLogEntity(packageName = packageName, startTime = startTime, endTime = null)
        )
    }

    suspend fun closeBackgroundMediaSession(id: Long, endTime: Long) {
        usageDao.closeBackgroundMediaLog(id, endTime)
    }

    suspend fun closeDanglingBackgroundMediaSessions(exceptIds: List<Long> = emptyList()) {
        usageDao.closeDanglingBackgroundMediaLogs(System.currentTimeMillis(), exceptIds)
    }

    suspend fun deleteOldBackgroundMediaLogs() {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 3600 * 1000L
        usageDao.deleteOldBackgroundMediaLogs(thirtyDaysAgo)
    }

    suspend fun getBackgroundMediaTotals(start: Long, end: Long) = usageDao.getBackgroundMediaTotals(start, end)

    fun hasNotificationAccess(context: Context): Boolean {
        val enabledListeners = android.provider.Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(context.packageName)
    }
}
