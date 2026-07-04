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

    fun toggleDarkTheme() {
        viewModelScope.launch {
            settingsManager.setDarkTheme(!isDarkTheme.value)
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

                // Save current battery state to log
                repository.logCurrentBatteryState()

                val batteryInfo = repository.getBatteryInfo()
                val lastUnpluggedTime = settingsManager.lastUnpluggedTime.first()
                val now = System.currentTimeMillis()

                val timeSinceCharge = now - lastUnpluggedTime
                val cleanTimeSinceCharge = if (timeSinceCharge > 0) timeSinceCharge else 4 * 3600 * 1000L

                // Get app usages since unplugged
                val rawAppUsages = repository.getAppUsageList(lastUnpluggedTime, now)
                
                // Calculate total screen-on time
                val screenOnTime = rawAppUsages.sumOf { it.screenTimeSinceChargeMs }
                
                // Cap screen-on time to cleanTimeSinceCharge
                val cleanScreenOn = if (screenOnTime > cleanTimeSinceCharge) cleanTimeSinceCharge else screenOnTime
                val cleanScreenOff = cleanTimeSinceCharge - cleanScreenOn

                // Filter app usages based on search and sort
                val appUsages = rawAppUsages

                // Query DB logs
                val history = repository.getAllUsageHistory().first()
                val batteryLogs = repository.getBatteryLogs(now - 24 * 3600 * 1000L).first()

                // Save today's log to usage history database
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val todayHistory = UsageHistoryEntity(
                    date = todayStr,
                    screenOnTimeMs = cleanScreenOn,
                    screenOffTimeMs = cleanScreenOff,
                    batteryUsedPct = batteryInfo.batteryUsedSinceCharge,
                    totalTimeSinceChargeMs = cleanTimeSinceCharge
                )
                repository.saveUsageHistory(todayHistory)

                // Real hourly buckets for today (00-04, 04-08, ... 20-24) — actual
                // per-window UsageStatsManager queries, not a fixed made-up split.
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val hourlyBuckets = (0 until 6).map { blockIndex ->
                    val blockStart = todayStart + blockIndex * 4 * 3600 * 1000L
                    val blockEnd = (blockStart + 4 * 3600 * 1000L).coerceAtMost(now)
                    repository.getScreenOnTimeForRange(blockStart, blockEnd)
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

                // Get app usage for this past day
                val pastApps = repository.getAppUsageList(dayStart, dayEnd)
                val daySot = pastApps.sumOf { it.screenTimeSinceChargeMs }
                val daySoff = (24 * 3600 * 1000L) - daySot

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
