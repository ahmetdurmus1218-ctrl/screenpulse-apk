package com.example.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "screenpulse_settings")

class SettingsManager(private val context: Context) {

    companion object {
        private val KEY_LAST_UNPLUGGED_TIME = longPreferencesKey("last_unplugged_time")
        private val KEY_LAST_UNPLUGGED_BATTERY = intPreferencesKey("last_unplugged_battery")
        private val KEY_LAST_CHARGE_TIME = longPreferencesKey("last_charge_time")
        private val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")
        private val KEY_WAS_CHARGING = booleanPreferencesKey("was_charging")
        private val KEY_CHARGE_SESSION_START_LEVEL = intPreferencesKey("charge_session_start_level")
        private val KEY_CUMULATIVE_CHARGE_PERCENT = floatPreferencesKey("cumulative_charge_percent")
        private val KEY_PLUG_IN_COUNT = intPreferencesKey("plug_in_count")
    }

    /**
     * Running total of "% points charged" across every real charging session this app has
     * observed. Dividing by 100 gives an estimated cycle count (one full cycle = the
     * equivalent of charging 0%→100% once, possibly spread across many partial sessions),
     * for devices/Android versions that don't expose the real hardware cycle counter.
     */
    val cumulativeChargePercent: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[KEY_CUMULATIVE_CHARGE_PERCENT] ?: 0f
    }

    /**
     * Simple, literal count of "how many times the phone was plugged in to charge" —
     * distinct from the health-based cycle estimate above, which counts accumulated %
     * charged, not plug events. Kept separate on purpose: five quick top-up plugs and
     * one long full charge are very different for battery wear even though they could
     * both be "1 charge event" depending on how you count, so this raw count is shown
     * as its own metric rather than folded into the cycle estimate.
     */
    val plugInCount: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_PLUG_IN_COUNT] ?: 0
    }

    suspend fun onChargingSessionStart(batteryLevel: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CHARGE_SESSION_START_LEVEL] = batteryLevel
            preferences[KEY_PLUG_IN_COUNT] = (preferences[KEY_PLUG_IN_COUNT] ?: 0) + 1
        }
    }

    suspend fun onChargingSessionEnd(batteryLevelNow: Int) {
        context.dataStore.edit { preferences ->
            val startLevel = preferences[KEY_CHARGE_SESSION_START_LEVEL]
            if (startLevel != null && batteryLevelNow > startLevel) {
                val gained = (batteryLevelNow - startLevel).toFloat()
                val current = preferences[KEY_CUMULATIVE_CHARGE_PERCENT] ?: 0f
                preferences[KEY_CUMULATIVE_CHARGE_PERCENT] = current + gained
            }
            preferences.remove(KEY_CHARGE_SESSION_START_LEVEL)
        }
    }

    val wasCharging: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_WAS_CHARGING] ?: false
    }

    suspend fun setWasCharging(charging: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_WAS_CHARGING] = charging
        }
    }

    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_DARK_THEME] ?: true // app defaults to dark
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DARK_THEME] = enabled
        }
    }

    val lastUnpluggedTime: Flow<Long> = context.dataStore.data.map { preferences ->
        // 0L until a real value has been persisted (see initializeIfNeeded / triggerUnplugEvent).
        // Previously this recomputed "now - 4h" on every read, which always evaluated to
        // exactly 4 hours elapsed no matter when you checked it.
        preferences[KEY_LAST_UNPLUGGED_TIME] ?: 0L
    }

    val lastUnpluggedBattery: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_UNPLUGGED_BATTERY] ?: 100
    }

    val lastChargeTime: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_CHARGE_TIME] ?: 0L
    }

    /**
     * Ensures we always have a real, persisted baseline timestamp instead of a
     * freshly-recomputed fake one. Call once at app startup. If this is the very
     * first run (or the values were never set because no unplug/plug broadcast
     * has fired yet), anchor both timestamps to "now" so elapsed-time math is
     * honest (starts at 0 and actually counts up) rather than permanently frozen.
     */
    suspend fun initializeIfNeeded(currentBatteryLevel: Int) {
        context.dataStore.edit { preferences ->
            val now = System.currentTimeMillis()
            if (preferences[KEY_LAST_UNPLUGGED_TIME] == null) {
                preferences[KEY_LAST_UNPLUGGED_TIME] = now
            }
            if (preferences[KEY_LAST_CHARGE_TIME] == null) {
                preferences[KEY_LAST_CHARGE_TIME] = now
            }
            if (preferences[KEY_LAST_UNPLUGGED_BATTERY] == null) {
                // BUG FIX: this used to default to a hardcoded 100 forever (never actually
                // set until a real unplug broadcast fired), so "battery used since charge"
                // was really just "100 - current battery", not a real measurement.
                preferences[KEY_LAST_UNPLUGGED_BATTERY] = currentBatteryLevel
            }
        }
    }

    suspend fun saveUnpluggedState(timeMs: Long, batteryLevel: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LAST_UNPLUGGED_TIME] = timeMs
            preferences[KEY_LAST_UNPLUGGED_BATTERY] = batteryLevel
        }
    }

    suspend fun saveLastChargeTime(timeMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LAST_CHARGE_TIME] = timeMs
        }
    }
}
