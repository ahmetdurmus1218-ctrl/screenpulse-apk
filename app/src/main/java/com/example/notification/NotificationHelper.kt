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

    // v2: bumped from IMPORTANCE_LOW to IMPORTANCE_DEFAULT — on this device's launcher,
    // LOW-importance notifications were being posted successfully (visible in the shade,
    // AOD icon showing) but silently excluded from the actual lock screen list. A NEW
    // channel ID is required here: Android does not let app code change the importance
    // of a channel that already exists, so bumping the constant alone would do nothing
    // for anyone who already had the old "screenpulse_lockscreen" channel created.
    const val CHANNEL_ID = "screenpulse_lockscreen_v2"
    const val NOTIFICATION_ID = 4201

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Clean up the orphaned pre-v2 channel — it's dead weight in system notification
        // settings for anyone who had the app before the importance-level fix. Safe no-op
        // if it was never created.
        try {
            manager.deleteNotificationChannel("screenpulse_lockscreen")
        } catch (_: Throwable) {
        }
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Kilit Ekranı Özeti",
            // DEFAULT: still no heads-up popup (that needs HIGH), but visible/eligible for
            // lock screen display on OEM skins that filter LOW out of that list.
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Pil yüzdesi ve ekran açık süresini kilit ekranında gösterir."
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
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
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
    }
}
