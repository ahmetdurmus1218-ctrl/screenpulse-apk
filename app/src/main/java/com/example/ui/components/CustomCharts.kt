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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val isCompact = maxWidth < 140.dp
        
        val strokeWidthDp = if (isCompact) 10.dp else 16.dp
        val sunRadiusDp = if (isCompact) 4.dp else 7.dp
        val innerRayRDp = if (isCompact) 5.5.dp else 9.dp
        val outerRayRDp = if (isCompact) 8.dp else 12.dp
        val rayWidthDp = if (isCompact) 1.2.dp else 2.dp

        val total = (screenOnMs + screenOffMs).toFloat()
        val screenOnPct = if (total > 0) (screenOnMs / total) else 0f

        val screenOnColor = Color(0xFF2B66FF)   // Royal Blue (Screen On)
        val screenOffColor = Color(0xFF6F98FF)  // Light Pastel Blue (Screen Off)
        val trackColor = MaterialTheme.colorScheme.outlineVariant      // Theme-aware track color

        val animatedOnPct by animateFloatAsState(
            targetValue = screenOnPct,
            animationSpec = tween(durationMillis = 1000),
            label = "onPct"
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = strokeWidthDp.toPx()
            val diameter = size.minDimension - strokeWidth
            val rectSize = Size(diameter, diameter)
            val topLeft = Offset(
                (size.width - diameter) / 2,
                (size.height - diameter) / 2
            )

            // 1. Draw Background/Track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = rectSize,
                style = Stroke(width = strokeWidth)
            )

            val sweepOn = animatedOnPct * 360f
            val sweepOff = (1f - animatedOnPct) * 360f

            // 2. Draw Screen Off Sector (Light Pastel Blue)
            if (sweepOff > 0) {
                drawArc(
                    color = screenOffColor,
                    startAngle = -90f + sweepOn,
                    sweepAngle = sweepOff,
                    useCenter = false,
                    topLeft = topLeft,
                    size = rectSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
            }

            // 3. Draw Screen On Sector (Royal Blue)
            if (sweepOn > 0) {
                drawArc(
                    color = screenOnColor,
                    startAngle = -90f,
                    sweepAngle = sweepOn,
                    useCenter = false,
                    topLeft = topLeft,
                    size = rectSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
            }

            // 4. Draw Beautiful Golden Sun Indicator at the end of the Screen On sector
            val angleRad = Math.toRadians((-90f + sweepOn).toDouble())
            val r = diameter / 2
            val cx = size.width / 2
            val cy = size.height / 2
            val sunX = cx + r * kotlin.math.cos(angleRad).toFloat()
            val sunY = cy + r * kotlin.math.sin(angleRad).toFloat()
            val sunCenter = Offset(sunX, sunY)

            // Golden Sun Core
            drawCircle(
                color = Color(0xFFFFD54F),
                radius = sunRadiusDp.toPx(),
                center = sunCenter
            )
            // Golden Sun Core Outline
            drawCircle(
                color = Color(0xFFFFB300),
                radius = sunRadiusDp.toPx(),
                center = sunCenter,
                style = Stroke(width = if (isCompact) 1.dp.toPx() else 1.5.dp.toPx())
            )

            // Sun Rays (8 rays)
            val rayCount = 8
            val innerRayR = innerRayRDp.toPx()
            val outerRayR = outerRayRDp.toPx()
            for (i in 0 until rayCount) {
                val rayAngle = Math.toRadians((i * (360.0 / rayCount)).toDouble())
                val startX = sunX + innerRayR * kotlin.math.cos(rayAngle).toFloat()
                val startY = sunY + innerRayR * kotlin.math.sin(rayAngle).toFloat()
                val endX = sunX + outerRayR * kotlin.math.cos(rayAngle).toFloat()
                val endY = sunY + outerRayR * kotlin.math.sin(rayAngle).toFloat()
                drawLine(
                    color = Color(0xFFFFD54F),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = rayWidthDp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(if (isCompact) 6.dp else 12.dp)
        ) {
            val totalSeconds = screenOnMs / 1000
            val totalMinutes = totalSeconds / 60
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            val displaySot = if (hours > 0) "${hours}s ${minutes}dk" else "${minutes}dk"

            Text(
                text = displaySot,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = if (isCompact) 18.sp else 32.sp
                ),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(if (isCompact) 1.dp else 2.dp))
            Text(
                text = if (isCompact) "Ekran Açık" else "Ekran Açık Süresi",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (isCompact) 10.sp else 13.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1
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

    if (logs.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "Eğriyi çizmek için henüz yeterli pil verisi yok",
                style = MaterialTheme.typography.bodyMedium,
                color = onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
        return
    }

    val activeLogs = logs

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

    if (history.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Henüz veri yok",
                style = MaterialTheme.typography.bodyMedium,
                color = onSurface.copy(alpha = 0.5f)
            )
        }
        return
    }

    val displayHistory = remember(history) { history.takeLast(7) }

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
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor,
                            primaryColor.copy(alpha = 0.55f)
                        ),
                        startY = y,
                        endY = y + barHeight
                    ),
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
            val valueStr = String.format(Locale.getDefault(), "%.1fsa", sotHours)
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
