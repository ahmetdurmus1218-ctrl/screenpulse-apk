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
import kotlinx.coroutines.launch
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

    var selectedRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var selectedRangeApps by remember { mutableStateOf<List<com.example.data.model.AppUsageItem>>(emptyList()) }
    var isLoadingRangeApps by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

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
                                            .height(180.dp),
                                        onRangeSelected = { start, end ->
                                            selectedRange = start to end
                                            isLoadingRangeApps = true
                                            coroutineScope.launch {
                                                selectedRangeApps = viewModel.getAppUsageForRange(start, end)
                                                isLoadingRangeApps = false
                                            }
                                        },
                                        onSelectionCleared = {
                                            selectedRange = null
                                            selectedRangeApps = emptyList()
                                        }
                                    )

                                    if (selectedRange == null) {
                                        Text(
                                            text = "İpucu: bir aralığı parmağınızla sürükleyip seçerek o saatlerde hangi uygulamaların kullanıldığını görebilirsiniz",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(top = 10.dp)
                                        )
                                    } else {
                                        val range = selectedRange!!
                                        val rangeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

                                        // Real measured battery % drop within this exact window — linearly
                                        // interpolated between the surrounding log points (not snapped to
                                        // "nearest point before", which could return 0% for narrow windows
                                        // that fall entirely between two sparse data points).
                                        val logsInOrder = state.batteryLogs.sortedBy { it.timestamp }
                                        fun batteryAt(t: Long): Int? {
                                            if (logsInOrder.isEmpty()) return null
                                            val before = logsInOrder.lastOrNull { it.timestamp <= t }
                                            val after = logsInOrder.firstOrNull { it.timestamp >= t }
                                            return when {
                                                before != null && after != null && before.timestamp != after.timestamp -> {
                                                    val frac = (t - before.timestamp).toFloat() / (after.timestamp - before.timestamp)
                                                    (before.batteryLevel + ((after.batteryLevel - before.batteryLevel) * frac)).toInt()
                                                }
                                                before != null -> before.batteryLevel
                                                after != null -> after.batteryLevel
                                                else -> null
                                            }
                                        }
                                        val batteryAtStart = batteryAt(range.first)
                                        val batteryAtEnd = batteryAt(range.second)
                                        val realDropPct = if (batteryAtStart != null && batteryAtEnd != null) {
                                            (batteryAtStart - batteryAtEnd).coerceAtLeast(0)
                                        } else null
                                        val totalActiveMs = selectedRangeApps.sumOf { it.foregroundTimeMs + it.backgroundTimeMs }

                                        Spacer(modifier = Modifier.height(14.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${rangeFormat.format(Date(range.first))} – ${rangeFormat.format(Date(range.second))} arasında en çok kullanılanlar",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Temizle",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.clickable {
                                                    selectedRange = null
                                                    selectedRangeApps = emptyList()
                                                }
                                            )
                                        }
                                        if (realDropPct != null) {
                                            Text(
                                                text = "Bu aralıkta pil %$realDropPct düştü — aşağıdaki dağılım, her uygulamanın bu düşüşteki tahmini payı",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))

                                        if (isLoadingRangeApps) {
                                            Text(
                                                text = "Yükleniyor…",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else if (selectedRangeApps.isEmpty()) {
                                            Text(
                                                text = "Bu aralıkta kayıtlı kullanım bulunamadı",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                selectedRangeApps.take(5).forEach { app ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        if (app.icon != null) {
                                                            Image(
                                                                bitmap = app.icon.toBitmap().asImageBitmap(),
                                                                contentDescription = null,
                                                                modifier = Modifier
                                                                    .size(28.dp)
                                                                    .clip(RoundedCornerShape(10.dp))
                                                            )
                                                        } else {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(28.dp)
                                                                    .clip(RoundedCornerShape(10.dp))
                                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = app.appName,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            if (realDropPct != null && realDropPct > 0 && totalActiveMs > 0) {
                                                                val appActiveMs = app.foregroundTimeMs + app.backgroundTimeMs
                                                                val estPct = (realDropPct * (appActiveMs.toDouble() / totalActiveMs))
                                                                Text(
                                                                    text = "~%.1f%% pil".format(estPct),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = Color(0xFFFF7043)
                                                                )
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        val mins = (app.foregroundTimeMs + app.backgroundTimeMs) / 60000
                                                        val label = if (mins >= 60) {
                                                            "${mins / 60}sa ${mins % 60}dk"
                                                        } else {
                                                            "${mins}dk"
                                                        }
                                                        Text(
                                                            text = label,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
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
                .clip(RoundedCornerShape(11.dp))
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
    // Daily/Weekly (1, 2) build their own calendar-based skeleton below and handle an
    // empty history list fine (all-zero bars); only Monthly (3) and the default case
    // have nothing meaningful to show with no data at all.
    if (history.isEmpty() && periodIndex != 0 && periodIndex != 1 && periodIndex != 2) return emptyList()
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
        1 -> { // Daily (real last 7 calendar days, gaps filled with 0 — NOT just the
            // last 7 rows in the table, which skews the whole window and can repeat
            // a weekday when a day's record is missing, e.g. from populateHistoryIfEmpty()
            // skipping days with zero measured screen-on time).
            val byDate = history.associateBy { it.date }
            val cal = Calendar.getInstance()
            (6 downTo 0).map { daysAgo ->
                cal.time = Date()
                cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
                val dateStr = sdfOutput.format(cal.time)
                byDate[dateStr] ?: UsageHistoryEntity(
                    date = dateStr,
                    screenOnTimeMs = 0L,
                    screenOffTimeMs = 0L,
                    batteryUsedPct = -1,
                    totalTimeSinceChargeMs = 0L
                )
            }
        }
        2 -> { // Weekly (real calendar weeks, Monday-start, gaps filled with 0 — same
            // fix as Daily: grouping raw table rows by chunked(7) shifted whole weeks
            // whenever a day was missing from the table).
            val byDate = history.associateBy { it.date }
            val today = Calendar.getInstance()
            val currentWeekStart = Calendar.getInstance().apply {
                time = today.time
                val dow = get(Calendar.DAY_OF_WEEK) // Sunday=1 ... Saturday=7
                val daysSinceMonday = (dow + 5) % 7 // Monday=0 ... Sunday=6
                add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            (3 downTo 0).map { weeksAgo ->
                val weekStart = (currentWeekStart.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, -weeksAgo * 7)
                }
                val weekDates = (0..6).map { offset ->
                    val d = (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
                    sdfOutput.format(d.time)
                }
                val weekEntries = weekDates.mapNotNull { byDate[it] }
                UsageHistoryEntity(
                    date = "Hf ${4 - weeksAgo}",
                    screenOnTimeMs = weekEntries.sumOf { it.screenOnTimeMs },
                    screenOffTimeMs = weekEntries.sumOf { it.screenOffTimeMs },
                    batteryUsedPct = weekEntries.sumOf { it.batteryUsedPct.coerceAtLeast(0) },
                    totalTimeSinceChargeMs = weekEntries.sumOf { it.totalTimeSinceChargeMs }
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
