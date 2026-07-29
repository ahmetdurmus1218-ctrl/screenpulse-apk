package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import com.example.MainActivity
import com.example.R
import com.example.ScreenPulseApplication
import com.example.data.model.BatteryInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

open class ScreenPulseWidgetProvider(
    @LayoutRes protected open val layoutResId: Int = R.layout.widget_2x2
) : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Step 1: Immediate synchronous update using this provider's exact layout
        appWidgetIds.forEach { widgetId ->
            try {
                val initialViews = RemoteViews(context.packageName, layoutResId)
                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                initialViews.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                appWidgetManager.updateAppWidget(widgetId, initialViews)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        val pendingResult = goAsync()

        val app = context.applicationContext as? ScreenPulseApplication
        if (app == null) {
            try { pendingResult.finish() } catch (_: Throwable) {}
            return
        }
        val repository = app.repository
        val settingsManager = app.settingsManager

        CoroutineScope(Dispatchers.IO).launch {
            try {
                try {
                    repository.checkAndUpdateChargeTransition()
                } catch (t: Throwable) {
                    t.printStackTrace()
                }

                val batteryInfo = try {
                    repository.getBatteryInfo()
                } catch (t: Throwable) {
                    BatteryInfo(
                        percentage = 50,
                        isCharging = false,
                        chargingStatus = "Deşarj Oluyor",
                        voltage = 3.8f,
                        temperature = 25.0f,
                        health = "İyi",
                        cycleCount = 0,
                        cycleCountIsEstimate = true,
                        cycleProgressPct = 0,
                        hardwareCycleCount = -1,
                        plugInCount = 0,
                        batteryUsedSinceCharge = 0,
                        lastChargeTimeMs = 0L,
                        lastUnpluggedTimeMs = 0L
                    )
                }

                val now = System.currentTimeMillis()
                val lastUnpluggedTime = try {
                    val rawLastUnplugged = settingsManager.lastUnpluggedTime.first()
                    if (rawLastUnplugged <= 0L || rawLastUnplugged > now) (now - 4 * 3600 * 1000L) else rawLastUnplugged
                } catch (t: Throwable) {
                    now - 4 * 3600 * 1000L
                }

                val (realScreenOn, realScreenOff) = try {
                    repository.getScreenOnOffFromEvents(lastUnpluggedTime, now)
                } catch (t: Throwable) {
                    (0L to 0L)
                }

                val timeSinceCharge = now - lastUnpluggedTime
                val cleanTimeSinceCharge = if (timeSinceCharge > 0) timeSinceCharge else 4 * 3600 * 1000L
                val cleanScreenOn = if (realScreenOn > cleanTimeSinceCharge) cleanTimeSinceCharge else realScreenOn
                val cleanScreenOff = (cleanTimeSinceCharge - cleanScreenOn).coerceAtLeast(0L)

                val isDark = try {
                    settingsManager.isDarkTheme.first()
                } catch (t: Throwable) {
                    true
                }

                appWidgetIds.forEach { widgetId ->
                    val views = populateWidgetViews(
                        context = context,
                        targetLayoutResId = layoutResId,
                        batteryInfo = batteryInfo,
                        screenOnMs = cleanScreenOn,
                        screenOffMs = cleanScreenOff,
                        timeSinceChargeMs = cleanTimeSinceCharge,
                        isDark = isDark
                    )

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
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                try {
                    pendingResult.finish()
                } catch (_: Throwable) {}
            }
        }
    }

    private fun populateWidgetViews(
        context: Context,
        targetLayoutResId: Int,
        batteryInfo: BatteryInfo,
        screenOnMs: Long,
        screenOffMs: Long,
        timeSinceChargeMs: Long,
        isDark: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, targetLayoutResId)
        views.setInt(
            R.id.widget_root,
            "setBackgroundResource",
            if (isDark) R.drawable.widget_background else R.drawable.widget_background_light
        )

        // Same semantic roles used consistently across every widget layout's XML —
        // just swapped for light-background-appropriate values when isDark is false.
        val colorPrimary = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#1A1A2E")
        val colorSecondary = if (isDark) Color.parseColor("#99AAB8E8") else Color.parseColor("#995A6478")
        val colorDim = if (isDark) Color.parseColor("#66FFFFFF") else Color.parseColor("#66000000")
        val colorAccent = Color.parseColor("#7A97FF") // brand blue reads fine on both backgrounds
        val colorDivider = if (isDark) Color.parseColor("#14FFFFFF") else Color.parseColor("#14000000")

        val sotStr = formatWidgetTime(screenOnMs)
        val soffStr = formatWidgetTime(screenOffMs)
        val sinceChargeStr = formatWidgetTime(timeSinceChargeMs)
        val lastChargeStr = if (batteryInfo.lastChargeTimeMs > 0) {
            "Şarj: " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(batteryInfo.lastChargeTimeMs))
        } else {
            "Şarj: Bilinmiyor"
        }

        val batteryIconBitmap = drawMiniBatteryIcon(batteryInfo.percentage, batteryInfo.isCharging, isDark)

        val cycleCountVal = if (batteryInfo.hardwareCycleCount > 0) batteryInfo.hardwareCycleCount else batteryInfo.cycleCount.coerceAtLeast(0)
        val cycleStr = "${cycleCountVal} dng"
        val cycleDotStr = "• ${cycleCountVal} dng"

        when (targetLayoutResId) {
            R.layout.widget_1x4 -> {
                views.setTextViewText(R.id.widget_battery, "%${batteryInfo.percentage}")
                views.setTextColor(R.id.widget_battery, colorPrimary)
                views.setTextViewText(R.id.widget_cycle_value, cycleStr)
                views.setTextColor(R.id.widget_cycle_value, colorSecondary)
                views.setImageViewBitmap(R.id.widget_battery_icon, batteryIconBitmap)
                views.setTextViewText(R.id.widget_sot_value, sinceChargeStr)
                views.setTextColor(R.id.widget_sot_value, colorAccent)
                views.setTextColor(R.id.widget_label_since_charge, colorDim)
                getVectorBitmap(context, R.drawable.ic_widget_bolt, 12, 12)?.let {
                    views.setImageViewBitmap(R.id.widget_bolt_icon, it)
                }
                val ring = drawScreenTimeRing(context, sotStr, screenOnMs, screenOffMs, compact = true, isDark = isDark)
                views.setImageViewBitmap(R.id.widget_sot_ring, ring)
            }
            R.layout.widget_2x4 -> {
                views.setTextViewText(R.id.widget_sot_value, sotStr)
                views.setTextColor(R.id.widget_sot_value, colorPrimary)
                views.setTextViewText(R.id.widget_battery, "%${batteryInfo.percentage}")
                views.setTextColor(R.id.widget_battery, colorPrimary)
                views.setTextViewText(R.id.widget_cycle_value, cycleDotStr)
                views.setTextColor(R.id.widget_cycle_value, colorSecondary)
                views.setTextColor(R.id.widget_label_since_charge_title, colorSecondary)
                views.setTextColor(R.id.widget_label_pil, colorDim)
                views.setInt(R.id.widget_divider, "setBackgroundColor", colorDivider)
                views.setImageViewBitmap(R.id.widget_battery_icon, batteryIconBitmap)
                getVectorBitmap(context, R.drawable.ic_widget_sun, 14, 14)?.let {
                    views.setImageViewBitmap(R.id.widget_sun_icon, it)
                }
            }
            R.layout.widget_4x4 -> {
                views.setTextViewText(R.id.widget_sot_value, sotStr)
                views.setTextColor(R.id.widget_sot_value, colorPrimary)
                views.setTextViewText(R.id.widget_screen_off_value, soffStr)
                views.setTextColor(R.id.widget_screen_off_value, colorPrimary)
                views.setTextViewText(R.id.widget_last_charge_time, lastChargeStr)
                views.setTextColor(R.id.widget_last_charge_time, colorDim)
                views.setTextColor(R.id.widget_label_brand, colorSecondary)
                views.setTextColor(R.id.widget_label_standby, colorDim)
                views.setTextColor(R.id.widget_label_temp, colorDim)
                views.setTextColor(R.id.widget_label_voltage, colorDim)
                views.setTextColor(R.id.widget_label_cycle_title, colorDim)
                views.setInt(R.id.widget_divider_1, "setBackgroundColor", colorDivider)
                views.setInt(R.id.widget_divider_2, "setBackgroundColor", colorDivider)
                getVectorBitmap(context, R.drawable.ic_widget_pulse, 14, 14)?.let {
                    views.setImageViewBitmap(R.id.widget_pulse_icon, it)
                }
                views.setTextViewText(
                    R.id.widget_temp_value,
                    String.format(Locale.getDefault(), "%.1f°C", batteryInfo.temperature)
                )
                views.setTextColor(R.id.widget_temp_value, colorPrimary)
                views.setTextViewText(
                    R.id.widget_voltage_value,
                    String.format(Locale.getDefault(), "%.1fV", batteryInfo.voltage)
                )
                views.setTextColor(R.id.widget_voltage_value, colorPrimary)
                views.setTextViewText(R.id.widget_cycle_value, cycleStr)
                views.setTextColor(R.id.widget_cycle_value, colorPrimary)
                val bitmap = drawCircularBattery(context, batteryInfo.percentage, batteryInfo.isCharging, isDark = isDark)
                views.setImageViewBitmap(R.id.widget_battery_circle, bitmap)
            }
            R.layout.widget_4x2 -> {
                views.setTextViewText(R.id.widget_battery, "%${batteryInfo.percentage}")
                views.setTextColor(R.id.widget_battery, colorPrimary)
                views.setTextViewText(R.id.widget_cycle_value, cycleStr)
                views.setTextColor(R.id.widget_cycle_value, colorSecondary)
                views.setTextViewText(R.id.widget_sot_value, sinceChargeStr)
                views.setTextColor(R.id.widget_sot_value, colorAccent)
                views.setTextColor(R.id.widget_label_pil_seviyesi, colorDim)
                views.setTextColor(R.id.widget_label_since_charge, colorDim)
                views.setInt(R.id.widget_divider, "setBackgroundColor", colorDivider)
                views.setImageViewBitmap(R.id.widget_battery_icon, batteryIconBitmap)
                getVectorBitmap(context, R.drawable.ic_widget_sun, 16, 16)?.let {
                    views.setImageViewBitmap(R.id.widget_sun_icon, it)
                }
                val ring = drawScreenTimeRing(context, sotStr, screenOnMs, screenOffMs, compact = false, isDark = isDark)
                views.setImageViewBitmap(R.id.widget_sot_ring, ring)
            }
            else -> { // widget_2x2
                views.setTextViewText(R.id.widget_battery, "%${batteryInfo.percentage}")
                views.setTextColor(R.id.widget_battery, colorPrimary)
                views.setTextViewText(R.id.widget_cycle_value, cycleStr)
                views.setTextColor(R.id.widget_cycle_value, colorSecondary)
                views.setImageViewBitmap(R.id.widget_battery_icon, batteryIconBitmap)
                val ring = drawScreenTimeRing(context, sotStr, screenOnMs, screenOffMs, compact = true, isDark = isDark)
                views.setImageViewBitmap(R.id.widget_sot_ring, ring)
            }
        }

        return views
    }

    private fun getVectorBitmap(context: Context, resId: Int, widthDp: Int = 16, heightDp: Int = 16): Bitmap? {
        return try {
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, resId) ?: return null
            val density = context.resources.displayMetrics.density
            val w = (widthDp * density).toInt().coerceAtLeast(1)
            val h = (heightDp * density).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            bitmap
        } catch (_: Throwable) {
            null
        }
    }

    private fun drawMiniBatteryIcon(percentage: Int, isCharging: Boolean, isDark: Boolean): Bitmap {
        val w = 120
        val h = 60
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bodyRight = w - 14f
        val nubWidth = 10f
        val nubHeight = h * 0.4f
        val strokeW = 5f

        val outlineColor = if (isDark) Color.parseColor("#66FFFFFF") else Color.parseColor("#66000000")
        val outlinePaint = Paint().apply {
            color = outlineColor
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            isAntiAlias = true
        }
        val bodyRect = RectF(strokeW / 2, strokeW / 2, bodyRight, h - strokeW / 2)
        canvas.drawRoundRect(bodyRect, 10f, 10f, outlinePaint)

        val nubPaint = Paint().apply {
            color = outlineColor
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

    private fun drawScreenTimeRing(context: Context, valueLabel: String, screenOnMs: Long, screenOffMs: Long, compact: Boolean, isDark: Boolean): Bitmap {
        val size = 200
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val total = (screenOnMs + screenOffMs).toFloat()
        val onPct = if (total > 0) (screenOnMs.toFloat() / total).coerceIn(0f, 1f) else 0f

        val ringStrokeWidth = if (compact) 16f else 20f
        val pad = ringStrokeWidth + 6f
        val rect = RectF(pad, pad, size - pad, size - pad)

        val paintTrack = Paint().apply {
            color = if (isDark) Color.parseColor("#336F98FF") else Color.parseColor("#332B66FF")
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
            color = if (isDark) Color.WHITE else Color.parseColor("#1A1A2E")
            textSize = if (compact) 28f else 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        canvas.drawArc(rect, 0f, 360f, false, paintTrack)
        val sweepAngle = 360f * onPct
        canvas.drawArc(rect, -90f, sweepAngle, false, paintProgress)

        // Draw sun indicator orb at the tip of the progress arc
        val endAngleDeg = -90f + sweepAngle
        val endAngleRad = Math.toRadians(endAngleDeg.toDouble())
        val centerX = size / 2f
        val centerY = size / 2f
        val radius = (size - 2 * pad) / 2f

        val tipX = centerX + radius * Math.cos(endAngleRad).toFloat()
        val tipY = centerY + radius * Math.sin(endAngleRad).toFloat()

        // Glowing backdrop circle for the sun
        val glowPaint = Paint().apply {
            color = Color.parseColor("#80FFC857")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val glowRadius = if (compact) 12f else 15f
        canvas.drawCircle(tipX, tipY, glowRadius, glowPaint)

        // Draw sun vector icon centered at tip
        val sunSizeDp = if (compact) 16 else 20
        getVectorBitmap(context, R.drawable.ic_widget_sun, sunSizeDp, sunSizeDp)?.let { sunBmp ->
            canvas.drawBitmap(sunBmp, tipX - sunBmp.width / 2f, tipY - sunBmp.height / 2f, null)
        }

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

    private fun drawCircularBattery(context: Context, percentage: Int, isCharging: Boolean, isDark: Boolean): Bitmap {
        val size = 160
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val pad = 22f
        val rect = RectF(pad, pad, size - pad, size - pad)

        val paintTrack = Paint().apply {
            color = if (isDark) Color.parseColor("#15FFFFFF") else Color.parseColor("#15000000")
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

        val sweepAngle = (percentage.coerceIn(0, 100) / 100f) * 360f
        canvas.drawArc(rect, 0f, 360f, false, paintTrack)
        canvas.drawArc(rect, -90f, sweepAngle, false, paintProgress)

        // Sun or bolt icon at progress tip on battery circle
        val endAngleDeg = -90f + sweepAngle
        val endAngleRad = Math.toRadians(endAngleDeg.toDouble())
        val centerX = size / 2f
        val centerY = size / 2f
        val radius = (size - 2 * pad) / 2f

        val tipX = centerX + radius * Math.cos(endAngleRad).toFloat()
        val tipY = centerY + radius * Math.sin(endAngleRad).toFloat()

        val glowColorStr = if (isCharging) "#80FFC857" else "#802196F3"
        val glowPaint = Paint().apply {
            color = Color.parseColor(glowColorStr)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(tipX, tipY, 13f, glowPaint)

        val iconRes = if (isCharging) R.drawable.ic_widget_bolt else R.drawable.ic_widget_sun
        getVectorBitmap(context, iconRes, 16, 16)?.let { iconBmp ->
            canvas.drawBitmap(iconBmp, tipX - iconBmp.width / 2f, tipY - iconBmp.height / 2f, null)
        }

        val paintText = Paint().apply {
            color = if (isDark) Color.WHITE else Color.parseColor("#1A1A2E")
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val paintLabel = Paint().apply {
            color = if (isDark) Color.parseColor("#88FFFFFF") else Color.parseColor("#88000000")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        canvas.drawText("${percentage}%", 80f, 85f, paintText)
        canvas.drawText("PİL", 80f, 112f, paintLabel)

        return bitmap
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val providers = arrayOf(
                    ScreenPulseWidgetProvider2x2::class.java,
                    ScreenPulseWidgetProvider4x2::class.java,
                    ScreenPulseWidgetProvider4x4::class.java,
                    ScreenPulseWidgetProvider2x4::class.java,
                    ScreenPulseWidgetProvider1x4::class.java
                )
                for (providerClass in providers) {
                    val component = android.content.ComponentName(context, providerClass)
                    val ids = appWidgetManager.getAppWidgetIds(component)
                    if (ids.isNotEmpty()) {
                        val intent = Intent(context, providerClass).apply {
                            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                        }
                        context.sendBroadcast(intent)
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
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
}

class ScreenPulseWidgetProvider2x2 : ScreenPulseWidgetProvider(R.layout.widget_2x2)
class ScreenPulseWidgetProvider4x2 : ScreenPulseWidgetProvider(R.layout.widget_4x2)
class ScreenPulseWidgetProvider4x4 : ScreenPulseWidgetProvider(R.layout.widget_4x4)
class ScreenPulseWidgetProvider2x4 : ScreenPulseWidgetProvider(R.layout.widget_2x4)
class ScreenPulseWidgetProvider1x4 : ScreenPulseWidgetProvider(R.layout.widget_1x4)
