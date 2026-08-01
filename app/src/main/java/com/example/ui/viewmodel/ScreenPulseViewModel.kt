package com.example.ui.viewmodel

import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.BatteryLogEntity
import com.example.data.database.UsageHistoryEntity
import com.example.data.datastore.SettingsManager
import com.example.data.model.AppUsageItem
import com.example.data.model.BatteryInfo
import com.example.data.repository.UsageRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

sealed interface MainUiState {
    object Loading : MainUiState
    data class Success(
        val batteryInfo: BatteryInfo,
        val screenOnTimeMs: Long,
        val screenOffTimeMs: Long,
        val timeSinceLastChargeMs: Long,
        val appUsageList: List<AppUsageItem>,
        val usageHistory: List<UsageHistoryEntity>,
        val batteryLogs: List<BatteryLogEntity>,
        val hourlyBuckets: List<Long> = List(6) { 0L }, // real screen-on ms for each 4h block of today (00-04, 04-08, ... 20-24)
        val unlockCount: Int = 0,
        val hasPermission: Boolean
    ) : MainUiState
}

class ScreenPulseViewModel(
    private val repository: UsageRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortBy = MutableStateFlow(SortOption.USAGE_TIME)
    val sortBy: StateFlow<SortOption> = _sortBy.asStateFlow()

    val isDarkTheme: StateFlow<Boolean> = settingsManager.isDarkTheme
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, true)

    val lockScreenNotificationMode: StateFlow<String> = settingsManager.lockScreenNotificationMode
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, "off")

    /** Persists the choice and immediately starts/stops the underlying service/worker. */
    fun setLockScreenNotificationMode(context: android.content.Context, mode: String) {
        viewModelScope.launch {
            settingsManager.setLockScreenNotificationMode(mode)
            com.example.notification.LockScreenNotificationController.applyMode(context, mode)
        }
    }

    /** Top apps used within an arbitrary time window — e.g. a range the person drag-selects
     *  on the battery drain chart, to see what was running while the battery dropped. */
    suspend fun getAppUsageForRange(startTime: Long, endTime: Long): List<com.example.data.model.AppUsageItem> {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            repository.getAppUsageList(startTime, endTime)
        }
    }

    fun getRawUsageEventsDebug(startTime: Long, endTime: Long): String =
        repository.getRawUsageEventsDebug(startTime, endTime)

    fun hasNotificationAccess(context: android.content.Context): Boolean =
        repository.hasNotificationAccess(context)

    fun toggleDarkTheme(context: android.content.Context) {
        viewModelScope.launch {
            settingsManager.setDarkTheme(!isDarkTheme.value)
            com.example.widget.ScreenPulseWidgetProvider.updateAllWidgets(context)
        }
    }

    enum class SortOption {
        USAGE_TIME, APP_NAME, PERCENTAGE
    }

    init {
        viewModelScope.launch {
            val currentBattery = repository.getBatteryInfo().percentage
            settingsManager.initializeIfNeeded(currentBattery)
            refreshStats()
            startAutoRefresh()
        }
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000L) // refresh every 30s so on-screen timers actually progress
                refreshStats()
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortBy(option: SortOption) {
        _sortBy.value = option
    }

    fun checkPermissionAndRefresh() {
        refreshStats()
    }

    fun refreshStats() {
        viewModelScope.launch {
            val hasPermission = repository.hasUsageStatsPermission()
            if (!hasPermission) {
                val batteryInfo = repository.getBatteryInfo()
                _uiState.value = MainUiState.Success(
                    batteryInfo = batteryInfo,
                    screenOnTimeMs = 0L,
                    screenOffTimeMs = 0L,
                    timeSinceLastChargeMs = 0L,
                    appUsageList = emptyList(),
                    usageHistory = emptyList(),
                    batteryLogs = emptyList(),
                    hasPermission = false
                )
                return@launch
            }

            try {
                // Populate history from past days if first run and empty
                populateHistoryIfEmpty()

                // Catch up on any charge/unplug transition immediately (don't wait for the
                // 15-minute background worker) — this is what makes "Pil Tüketimi" feel
                // live whenever the app is open.
                repository.checkAndUpdateChargeTransition()

                // Save current battery state to log
                repository.logCurrentBatteryState()

                val batteryInfo = repository.getBatteryInfo()
                val lastUnpluggedTime = settingsManager.lastUnpluggedTime.first()
                val now = System.currentTimeMillis()

                val timeSinceCharge = now - lastUnpluggedTime
                val cleanTimeSinceCharge = if (timeSinceCharge > 0) timeSinceCharge else 4 * 3600 * 1000L

                // Get app usages since unplugged (for the per-app breakdown / Apps screen)
                val rawAppUsages = repository.getAppUsageList(lastUnpluggedTime, now)

                // Real screen on/off split, from Android's own screen-interactive events —
                // NOT a sum of per-app foreground time, which can overcount (apps' reported
                // foreground windows can overlap) and used to make "Ekran Kapalı Süresi"
                // look stuck near zero even after a screen-off day.
                val (realScreenOn, realScreenOff) = repository.getScreenOnOffFromEvents(lastUnpluggedTime, now)
                val cleanScreenOn = if (realScreenOn > cleanTimeSinceCharge) cleanTimeSinceCharge else realScreenOn
                val cleanScreenOff = (cleanTimeSinceCharge - cleanScreenOn).coerceAtLeast(0L)

                // Filter app usages based on search and sort
                val appUsages = rawAppUsages

                // Query DB logs
                val history = repository.getAllUsageHistory().first()
                val batteryLogs = repository.getBatteryLogs(now - 24 * 3600 * 1000L).first()

                // Calendar-day boundary (midnight), independent of charge cycles.
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                // Save today's log to usage history database.
                // BUG FIX: this used to be built from cleanScreenOn/cleanScreenOff, which are
                // measured from lastUnpluggedTime (i.e. since the last charge), NOT since
                // midnight. That made the "Gün" bar chart collapse to a tiny value every time
                // the phone was plugged in and unplugged during the day, even though the day's
                // real screen time kept accumulating. The daily record must be keyed off the
                // calendar day, not the charge cycle, so it stays correct across any number of
                // charges within the same day.
                val (dayScreenOn, dayScreenOff) = repository.getScreenOnOffFromEvents(todayStart, now)
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val todayHistory = UsageHistoryEntity(
                    date = todayStr,
                    screenOnTimeMs = dayScreenOn,
                    screenOffTimeMs = dayScreenOff,
                    batteryUsedPct = batteryInfo.batteryUsedSinceCharge,
                    totalTimeSinceChargeMs = (now - todayStart).coerceAtLeast(0L)
                )
                repository.saveUsageHistory(todayHistory)

                // Real hourly buckets for today (00-04, 04-08, ... 20-24) — actual
                // per-window event-based queries (same accurate source as the main screen-on
                // figure). BUG FIX: this used to call getScreenOnTimeForRange(), which sums
                // per-app totalTimeInForeground via queryAndAggregateUsageStats — a method
                // that's unreliable for narrow sub-day windows and was returning nearly the
                // same (whole-day-ish) total for every single 4-hour block.
                val hourlyBuckets = (0 until 6).map { blockIndex ->
                    val blockStart = todayStart + blockIndex * 4 * 3600 * 1000L
                    val blockEnd = (blockStart + 4 * 3600 * 1000L).coerceAtMost(now)
                    repository.getScreenOnOffFromEvents(blockStart, blockEnd).first
                }

                val unlockCount = repository.getUnlockCount(lastUnpluggedTime, now)

                _uiState.value = MainUiState.Success(
                    batteryInfo = batteryInfo,
                    screenOnTimeMs = cleanScreenOn,
                    screenOffTimeMs = cleanScreenOff,
                    timeSinceLastChargeMs = cleanTimeSinceCharge,
                    appUsageList = appUsages,
                    usageHistory = history.sortedBy { it.date },
                    batteryLogs = batteryLogs,
                    hourlyBuckets = hourlyBuckets,
                    unlockCount = unlockCount,
                    hasPermission = true
                )
                // Keep home-screen widgets in sync with the app itself, independent of
                // whether the optional lock-screen notification ("Sürekli Açık") is running —
                // widgets shouldn't only feel live when that separate feature happens to be on.
                com.example.widget.ScreenPulseWidgetProvider.updateAllWidgets(repository.context)
            } catch (e: Exception) {
                // Keep showing loading or previous if error
            }
        }
    }

    private suspend fun populateHistoryIfEmpty() {
        val currentHistory = repository.getAllUsageHistory().first()
        if (currentHistory.isEmpty()) {
            val now = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            // Query for past 6 days to populate rich data
            for (i in 6 downTo 1) {
                val cal = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -i)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val dayStart = cal.timeInMillis
                val dayEnd = dayStart + 24 * 3600 * 1000L - 1000L
                val dateStr = dateFormat.format(cal.time)

                // Real screen on/off for this past day, from actual screen-interactive events
                // (not a per-app foreground sum, which can overcount and make "off" time
                // look like it never accumulates).
                val (daySot, daySoff) = repository.getScreenOnOffFromEvents(dayStart, dayEnd)

                if (daySot > 0) {
                    val history = UsageHistoryEntity(
                        date = dateStr,
                        screenOnTimeMs = daySot,
                        screenOffTimeMs = daySoff,
                        batteryUsedPct = -1, // unknown: no real battery log exists for days before install
                        totalTimeSinceChargeMs = 24 * 3600 * 1000L
                    )
                    repository.saveUsageHistory(history)
                }
            }
        }
    }

    class Factory(
        private val repository: UsageRepository,
        private val settingsManager: SettingsManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ScreenPulseViewModel::class.java)) {
                return ScreenPulseViewModel(repository, settingsManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
