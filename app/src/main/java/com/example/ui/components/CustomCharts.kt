package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.BatteryLogEntity
import com.example.data.database.UsageHistoryEntity
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ScreenOnOffRing(
    screenOnMs: Long,
    screenOffMs: Long,
    modifier: Modifier = Modifier
) {
    val total = (screenOnMs + screenOffMs).toFloat()
    val screenOnPct = if (total > 0) (screenOnMs / total) else 0f
    val screenOffPct = if (total > 0) (screenOffMs / total) else 0f

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondaryContainer
    val onSurface = MaterialTheme.colorScheme.onSurface

    val animatedOnPct by animateFloatAsState(
        targetValue = screenOnPct,
        animationSpec = tween(durationMillis = 1000),
        label = "onPct"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 32.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val rectSize = Size(diameter, diameter)
            val topLeft = Offset(
                (size.width - diameter) / 2,
                (size.height - diameter) / 2
            )

            // Draw Background Track (Screen Off)
            drawArc(
                color = secondaryColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = rectSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Draw Foreground (Screen On)
            if (animatedOnPct > 0) {
                drawArc(
                    color = primaryColor,
                    startAngle = -90f,
                    sweepAngle = animatedOnPct * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = rectSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val sotHours = screenOnMs / (1000f * 60 * 60)
            Text(
                text = String.format(Locale.getDefault(), "%.1f Hrs", sotHours),
                style = MaterialTheme.typography.titleLarge,
                color = onSurface
            )
            Text(
                text = "Screen On Time",
                style = MaterialTheme.typography.labelMedium,
                color = onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun BatteryDrainChart(
    logs: List<BatteryLogEntity>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()

    // Mock logs if there are fewer than 2 to render a line
    val activeLogs = remember(logs) {
        if (logs.size >= 2) {
            logs
        } else {
            val now = System.currentTimeMillis()
            listOf(
                BatteryLogEntity(now - 12 * 3600 * 1000L, 100, false),
                BatteryLogEntity(now - 8 * 3600 * 1000L, 85, false),
                BatteryLogEntity(now - 4 * 3600 * 1000L, 60, false),
                BatteryLogEntity(now, 45, false)
            )
        }
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val minTime = activeLogs.first().timestamp
        val maxTime = activeLogs.last().timestamp
        val timeDelta = (maxTime - minTime).toFloat()

        val points = activeLogs.map { log ->
            val x = if (timeDelta > 0) {
                ((log.timestamp - minTime) / timeDelta) * width
            } else {
                0f
            }
            val y = height - (log.batteryLevel / 100f) * height
            Offset(x, y)
        }

        // Draw horizontal grid lines
        for (i in 1..3) {
            val gridY = height * (i / 4f)
            drawLine(
                color = onSurface.copy(alpha = 0.1f),
                start = Offset(0f, gridY),
                end = Offset(width, gridY),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Draw line pathway
        val path = Path().apply {
            if (points.isNotEmpty()) {
                moveTo(points[0].x, points[0].y)
                for (j in 1 until points.size) {
                    val pPrev = points[j - 1]
                    val pCurr = points[j]
                    // Bezier smoothing
                    val controlX = (pPrev.x + pCurr.x) / 2
                    cubicTo(controlX, pPrev.y, controlX, pCurr.y, pCurr.x, pCurr.y)
                }
            }
        }

        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw glowing area under the path
        if (points.isNotEmpty()) {
            val fillPath = Path().apply {
                addPath(path)
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.25f),
                        primaryColor.copy(alpha = 0.0f)
                    )
                )
            )
        }

        // Draw Y Axis Labels
        val textStyle = TextStyle(color = onSurface.copy(alpha = 0.5f), fontSize = 10.sp)
        drawText(
            textMeasurer = textMeasurer,
            text = "100%",
            topLeft = Offset(8.dp.toPx(), 4.dp.toPx()),
            style = textStyle
        )
        drawText(
            textMeasurer = textMeasurer,
            text = "0%",
            topLeft = Offset(8.dp.toPx(), height - 16.dp.toPx()),
            style = textStyle
        )
    }
}

@Composable
fun UsageBarChart(
    history: List<UsageHistoryEntity>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()

    val displayHistory = remember(history) {
        if (history.isNotEmpty()) {
            history.takeLast(7)
        } else {
            // Generate clean mock days for visual previews
            val sdf = SimpleDateFormat("EE", Locale.getDefault())
            val list = mutableListOf<UsageHistoryEntity>()
            for (i in 6 downTo 0) {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -i)
                val dayStr = sdf.format(cal.time)
                list.add(
                    UsageHistoryEntity(
                        date = dayStr,
                        screenOnTimeMs = (2..6).random() * 3600 * 1000L,
                        screenOffTimeMs = 12 * 3600 * 1000L,
                        batteryUsedPct = 30,
                        totalTimeSinceChargeMs = 24 * 3600 * 1000L
                    )
                )
            }
            list
        }
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val maxSotMs = (displayHistory.maxOfOrNull { it.screenOnTimeMs } ?: 1L).coerceAtLeast(3600 * 1000L)
        val itemCount = displayHistory.size
        val barWidth = (width / (itemCount * 1.6f)).coerceAtLeast(16.dp.toPx())
        val spacing = (width - (barWidth * itemCount)) / (itemCount + 1)

        displayHistory.forEachIndexed { index, item ->
            val sotHours = item.screenOnTimeMs / (1000f * 60 * 60)
            val barHeight = (item.screenOnTimeMs.toFloat() / maxSotMs) * (height - 32.dp.toPx())
            val x = spacing + index * (barWidth + spacing)
            val y = height - 20.dp.toPx() - barHeight

            // Draw background track of bar
            drawRoundRect(
                color = containerColor.copy(alpha = 0.5f),
                topLeft = Offset(x, 0f),
                size = Size(barWidth, height - 20.dp.toPx()),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            )

            // Draw actual bar
            if (barHeight > 0f) {
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
            }

            // Draw x label
            val label = try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val parsedDate = inputFormat.parse(item.date)
                val outputFormat = SimpleDateFormat("EE", Locale.getDefault())
                if (parsedDate != null) outputFormat.format(parsedDate) else item.date
            } catch (e: Exception) {
                item.date
            }

            val textLayoutResult = textMeasurer.measure(
                text = label,
                style = TextStyle(color = onSurface.copy(alpha = 0.6f), fontSize = 10.sp)
            )
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(x + (barWidth - textLayoutResult.size.width) / 2, height - 16.dp.toPx())
            )

            // Draw value on top of bar
            val valueStr = String.format(Locale.getDefault(), "%.1fh", sotHours)
            val valueLayoutResult = textMeasurer.measure(
                text = valueStr,
                style = TextStyle(color = onSurface, fontSize = 9.sp)
            )
            drawText(
                textLayoutResult = valueLayoutResult,
                topLeft = Offset(x + (barWidth - valueLayoutResult.size.width) / 2, y - 14.dp.toPx())
            )
        }
    }
}
