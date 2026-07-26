package com.example.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ScreenPulseApplication
import kotlinx.coroutines.flow.first

/**
 * Battery-friendly alternative to LockScreenNotificationService: refreshes the same
 * compact notification every ~15 minutes (the platform-enforced minimum interval for
 * periodic WorkManager work) instead of continuously, and doesn't run a foreground
 * service in between ticks.
 */
class LockScreenNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as ScreenPulseApplication
            val repository = app.repository
            val settingsManager = app.settingsManager

            // Respect the current mode: if the person switched away from "periodic"
            // since this was scheduled, do nothing (the caller cancels future runs too,
            // but a run already in flight when they switch should also no-op).
            if (settingsManager.lockScreenNotificationMode.first() != "periodic") {
                return Result.success()
            }

            val batteryInfo = repository.getBatteryInfo()
            val now = System.currentTimeMillis()
            val rawLastUnplugged = settingsManager.lastUnpluggedTime.first()
            val lastUnplugged = if (rawLastUnplugged <= 0L || rawLastUnplugged > now) {
                now - 4 * 3600 * 1000L
            } else {
                rawLastUnplugged
            }
            val (screenOnMs, _) = repository.getScreenOnOffFromEvents(lastUnplugged, now)

            val notification = NotificationHelper.buildNotification(
                applicationContext, batteryInfo, screenOnMs, ongoing = false
            )
            NotificationManagerCompat.from(applicationContext)
                .notify(NotificationHelper.NOTIFICATION_ID, notification)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
