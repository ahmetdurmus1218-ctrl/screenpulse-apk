package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.R
import com.example.data.model.BatteryInfo
import com.example.ui.components.ScreenOnOffRing
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.ScreenPulseViewModel
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────

@Composable
fun DashboardScreen(
    viewModel: ScreenPulseViewModel,
    onNavigateToApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val state = uiState) {
            is MainUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            }
            is MainUiState.Success -> {
                if (!state.hasPermission) {
                    PermissionOnboarding(onRequestPermission = {
                        val intent = try {
                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        } catch (e: Exception) {
                            Intent(Settings.ACTION_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        }
                        context.startActivity(intent)
                    })
                } else {
                    val isDark by viewModel.isDarkTheme.collectAsState()
                    DashboardContent(
                        state = state,
                        onRefresh = { viewModel.refreshStats() },
                        onNavigateToApps = onNavigateToApps,
                        isDarkTheme = isDark,
                        onToggleTheme = { viewModel.toggleDarkTheme() }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Permission onboarding
// ─────────────────────────────────────────────────────────────────────────

@Composable
fun PermissionOnboarding(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_permission_pulse_1783019355690),
                    contentDescription = "Kullanım İzni Görseli",
                    modifier = Modifier.size(140.dp).clip(RoundedCornerShape(28.dp))
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Kullanım Erişimi Gerekli",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "ScreenPulse; ekran açık kalma süresini, pil sağlığını ve uygulama kullanım verilerini güvenli Android sistem API'leri ile yalnızca cihazınızda takip eder. Hiçbir kişisel veri cihazınızdan dışarı çıkmaz.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = onRequestPermission,
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(imageVector = Icons.Filled.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("İzin Ver", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Main content
// ─────────────────────────────────────────────────────────────────────────

@Composable
fun DashboardContent(
    state: MainUiState.Success,
    onRefresh: () -> Unit,
    onNavigateToApps: () -> Unit,
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {}
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 10 }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 96.dp)
        ) {
            item { DashboardHeader(isDarkTheme, onToggleTheme, onRefresh) }
            item { HeroRingCard(state) }
            item { TwoStatCardsRow(state) }
            if (state.appUsageList.isNotEmpty()) {
                item { MostUsedAppsCard(state, onNavigateToApps) }
            }
            item { BatteryInfoCard(state, onRefresh) }
            item { WidgetPreviewsSection(state) }
        }
    }
}

@Composable
private fun DashboardHeader(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_widget_pulse),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "ScreenPulse",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-0.5).sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(
                icon = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                contentDescription = if (isDarkTheme) "Açık temaya geç" else "Koyu temaya geç",
                onClick = onToggleTheme
            )
            Spacer(modifier = Modifier.width(10.dp))
            RoundIconButton(
                icon = Icons.Default.Refresh,
                contentDescription = "Yenile",
                onClick = onRefresh
            )
        }
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun HeroRingCard(state: MainUiState.Success) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Son Şarjdan Beri",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(contentAlignment = Alignment.TopCenter) {
                ScreenOnOffRing(
                    screenOnMs = state.screenOnTimeMs,
                    screenOffMs = state.screenOffTimeMs,
                    modifier = Modifier.size(168.dp)
                )
                // Small sun accent, matching the reference image
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(26.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WbSunny,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HeroStat(value = "${state.batteryInfo.percentage}%", label = "Pil Seviyesi")
                HeroStat(value = formatTime(state.timeSinceLastChargeMs), label = "Şarjdan Beri")
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
        )
    }
}

@Composable
private fun TwoStatCardsRow(state: MainUiState.Success) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(
            title = "Ekran Kapalı Süresi",
            value = formatTime(state.screenOffTimeMs),
            icon = Icons.Outlined.VisibilityOff,
            modifier = Modifier.weight(1f),
            accentColor = Color(0xFF7A97FF)
        )
        StatCard(
            title = "Kilit Açma",
            value = "${state.unlockCount}",
            subtitle = "Açılış",
            icon = Icons.Outlined.LockOpen,
            modifier = Modifier.weight(1f),
            accentColor = Color(0xFFFFC857)
        )
    }
}

