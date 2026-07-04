package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Outlined.BatteryStd, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("Pil Bilgisi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onRefresh) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Yenile")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${state.batteryInfo.percentage}%",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (state.batteryInfo.isCharging) Color(0xFF1B4332) else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = state.batteryInfo.chargingStatus,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (state.batteryInfo.isCharging) Color(0xFF7CE0A6) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp))

        GridBatteryMetrics(state.batteryInfo)
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
                if (info.cycleCount >= 0) "${info.cycleCount}" else "Desteklenmiyor",
                Icons.Outlined.Cached,
                Modifier.weight(1f)
            )
            val dateStr = if (info.lastChargeTimeMs > 0) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(info.lastChargeTimeMs))
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
