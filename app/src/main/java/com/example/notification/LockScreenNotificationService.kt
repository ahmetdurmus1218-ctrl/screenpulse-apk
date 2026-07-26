package com.example.notification

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import com.example.ScreenPulseApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps a persistent, always-updating notification alive (chosen as the "Sürekli Açık"
 * mode from the in-app picker) — same idea as a music player's ongoing notification.
 * Refreshes on a ~60s tick and immediately on real battery broadcasts, so the lock
 * screen figure stays close to real-time without polling faster than that (no
 * meaningful accuracy gain from tighter than ~1 min for a screen-time/battery display).
 */
class LockScreenNotificationService : Service() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scope.launch { refresh() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        scope.launch {
            while (true) {
                refresh()
                delay(60_000L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Show a placeholder immediately so startForeground() is satisfied right away —
        // refresh() (below) posts the real content moments later once data is fetched.
        val app = applicationContext as ScreenPulseApplication
        scope.launch {
            val battery = try {
                app.repository.getBatteryInfo()
            } catch (_: Throwable) {
                null
            }
            val notification = NotificationHelper.buildNotification(
                this@LockScreenNotificationService,
                battery ?: return@launch,
                screenOnMs = 0L,
                ongoing = true
            )
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private suspend fun refresh() {
        try {
            val app = applicationContext as ScreenPulseApplication
            val repository = app.repository
            val settingsManager = app.settingsManager

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
        } catch (_: Throwable) {
            // Never let a refresh failure crash the service — just skip this tick.
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Throwable) {
        }
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