@Composable
private fun MostUsedAppsCard(state: MainUiState.Success, onNavigateToApps: () -> Unit) {
    DashboardSectionCard {
        Text(
            text = "En Çok Kullanılan Uygulamalar",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))

        val topApps = state.appUsageList.sortedByDescending { it.screenTimeSinceChargeMs }.take(4)
        val maxUsage = topApps.maxOfOrNull { it.screenTimeSinceChargeMs }?.coerceAtLeast(1L) ?: 1L

        topApps.forEachIndexed { index, app ->
            MostUsedAppRow(app.appName, app.icon, app.screenTimeSinceChargeMs, app.percentageOfTotal, maxUsage)
            if (index != topApps.lastIndex) Spacer(modifier = Modifier.height(14.dp))
        }

        Spacer(modifier = Modifier.height(14.dp))

        TextButton(onClick = onNavigateToApps, modifier = Modifier.fillMaxWidth()) {
            Text("Tümünü Gör")
            Spacer(modifier = Modifier.width(4.dp))
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun MostUsedAppRow(
    appName: String,
    icon: android.graphics.drawable.Drawable?,
    usageMs: Long,
    percentage: Double,
    maxUsage: Long
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                val bitmap = remember(icon) {
                    icon.toBitmap(120, 120, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
                }
                Image(bitmap = bitmap, contentDescription = appName, modifier = Modifier.size(28.dp))
            } else {
                Icon(imageVector = Icons.Default.Android, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatTime(usageMs),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = String.format(Locale.getDefault(), "Toplamın %%%.1f'i", percentage),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (usageMs.toFloat() / maxUsage.toFloat()).coerceIn(0.02f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
        }
    }
}

@Composable
private fun BatteryInfoCard(state: MainUiState.Success, onRefresh: () -> Unit) {
    DashboardSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Pil Bilgisi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            IconButton(onClick = onRefresh) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Yenile")
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            // Left: vertical battery illustration + big percentage
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VerticalBatteryIcon(
                    percentage = state.batteryInfo.percentage,
                    isCharging = state.batteryInfo.isCharging,
                    modifier = Modifier.size(width = 26.dp, height = 46.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "${state.batteryInfo.percentage}%",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = state.batteryInfo.chargingStatus,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            VerticalDivider(
                modifier = Modifier.height(72.dp).padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // Right: compact 3-row info list
            Column(
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompactStatRow("Sıcaklık", String.format(Locale.getDefault(), "%.1f°C", state.batteryInfo.temperature))
                CompactStatRow("Voltaj", String.format(Locale.getDefault(), "%.2f V", state.batteryInfo.voltage))
                CompactStatRow("Pil Sağlığı", state.batteryInfo.health)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp))

        // Secondary metrics not shown in the compact summary above
        Row(modifier = Modifier.fillMaxWidth()) {
            BatteryMetricItem(
                "Pil Tüketimi",
                "${state.batteryInfo.batteryUsedSinceCharge}%",
                Icons.Outlined.TrendingDown,
                Modifier.weight(1f)
            )
            BatteryMetricItem(
                "Şarj Döngüsü",
                when {
                    state.batteryInfo.cycleCount < 0 -> "Bekleniyor"
                    state.batteryInfo.cycleCountIsEstimate -> "~${state.batteryInfo.cycleCount}"
                    else -> "${state.batteryInfo.cycleCount}"
                },
                Icons.Outlined.Cached,
                Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            val dateStr = if (state.batteryInfo.lastUnpluggedTimeMs > 0) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(state.batteryInfo.lastUnpluggedTimeMs))
            } else "Bilinmiyor"
            BatteryMetricItem("Fişten Çekilme", dateStr, Icons.Outlined.Power, Modifier.weight(1f))
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CompactStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Realistic vertical battery illustration that fills proportionally to charge level. */
@Composable
private fun VerticalBatteryIcon(percentage: Int, isCharging: Boolean, modifier: Modifier = Modifier) {
    val fillColor = if (isCharging) Color(0xFFFFC857) else Color(0xFF00C853)
    Canvas(modifier = modifier) {
        val capHeight = size.height * 0.08f
        val bodyTop = capHeight
        val bodyHeight = size.height - capHeight
        val cornerRadius = CornerRadius(size.width * 0.25f, size.width * 0.25f)

        // Cap (the little nub on top)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.6f),
            topLeft = Offset(size.width * 0.3f, 0f),
            size = Size(size.width * 0.4f, capHeight + 2f),
            cornerRadius = CornerRadius(2f, 2f)
        )

        // Outline
        drawRoundRect(
            color = Color.White.copy(alpha = 0.5f),
            topLeft = Offset(0f, bodyTop),
            size = Size(size.width, bodyHeight),
            cornerRadius = cornerRadius,
            style = Stroke(width = 2.5f)
        )

        // Fill, from the bottom up
        val inset = 4f
        val fillHeight = (bodyHeight - inset * 2) * (percentage.coerceIn(0, 100) / 100f)
        drawRoundRect(
            color = fillColor,
            topLeft = Offset(inset, bodyTop + bodyHeight - inset - fillHeight),
            size = Size(size.width - inset * 2, fillHeight.coerceAtLeast(4f)),
            cornerRadius = CornerRadius(size.width * 0.18f, size.width * 0.18f)
        )
    }
}

/** Shared rounded card shell used by every dashboard section below the hero card. */
@Composable
private fun DashboardSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Reusable pieces
// ─────────────────────────────────────────────────────────────────────────

@Composable
fun GridBatteryMetrics(info: BatteryInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            BatteryMetricItem("Pil Tüketimi", "${info.batteryUsedSinceCharge}%", Icons.Outlined.TrendingDown, Modifier.weight(1f))
            BatteryMetricItem("Sıcaklık", String.format(Locale.getDefault(), "%.1f°C", info.temperature), Icons.Outlined.Thermostat, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            BatteryMetricItem("Voltaj", String.format(Locale.getDefault(), "%.2f V", info.voltage), Icons.Outlined.ElectricBolt, Modifier.weight(1f))
            BatteryMetricItem("Pil Sağlığı", info.health, Icons.Outlined.HealthAndSafety, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            BatteryMetricItem(
                "Şarj Döngüsü",
                when {
                    info.cycleCount < 0 -> "Bekleniyor"
                    info.cycleCountIsEstimate -> "~${info.cycleCount}"
                    else -> "${info.cycleCount}"
                },
                Icons.Outlined.Cached,
                Modifier.weight(1f)
            )
            val dateStr = if (info.lastUnpluggedTimeMs > 0) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(info.lastUnpluggedTimeMs))
            } else "Bilinmiyor"
            BatteryMetricItem("Fişten Çekilme", dateStr, Icons.Outlined.Power, Modifier.weight(1f))
        }
    }
}

@Composable
fun BatteryMetricItem(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF7A97FF),
    subtitle: String? = null
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
        }
    }
}

