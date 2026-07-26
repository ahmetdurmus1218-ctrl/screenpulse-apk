package com.example.notification

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val PERIODIC_WORK_NAME = "lock_screen_notification_periodic"

object LockScreenNotificationController {

    /** "off" | "continuous" | "periodic" */
    fun applyMode(context: Context, mode: String) {
        val workManager = WorkManager.getInstance(context)

        // Always start from a clean slate, then set up whichever mode is active.
        context.stopService(Intent(context, LockScreenNotificationService::class.java))
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)

        when (mode) {
            "continuous" -> {
                context.startForegroundService(Intent(context, LockScreenNotificationService::class.java))
            }
            "periodic" -> {
                val request = PeriodicWorkRequestBuilder<LockScreenNotificationWorker>(
                    15, TimeUnit.MINUTES
                ).build()
                workManager.enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    request
                )
            }
            else -> {
                NotificationManagerCompat.from(context).cancel(NotificationHelper.NOTIFICATION_ID)
            }
        }
    }
}
