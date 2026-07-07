package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
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
        // CRITICAL FIX: without goAsync(), Android is free to kill this process the moment
        // onUpdate() returns — but all our work below (DataStore reads, Room DB, UsageStats
        // queries) happens asynchronously in a coroutine that hadn't necessarily finished yet.
        // That's why widgets could silently never update: the process could be torn down
        // mid-flight before appWidgetManager.updateAppWidget() ever got called.
        val pendingResult = goAsync()

        val app = context.applicationContext as ScreenPulseApplication
        val repository = app.repository
        val settingsManager = app.settingsManager

        scope.launch {
            try {
                // Catch up on any missed charge/unplug transition, same as the app does.
                repository.checkAndUpdateChargeTransition()

                // Fetch real telemetry
                val batteryInfo = repository.getBatteryInfo()
                val lastUnpluggedTime = settingsManager.lastUnpluggedTime.first()
                val now = System.currentTimeMillis()

                // Real screen on/off split, same accurate source used by the app itself —
                // NOT a sum of per-app foreground time (which can overcount).
                val (realScreenOn, realScreenOff) = repository.getScreenOnOffFromEvents(lastUnpluggedTime, now)

                val timeSinceCharge = now - lastUnpluggedTime
                val cleanTimeSinceCharge = if (timeSinceCharge > 0) timeSinceCharge else 4 * 3600 * 1000L
                val cleanScreenOn = if (realScreenOn > cleanTimeSinceCharge) cleanTimeSinceCharge else realScreenOn
                val cleanScreenOff = (cleanTimeSinceCharge - cleanScreenOn).coerceAtLeast(0L)

                appWidgetIds.forEach { widgetId ->
                    val views = try {
                        resolveWidgetView(
                            context = context,
                            widgetId = widgetId,
                            appWidgetManager = appWidgetManager,
                            batteryInfo = batteryInfo,
                            screenOnMs = cleanScreenOn,
                            screenOffMs = cleanScreenOff,
                            timeSinceChargeMs = cleanTimeSinceCharge
                        )
                    } catch (e: Throwable) {
                        // Record the specific failure, but still show SOMETHING rather
                        // than leaving the widget totally blank.
                        val sw = java.io.StringWriter()
                        e.printStackTrace(java.io.PrintWriter(sw))
                        context.getSharedPreferences("widget_diag", Context.MODE_PRIVATE).edit()
                            .putString("last_error", "resolveWidgetView failed: " + sw.toString().take(1800))
                            .putLong("last_error_ts", System.currentTimeMillis())
                            .apply()
                        RemoteViews(context.packageName, R.layout.widget_2x2).apply {
                            setTextViewText(R.id.widget_battery, "%${batteryInfo.percentage}")
                        }
                    }

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
                // Success — clear any previously recorded error so the app doesn't show stale info.
                context.getSharedPreferences("widget_diag", Context.MODE_PRIVATE).edit()
                    .putString("last_error", null)
                    .putLong("last_success_ts", System.currentTimeMillis())
                    .apply()
            } catch (e: Throwable) {
                // Prevent widget crashing, but record what happened so it can be viewed
                // from inside the app itself (no computer/adb needed to diagnose).
                // NOTE: catches Throwable, not just Exception — some failures (e.g.
                // OutOfMemoryError from bitmap drawing) are Errors, not Exceptions, and
                // a plain "catch (e: Exception)" would silently let those escape.
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                context.getSharedPreferences("widget_diag", Context.MODE_PRIVATE).edit()
                    .putString("last_error", sw.toString().take(2000))
                    .putLong("last_error_ts", System.currentTimeMillis())
                    .apply()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun resolveWidgetView(
        context: Context,
        widgetId: Int,
        appWidgetManager: AppWidgetManager,
        batteryInfo: BatteryInfo,
        screenOnMs: Long,
        screenOffMs: Long,
        timeSinceChargeMs: Long
    ): RemoteViews {
        // Query options to determine if we are 2x2, 4x2, or 4x4
        val options = appWidgetManager.getAppWidgetOptions(widgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

        val sotStr = formatWidgetTime(screenOnMs)
        val soffStr = formatWidgetTime(screenOffMs)
        val sinceChargeStr = formatWidgetTime(timeSinceChargeMs)
        val lastChargeStr = if (batteryInfo.lastChargeTimeMs > 0) {
            "Şarj: " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(batteryInfo.lastChargeTimeMs))
        } else {
            "Şarj: Bilinmiyor"
        }

        val batteryIconBitmap = drawMiniBatteryIcon(batteryInfo.percentage, batteryInfo.isCharging)

        return when {
            minWidth < 100 && minHeight >= 200 -> {
                // 1x4 Widget (narrow, tall): ring, then %+battery icon, then bolt+since-charge
                RemoteViews(context.packageName, R.layout.widget_1x4).apply {
                    setTextViewText(R.id.widget_battery, "%${batteryInfo.percentage}")
                    setImageViewBitmap(R.id.widget_battery_icon, batteryIconBitmap)
                    setTextViewText(R.id.widget_sot_value, sinceChargeStr)
                    val ring = drawScreenTimeRing(sotStr, screenOnMs, screenOffMs, compact = true)
                    setImageViewBitmap(R.id.widget_sot_ring, ring)
                }
            }
            minWidth < 200 && minHeight >= 200 -> {
                // 2x4 Widget (medium width, tall): header, big SOT number, %+battery icon
                RemoteViews(context.packageName, R.layout.widget_2x4).apply {
                    setTextViewText(R.id.widget_sot_value, sotStr)
                    setTextViewText(R.id.widget_battery, "%${batteryInfo.percentage}")
                    setImageViewBitmap(R.id.widget_battery_icon, batteryIconBitmap)
                }
            }
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
                // 4x2 Widget: ring on the left, %+Şarjdan Beri stacked on the right
                RemoteViews(context.packageName, R.layout.widget_4x2).apply {
                    setTextViewText(R.id.widget_battery, "%${batteryInfo.percentage}")
                    setTextViewText(R.id.widget_sot_value, sinceChargeStr)
                    setImageViewBitmap(R.id.widget_battery_icon, batteryIconBitmap)
                    val ring = drawScreenTimeRing(sotStr, screenOnMs, screenOffMs, compact = false)
                    setImageViewBitmap(R.id.widget_sot_ring, ring)
                }
            }
            else -> {
                // 2x2 Widget: ring, then %+battery icon
                RemoteViews(context.packageName, R.layout.widget_2x2).apply {
                    setTextViewText(R.id.widget_battery, "%${batteryInfo.percentage}")
                    setImageViewBitmap(R.id.widget_battery_icon, batteryIconBitmap)
                    val ring = drawScreenTimeRing(sotStr, screenOnMs, screenOffMs, compact = true)
                    setImageViewBitmap(R.id.widget_sot_ring, ring)
                }
            }
        }
    }

    /** Ring bitmap showing the screen-on/off proportion, matching the app's own ring styling. */
    /** Small horizontal battery pill icon (outline + green fill + nub), matching the reference exactly. */
    private fun drawMiniBatteryIcon(percentage: Int, isCharging: Boolean): Bitmap {
        val w = 120
        val h = 60
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bodyRight = w - 14f
        val nubWidth = 10f
        val nubHeight = h * 0.4f
        val strokeW = 5f

        val outlinePaint = Paint().apply {
            color = Color.parseColor("#66FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            isAntiAlias = true
        }
        val bodyRect = RectF(strokeW / 2, strokeW / 2, bodyRight, h - strokeW / 2)
        canvas.drawRoundRect(bodyRect, 10f, 10f, outlinePaint)

        val nubPaint = Paint().apply {
            color = Color.parseColor("#66FFFFFF")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val nubRect = RectF(bodyRight, (h - nubHeight) / 2f, bodyRight + nubWidth, (h + nubHeight) / 2f)
        canvas.drawRoundRect(nubRect, 4f, 4f, nubPaint)

        val fillColor = if (isCharging) Color.parseColor("#FFC857") else Color.parseColor("#00C853")
        val fillPaint = Paint().apply {
            color = fillColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val inset = strokeW + 4f
        val maxFillWidth = bodyRight - inset * 2
        val fillWidth = (maxFillWidth * (percentage.coerceIn(0, 100) / 100f)).coerceAtLeast(6f)
        val fillRect = RectF(inset, inset, inset + fillWidth, h - inset)
        canvas.drawRoundRect(fillRect, 6f, 6f, fillPaint)

        return bitmap
    }

    private fun drawScreenTimeRing(valueLabel: String, screenOnMs: Long, screenOffMs: Long, compact: Boolean): Bitmap {
        val size = 200
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val total = (screenOnMs + screenOffMs).toFloat()
        val onPct = if (total > 0) screenOnMs / total else 0f

        val ringStrokeWidth = if (compact) 16f else 20f
        val paintTrack = Paint().apply {
            color = Color.parseColor("#336F98FF")
            style = Paint.Style.STROKE
            strokeWidth = ringStrokeWidth
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }
        val paintProgress = Paint().apply {
            color = Color.parseColor("#2B66FF")
            style = Paint.Style.STROKE
            strokeWidth = ringStrokeWidth
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }
        val paintText = Paint().apply {
            color = Color.WHITE
            textSize = if (compact) 28f else 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val pad = ringStrokeWidth
        val rect = RectF(pad, pad, size - pad, size - pad)
        canvas.drawArc(rect, 0f, 360f, false, paintTrack)
        canvas.drawArc(rect, -90f, 360f * onPct, false, paintProgress)

        // Wrap "3sa 18dk" onto two lines if needed
        val parts = valueLabel.split(" ")
        if (parts.size == 2) {
            canvas.drawText(parts[0], size / 2f, size / 2f - 6f, paintText)
            val paintText2 = Paint(paintText).apply { textSize = paintText.textSize * 0.7f }
            canvas.drawText(parts[1], size / 2f, size / 2f + 22f, paintText2)
        } else {
            canvas.drawText(valueLabel, size / 2f, size / 2f + 10f, paintText)
        }

        return bitmap
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
        canvas.drawText("PİL", 80f, 112f, paintLabel)

        return bitmap
    }

    private fun formatWidgetTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val totalMinutes = totalSeconds / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            "${hours}sa ${minutes}dk"
        } else {
            "${minutes}dk"
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        job.cancel()
    }
}

class ScreenPulseWidgetProvider2x2 : ScreenPulseWidgetProvider()
class ScreenPulseWidgetProvider4x2 : ScreenPulseWidgetProvider()
class ScreenPulseWidgetProvider4x4 : ScreenPulseWidgetProvider()
class ScreenPulseWidgetProvider2x4 : ScreenPulseWidgetProvider()
class ScreenPulseWidgetProvider1x4 : ScreenPulseWidgetProvider()
