package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.ScreenPulseApplication
import com.example.data.model.BatteryInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

open class ScreenPulseWidgetProvider : AppWidgetProvider() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext as ScreenPulseApplication
        val repository = app.repository
        val settingsManager = app.settingsManager

        scope.launch {
            try {
                // Fetch real telemetry
                val batteryInfo = repository.getBatteryInfo()
                val lastUnpluggedTime = settingsManager.lastUnpluggedTime.first()
                val now = System.currentTimeMillis()

                val rawAppUsages = repository.getAppUsageList(lastUnpluggedTime, now)
                val screenOnMs = rawAppUsages.sumOf { it.screenTimeSinceChargeMs }

                val timeSinceCharge = now - lastUnpluggedTime
                val cleanTimeSinceCharge = if (timeSinceCharge > 0) timeSinceCharge else 4 * 3600 * 1000L
                val cleanScreenOn = if (screenOnMs > cleanTimeSinceCharge) cleanTimeSinceCharge else screenOnMs
                val cleanScreenOff = cleanTimeSinceCharge - cleanScreenOn

                appWidgetIds.forEach { widgetId ->
                    val views = resolveWidgetView(
                        context = context,
                        widgetId = widgetId,
                        appWidgetManager = appWidgetManager,
                        batteryInfo = batteryInfo,
                        screenOnMs = cleanScreenOn,
                        screenOffMs = cleanScreenOff
                    )

                    // Add PendingIntent to open App on click
                    val intent = Intent(context, MainActivity::class.java)
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            } catch (e: Exception) {
                // Prevent widget crashing
            }
        }
    }

    private fun resolveWidgetView(
        context: Context,
        widgetId: Int,
        appWidgetManager: AppWidgetManager,
        batteryInfo: BatteryInfo,
        screenOnMs: Long,
        screenOffMs: Long
    ): RemoteViews {
        // Query options to determine if we are 2x2, 4x2, or 4x4
        val options = appWidgetManager.getAppWidgetOptions(widgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

        val sotStr = formatWidgetTime(screenOnMs)
        val soffStr = formatWidgetTime(screenOffMs)
        val lastChargeStr = if (batteryInfo.lastChargeTimeMs > 0) {
            "Pull: " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(batteryInfo.lastChargeTimeMs))
        } else {
            "Pull: Unknown"
        }

        return when {
            minWidth >= 200 && minHeight >= 200 -> {
                // 4x4 Widget
                RemoteViews(context.packageName, R.layout.widget_4x4).apply {
                    setTextViewText(R.id.widget_sot_value, sotStr)
                    setTextViewText(R.id.widget_screen_off_value, soffStr)
                    setTextViewText(R.id.widget_last_charge_time, lastChargeStr)
                    setTextViewText(
                        R.id.widget_temp_value,
                        String.format(Locale.getDefault(), "%.1f°C", batteryInfo.temperature)
                    )
                    setTextViewText(
                        R.id.widget_voltage_value,
                        String.format(Locale.getDefault(), "%.1fV", batteryInfo.voltage)
                    )
                    // Draw circular battery bitmap
                    val bitmap = drawCircularBattery(batteryInfo.percentage, batteryInfo.isCharging)
                    setImageViewBitmap(R.id.widget_battery_circle, bitmap)
                }
            }
            minWidth >= 200 -> {
                // 4x2 Widget
                RemoteViews(context.packageName, R.layout.widget_4x2).apply {
                    setTextViewText(R.id.widget_sot_value, sotStr)
                    setTextViewText(R.id.widget_last_charge_time, lastChargeStr)
                    // Draw circular battery bitmap
                    val bitmap = drawCircularBattery(batteryInfo.percentage, batteryInfo.isCharging)
                    setImageViewBitmap(R.id.widget_battery_circle, bitmap)
                }
            }
            else -> {
                // 2x2 Widget
                RemoteViews(context.packageName, R.layout.widget_2x2).apply {
                    setTextViewText(R.id.widget_sot_value, sotStr)
                    setTextViewText(R.id.widget_battery, "Battery: ${batteryInfo.percentage}%")
                }
            }
        }
    }

    private fun drawCircularBattery(percentage: Int, isCharging: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paintTrack = Paint().apply {
            color = Color.parseColor("#15FFFFFF") // Subtle overlay track
            style = Paint.Style.STROKE
            strokeWidth = 14f
            isAntiAlias = true
        }

        val progressColorStr = if (isCharging) "#4CAF50" else "#2196F3"
        val paintProgress = Paint().apply {
            color = Color.parseColor(progressColorStr)
            style = Paint.Style.STROKE
            strokeWidth = 14f
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }

        val paintText = Paint().apply {
            color = Color.WHITE
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val paintLabel = Paint().apply {
            color = Color.parseColor("#88FFFFFF")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val rect = RectF(16f, 16f, 144f, 144f)
        canvas.drawArc(rect, 0f, 360f, false, paintTrack)
        canvas.drawArc(rect, -90f, percentage * 3.6f, false, paintProgress)

        // Draw percentage text
        canvas.drawText("${percentage}%", 80f, 85f, paintText)
        canvas.drawText("BATTERY", 80f, 112f, paintLabel)

        return bitmap
    }

    private fun formatWidgetTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val totalMinutes = totalSeconds / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        job.cancel()
    }
}

class ScreenPulseWidgetProvider2x2 : ScreenPulseWidgetProvider()
class ScreenPulseWidgetProvider4x2 : ScreenPulseWidgetProvider()
class ScreenPulseWidgetProvider4x4 : ScreenPulseWidgetProvider()
