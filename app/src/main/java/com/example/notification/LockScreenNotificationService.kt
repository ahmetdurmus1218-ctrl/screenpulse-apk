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
        // CRITICAL: startForeground() must be called synchronously, immediately, with no
        // suspend/async work in between — Android (increasingly strictly on 14+/15+) kills
        // the service if this doesn't happen within a few seconds of it starting. The
        // previous version fetched real battery data (a suspend DataStore/UsageStats call)
        // BEFORE calling startForeground(), which could easily miss that window and cause
        // the service — and its notification — to silently never appear at all.
        startForeground(NotificationHelper.NOTIFICATION_ID, NotificationHelper.buildPlaceholderNotification(this))
        registerReceivers()
        scope.launch { refresh() }
        startTicker()
    }

    /** Guarantees a refresh roughly every 45s regardless of what's happening with the
     *  screen — ACTION_TIME_TICK (used as a secondary trigger via registerReceivers)
     *  only fires while the screen is ON, so it can't be relied on alone for a
     *  consistent cadence. This only runs while the person has explicitly chosen
     *  "Sürekli Açık", which already accepts the associated battery trade-off. */
    private fun startTicker() {
        scope.launch {
            while (job.isActive) {
                kotlinx.coroutines.delay(45_000L)
                refresh()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch { refresh() }
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
