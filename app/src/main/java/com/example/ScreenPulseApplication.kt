package com.example

import android.app.Application
import com.example.data.database.ScreenPulseDatabase
import com.example.data.datastore.SettingsManager
import com.example.data.repository.UsageRepository

class ScreenPulseApplication : Application() {

    lateinit var repository: UsageRepository
        private set

    lateinit var settingsManager: SettingsManager
        private set

    override fun onCreate() {
        super.onCreate()

        // Register crash logger FIRST, before anything else can throw.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(CrashLogger(this, previousHandler))

        val database = ScreenPulseDatabase.getDatabase(this)
        settingsManager = SettingsManager(this)
        repository = UsageRepository(this, database.usageDao(), settingsManager)

        schedulePeriodicBatteryStateCheck()
    }

    private fun schedulePeriodicBatteryStateCheck() {
        val request = androidx.work.PeriodicWorkRequestBuilder<com.example.worker.BatteryStateWorker>(
            15, java.util.concurrent.TimeUnit.MINUTES
        ).build()

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "battery_state_check",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
