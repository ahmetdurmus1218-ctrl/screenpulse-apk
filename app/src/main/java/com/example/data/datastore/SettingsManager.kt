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
    suspend fun initializeIfNeeded() {
        context.dataStore.edit { preferences ->
            val now = System.currentTimeMillis()
            if (preferences[KEY_LAST_UNPLUGGED_TIME] == null) {
                preferences[KEY_LAST_UNPLUGGED_TIME] = now
            }
            if (preferences[KEY_LAST_CHARGE_TIME] == null) {
                preferences[KEY_LAST_CHARGE_TIME] = now
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