fun formatTime(timeMs: Long): String {
    val totalMinutes = (timeMs / 1000) / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}sa ${minutes}dk" else "${minutes}dk"
}

// ─────────────────────────────────────────────────────────────────────────
// In-app "Widget Örnekleri" preview panel — shows what the real home-screen
// widgets look like, using the same live data as the rest of the app.
// These are plain Compose mockups for preview purposes, not the actual
// AppWidget RemoteViews (those live in com.example.widget).
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun WidgetPreviewsSection(state: MainUiState.Success) {
    Column {
        Text(
            text = "Widget Örnekleri",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            item {
                WidgetPreviewFrame(label = "4x2 Widget", width = 220.dp, height = 110.dp) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Outlined.WbSunny, null, tint = Color(0xFFFFC857), modifier = Modifier.align(Alignment.Start).size(14.dp))
                            MiniRing(state, size = 62.dp)
                        }
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                            Text("%${state.batteryInfo.percentage}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Pil Seviyesi", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(formatTime(state.timeSinceLastChargeMs), color = Color(0xFF7A97FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Şarjdan Beri", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp)
                        }
                    }
                }
            }
            item {
                WidgetPreviewFrame(label = "2x2 Widget", width = 130.dp, height = 130.dp) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        MiniRing(state, size = 66.dp)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("%${state.batteryInfo.percentage}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.width(4.dp))
                            MiniBatteryIcon(state.batteryInfo.percentage)
                        }
                    }
                }
            }
            item {
                WidgetPreviewFrame(label = "2x4 Widget", width = 130.dp, height = 220.dp) {
                    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.WbSunny, null, tint = Color(0xFFFFC857), modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Son Şarjdan Beri", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(formatTime(state.screenOnTimeMs), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("Ekran Açık Süresi", color = Color(0xFF7A97FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("%${state.batteryInfo.percentage}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.width(4.dp))
                            MiniBatteryIcon(state.batteryInfo.percentage)
                        }
                        Text("Pil Seviyesi", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp)
                    }
                }
            }
            item {
                WidgetPreviewFrame(label = "1x4 Widget", width = 88.dp, height = 220.dp) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.weight(1f))
                        MiniRing(state, size = 48.dp)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("%${state.batteryInfo.percentage}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Spacer(Modifier.width(3.dp))
                            MiniBatteryIcon(state.batteryInfo.percentage, small = true)
                        }
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Outlined.Bolt, null, tint = Color(0xFFFFC857), modifier = Modifier.size(12.dp))
                        Text(
                            formatTime(state.timeSinceLastChargeMs),
                            color = Color(0xFF7A97FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                        Text("Şarjdan Beri", color = Color.White.copy(alpha = 0.5f), fontSize = 7.sp)
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPreviewFrame(
    label: String,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    content: @Composable BoxScope.() -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF181B26), Color(0xFF12141C), Color(0xFF08090D))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
            content = content
        )
        Spacer(Modifier.height(8.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MiniRing(state: MainUiState.Success, size: androidx.compose.ui.unit.Dp) {
    val total = (state.screenOnTimeMs + state.screenOffTimeMs).toFloat()
    val onPct = if (total > 0) state.screenOnTimeMs / total else 0f
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.toPx() * 0.12f
            drawArc(
                color = Color(0xFF6F98FF).copy(alpha = 0.25f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0xFF2B66FF),
                startAngle = -90f, sweepAngle = 360f * onPct, useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            formatTime(state.screenOnTimeMs),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.16f).sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MiniBatteryIcon(percentage: Int, small: Boolean = false) {
    val w = if (small) 16.dp else 20.dp
    val h = if (small) 8.dp else 10.dp
    Canvas(modifier = Modifier.size(width = w, height = h)) {
        val bodyRight = size.width - 3f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.5f),
            topLeft = Offset(0f, 0f),
            size = Size(bodyRight, size.height),
            cornerRadius = CornerRadius(2f, 2f),
            style = Stroke(width = 1.5f)
        )
        drawRoundRect(
            color = Color(0xFF00C853),
            topLeft = Offset(2f, 2f),
            size = Size((bodyRight - 4f) * (percentage / 100f), size.height - 4f),
            cornerRadius = CornerRadius(1f, 1f)
        )
    }
}
