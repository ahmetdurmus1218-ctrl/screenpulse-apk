package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.database.UsageHistoryEntity
import com.example.ui.components.BatteryDrainChart
import com.example.ui.components.ScreenOnOffRing
import com.example.ui.components.UsageBarChart
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.ScreenPulseViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AnalyticsChartsScreen(
    viewModel: ScreenPulseViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) } // 0 = Hourly, 1 = Daily, 2 = Weekly, 3 = Monthly
    val tabLabels = listOf("Saat", "Gün", "Hafta", "Ay")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                )
            )
    ) {
        when (val state = uiState) {
            is MainUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is MainUiState.Success -> {
                if (!state.hasPermission) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Analizleri görüntülemek için izin verin.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)
                    ) {
                        // Title
                        item {
                            Text(
                                text = "Analiz Raporları",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Usage Period Tabs & Bar Chart
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(32.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.Timer,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Ekran Süresi Örüntüleri",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Segmented pill control with a glowing selected state
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .padding(4.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        tabLabels.forEachIndexed { index, label ->
                                            val selected = selectedTab == index
                                            val bgColor by androidx.compose.animation.animateColorAsState(
                                                targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                label = "tabBg"
                                            )
                                            val textColor by androidx.compose.animation.animateColorAsState(
                                                targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                label = "tabText"
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(bgColor)
                                                    .clickable { selectedTab = index }
                                                    .padding(vertical = 10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = textColor
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))

                                    // Get filtered chart items based on tab
                                    val filteredHistory = remember(selectedTab, state.usageHistory) {
                                        getChartDataForPeriod(selectedTab, state.usageHistory, state.hourlyBuckets)
                                    }

                                    UsageBarChart(
                                        history = filteredHistory,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                    )
                                }
                            }
                        }

                        // Battery Drain Chart
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(32.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.BatteryAlert,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Pil Tüketim Eğrisi (24s)",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(20.dp))

                                    BatteryDrainChart(
                                        logs = state.batteryLogs,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                    )
                                }
                            }
                        }

                        // Screen balance donut
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(32.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ScreenOnOffRing(
                                        screenOnMs = state.screenOnTimeMs,
                                        screenOffMs = state.screenOffTimeMs,
                                        modifier = Modifier.size(110.dp)
                                    )

                                    Spacer(modifier = Modifier.width(20.dp))

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Outlined.PieChart,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Kullanım Oranı",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        val total = (state.screenOnTimeMs + state.screenOffTimeMs).toFloat()
                                        val onPct = if (total > 0) (state.screenOnTimeMs / total) * 100 else 0f
                                        val offPct = if (total > 0) (state.screenOffTimeMs / total) * 100 else 0f

                                        Text(
                                            text = String.format(Locale.getDefault(), "Ekran Açık: %.1f%%", onPct),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = String.format(Locale.getDefault(), "Bekleme/Kapalı: %.1f%%", offPct),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Most Used Apps Leaderboard
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(32.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.TrendingUp,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "En Çok Kullanılan Uygulamalar",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    val topApps = state.appUsageList.sortedByDescending { it.screenTimeSinceChargeMs }.take(5)
                                    if (topApps.isEmpty()) {
                                        Text(
                                            text = "Henüz hiçbir uygulama için ekran süresi kaydedilmedi.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                                        )
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                            topApps.forEach { app ->
                                                AppProgressRow(app = app)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppProgressRow(app: com.example.data.model.AppUsageItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (app.icon != null) {
                val bitmap = remember(app.icon) {
                    app.icon.toBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
                }
                Image(
                    bitmap = bitmap,
                    contentDescription = app.appName,
                    modifier = Modifier.size(26.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatTime(app.screenTimeSinceChargeMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { (app.percentageOfTotal / 100).toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.secondaryContainer
            )
        }
    }
}

fun getChartDataForPeriod(
    periodIndex: Int,
    history: List<UsageHistoryEntity>,
    hourlyBuckets: List<Long> = List(6) { 0L }
): List<UsageHistoryEntity> {
    if (history.isEmpty() && periodIndex != 0) return emptyList()
    val sdfOutput = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    return when (periodIndex) {
        0 -> { // Hourly: real per-4h-window UsageStatsManager measurements for today
            val segments = listOf("00-04", "04-08", "08-12", "12-16", "16-20", "20-24")
            segments.mapIndexed { index, seg ->
                val blockSot = hourlyBuckets.getOrElse(index) { 0L }
                UsageHistoryEntity(
                    date = seg,
                    screenOnTimeMs = blockSot,
                    screenOffTimeMs = (4 * 3600 * 1000L - blockSot).coerceAtLeast(0L),
                    batteryUsedPct = -1, // not tracked at hourly resolution
                    totalTimeSinceChargeMs = 4 * 3600 * 1000L
                )
            }
        }
        1 -> { // Daily (show past 7 days)
            history.takeLast(7)
        }
        2 -> { // Weekly (aggregate days into week groups)
            val chunked = history.chunked(7)
            chunked.mapIndexed { index, list ->
                val weekSot = list.sumOf { it.screenOnTimeMs }
                val weekSoff = list.sumOf { it.screenOffTimeMs }
                UsageHistoryEntity(
                    date = "Hf ${index + 1}",
                    screenOnTimeMs = weekSot,
                    screenOffTimeMs = weekSoff,
                    batteryUsedPct = list.sumOf { it.batteryUsedPct },
                    totalTimeSinceChargeMs = list.sumOf { it.totalTimeSinceChargeMs }
                )
            }
        }
        3 -> { // Monthly: real aggregation of actual stored daily records, grouped by calendar month
            val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val monthLabelFormat = SimpleDateFormat("MMM", Locale("tr"))
            val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            history
                .mapNotNull { entry ->
                    val date = try { dayFormat.parse(entry.date) } catch (e: Exception) { null }
                    if (date != null) date to entry else null
                }
                .groupBy { (date, _) -> monthKeyFormat.format(date) }
                .toSortedMap()
                .map { (monthKey, entries) ->
                    val label = entries.first().first.let { monthLabelFormat.format(it) }
                        .replaceFirstChar { it.uppercase() }
                    UsageHistoryEntity(
                        date = label,
                        screenOnTimeMs = entries.sumOf { it.second.screenOnTimeMs },
                        screenOffTimeMs = entries.sumOf { it.second.screenOffTimeMs },
                        batteryUsedPct = -1,
                        totalTimeSinceChargeMs = entries.sumOf { it.second.totalTimeSinceChargeMs }
                    )
                }
        }
        else -> history
    }
}
