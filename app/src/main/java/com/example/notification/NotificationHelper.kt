package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.BatteryInfo
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Builds the compact battery + screen-time notification used for the lock-screen /
 * AOD display feature. Deliberately uses NotificationCompat's standard collapsed
 * layout (title + text), NOT a custom RemoteViews content view — after the widget
 * white-screen saga, a custom RemoteViews layout here would carry the exact same
 * risk class of bugs (layout/resource mismatches, launcher-specific quirks) for a
 * surface we have even less control over than a home-screen widget. The standard
 * layout is guaranteed compact (never expands to fill the lock screen) and renders
 * identically on every OEM skin.
 */
object NotificationHelper {

    const val CHANNEL_ID = "screenpulse_lockscreen"
    const val NOTIFICATION_ID = 4201

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Kilit Ekranı Özeti",
            // LOW: shows on lock screen/AOD without sound, vibration, or a heads-up popup —
            // the right importance level for a persistent status display, not an alert.
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Pil yüzdesi ve ekran açık süresini kilit ekranında gösterir."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun buildPlaceholderNotification(context: Context): android.app.Notification {
        ensureChannel(context)
        val openAppIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_widget_pulse)
            .setContentTitle("ScreenPulse")
            .setContentText("Yükleniyor…")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    fun buildNotification(
        context: Context,
        batteryInfo: BatteryInfo,
        screenOnMs: Long,
        ongoing: Boolean
    ): android.app.Notification {
        ensureChannel(context)

        val minutes = (screenOnMs / 60000f).roundToLong()
        val h = minutes / 60
        val m = minutes % 60
        val screenOnStr = if (h > 0) "${h}sa ${m}dk" else "${m}dk"

        val chargeStr = if (batteryInfo.isCharging) " ⚡" else ""
        val contentText = String.format(
            Locale.getDefault(),
            "%%%d%s  •  Ekran açık: %s",
            batteryInfo.percentage,
            chargeStr,
            screenOnStr
        )

        val openAppIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_widget_pulse)
            .setContentTitle("ScreenPulse")
            .setContentText(contentText)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }
}
