package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BatteryInfo
import com.example.ui.components.ScreenOnOffRing
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.ScreenPulseViewModel
import java.text.SimpleDateFormat
import java.util.*

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
            .background(Color(0xFF0F131E))
    ) {
        when (val state = uiState) {
            is MainUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF4C7CE5), strokeWidth = 3.dp)
                }
            }
            is MainUiState.Success -> {
                if (!state.hasPermission) {
                    PermissionOnboarding(onRequestPermission = {
                        try {
                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                    })
                } else {
                    DashboardContent(
                        state = state,
                        onRefresh = { viewModel.refreshStats() },
                        onNavigateToApps = onNavigateToApps
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionOnboarding(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171D2C)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_permission_pulse_1783019355690),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp).clip(RoundedCornerShape(24.dp))
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Kullanım Erişimi Gerekli",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ScreenPulse; verileri güvenli Android sistem API'leri ile yalnızca cihazınızda takip eder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8A99AD),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C7CE5)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("İzin Ver", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun DashboardContent(
    state: MainUiState.Success,
    onRefresh: () -> Unit,
    onNavigateToApps: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 96.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ScreenPulse",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = Color(0xFF4C7CE5),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Son Şarjdan Beri",
                            fontSize = 13.sp,
                            color = Color(0xFF8A99AD),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF171D2C))
                        .clickable { onRefresh() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Yenile",
                        tint = Color(0xFF8A99AD),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF171D2C)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ScreenOnOffRing(
                            screenOnMs = state.screenOnTimeMs,
                            screenOffMs = state.screenOffTimeMs,
                            modifier = Modifier.size(180.dp)
                        )
                        
                        // Tamamen standart Material kütüphanesinden WbSunny ikonu
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = null,
                            tint = Color(0xFFF1C40F),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(top = 16.dp, start = 16.dp)
                                .size(20.dp)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = formatTimeShort(state.screenOnTimeMs),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = "Ekran Açık Süresi",
                                fontSize = 12.sp,
                                color = Color(0xFF8A99AD)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(text = "%${state.batteryInfo.percentage}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(text = "Pil Seviyesi", fontSize = 12.sp, color = Color(0xFF8A99AD))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = formatTimeShort(state.timeSinceLastChargeMs), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(text = "Şarjdan Beri", fontSize = 12.sp, color = Color(0xFF8A99AD))
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF171D2C))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF4C7CE5), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Ekran Kapalı", fontSize = 11.sp, color = Color(0xFF8A99AD))
                            Text(text = formatTimeShort(state.screenOffTimeMs), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF171D2C))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color(0xFFF1C40F), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Kilit Açma", fontSize = 11.sp, color = Color(0xFF8A99AD))
                            Text(text = "43 Açılış", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF171D2C))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "En Çok Kullanılan Uygulamalar",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Harici kütüphaneye bağımlı ikonları kaldırıp projenin ana görselini (R.drawable) kullandık.
                    DummyAppRow("Claude", "56dk", "%30")
                    DummyAppRow("YouTube", "22dk", "%12")
                    DummyAppRow("Chrome", "18dk", "%10")
                    DummyAppRow("WhatsApp", "5dk", "%3")

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Tümünü Gör >",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF4C7CE5),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clickable { onNavigateToApps() }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF171D2C))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BatteryStd, contentDescription = null, tint = Color(0xFF2ECC71), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Pil Bilgisi", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2ECC71).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFF2ECC71), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = "%${state.batteryInfo.percentage}", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            BatteryRow("Sıcaklık", String.format(Locale.getDefault(), "%.1f°C", state.batteryInfo.temperature))
                            BatteryRow("Voltaj", String.format(Locale.getDefault(), "%.2f V", state.batteryInfo.voltage))
                            BatteryRow("Pil Sağlığı", state.batteryInfo.health)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DummyAppRow(name: String, time: String, percentage: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // İkon yerine projede kesin olarak var olan izin görselini minik boyutta kullandık.
        Image(
            painter = painterResource(id = R.drawable.img_permission_pulse_1783019355690),
            contentDescription = null,
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(text = "Ekran süresinin $percentage'i", fontSize = 11.sp, color = Color(0xFF8A99AD))
        }
        Text(text = time, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun BatteryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, fontSize = 12.sp, color = Color(0xFF8A99AD))
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

fun formatTimeShort(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val totalMinutes = totalSeconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}s ${minutes}dk" else "${minutes}dk"
}
