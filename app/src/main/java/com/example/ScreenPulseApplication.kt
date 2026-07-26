package com.example

import android.app.Application
import com.example.data.database.ScreenPulseDatabase
import com.example.data.datastore.SettingsManager
import com.example.data.repository.UsageRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
        reapplyLockScreenNotificationMode()
    }

    private fun reapplyLockScreenNotificationMode() {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val mode = settingsManager.lockScreenNotificationMode.first()
                if (mode != "off") {
                    com.example.notification.LockScreenNotificationController.applyMode(this@ScreenPulseApplication, mode)
                }
            } catch (_: Throwable) {
                // Non-critical — worst case the person re-toggles it from the app.
            }
        }
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
