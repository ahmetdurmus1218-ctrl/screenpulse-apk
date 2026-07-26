package com.example.notification

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import com.example.ScreenPulseApplication
import com.example.widget.ScreenPulseWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps a persistent, always-updating notification alive (the "Sürekli Açık" mode from
 * the in-app picker) — same idea as a music player's ongoing notification.
 *
 * Event-driven rather than polled: refreshes on real screen on/off and battery/charging
 * broadcasts (plus a once-a-minute TIME_TICK as a safety net), instead of a fixed sleep
 * loop. This reacts immediately to what actually changed and does no work in between,
 * which is both more accurate and lighter on battery than polling every N seconds.
 */
class LockScreenNotificationService : Service() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var isReceiverRegistered = false

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scope.launch { refresh() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceivers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Show a placeholder immediately so startForeground() is satisfied right away —
        // refresh() posts the real content moments later once data is fetched.
        val app = applicationContext as ScreenPulseApplication
        scope.launch {
            val battery = try {
                app.repository.getBatteryInfo()
            } catch (_: Throwable) {
                null
            } ?: return@launch
            val notification = NotificationHelper.buildNotification(
                this@LockScreenNotificationService, battery, screenOnMs = 0L, ongoing = true
            )
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
            refresh()
        }
        return START_STICKY
    }

    private fun registerReceivers() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_TIME_TICK) // once-a-minute safety net
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updateReceiver, filter)
        }
        isReceiverRegistered = true
    }

    private fun unregisterReceivers() {
        if (!isReceiverRegistered) return
        try {
            unregisterReceiver(updateReceiver)
        } catch (_: Throwable) {
        }
        isReceiverRegistered = false
    }

    private suspend fun refresh() {
        try {
            val app = applicationContext as ScreenPulseApplication
            val repository = app.repository
            val settingsManager = app.settingsManager

            repository.checkAndUpdateChargeTransition()
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
                this, batteryInfo, screenOnMs, ongoing = true
            )
            NotificationManagerCompat.from(this).notify(NotificationHelper.NOTIFICATION_ID, notification)

            // Keep home-screen widgets in sync with the same real-time triggers.
            ScreenPulseWidgetProvider.updateAllWidgets(applicationContext)
        } catch (_: Throwable) {
            // Never let a refresh failure crash the service — just skip this tick.
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceivers()
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
