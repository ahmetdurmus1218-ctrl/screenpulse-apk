package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.AnalyticsChartsScreen
import com.example.ui.screens.AppUsageScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ScreenPulseViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class ScreenRoute(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("dashboard", "Özet", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    APPS("apps", "Uygulamalar", Icons.Filled.Apps, Icons.Outlined.Apps),
    CHARTS("charts", "Analiz", Icons.Filled.BarChart, Icons.Outlined.BarChart)
}

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: ScreenPulseViewModel

    // Real-time Power Unplug/Plug State Broadcast Listener
    private val powerStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val app = context.applicationContext as ScreenPulseApplication
            val repo = app.repository

            CoroutineScope(Dispatchers.IO).launch {
                if (intent.action == Intent.ACTION_POWER_DISCONNECTED) {
                    // Unplugged event - get current battery level
                    val batteryStatusIntent = context.registerReceiver(
                        null,
                        IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                    )
                    val level = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val percentage = if (level >= 0 && scale > 0) (level * 100f / scale.toFloat()).toInt() else 100
                    
                    repo.triggerUnplugEvent(percentage)
                } else if (intent.action == Intent.ACTION_POWER_CONNECTED) {
                    // Plugged event
                    repo.triggerPlugEvent()
                }
                
                // Refresh statistics immediately
                launch(Dispatchers.Main) {
                    viewModel.refreshStats()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var keepSplashOn = true
        splashScreen.setKeepOnScreenCondition { keepSplashOn }
        android.os.Handler(mainLooper).postDelayed({ keepSplashOn = false }, 650)
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            splashScreenView.view.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction { splashScreenView.remove() }
                .start()
        }

        val app = application as ScreenPulseApplication
        val factory = ScreenPulseViewModel.Factory(app.repository, app.settingsManager)
        val vm by viewModels<ScreenPulseViewModel> { factory }
        viewModel = vm

        // Register power listeners
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        // Android 13+ (API 33) requires RECEIVER_EXPORTED/RECEIVER_NOT_EXPORTED to be
        // specified explicitly, otherwise registerReceiver() throws a SecurityException
        // at runtime. This app's targetSdk is 36, so this flag is mandatory.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(powerStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(powerStateReceiver, filter)
        }

        setContent {
            val isDark by viewModel.isDarkTheme.collectAsState()
            MyApplicationTheme(darkTheme = isDark) {
                MainContainer(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Auto-refresh when returning to app (useful if permission granted in settings)
        viewModel.checkPermissionAndRefresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(powerStateReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }
    }
}

@Composable
fun MainContainer(viewModel: ScreenPulseViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val screens = listOf(ScreenRoute.DASHBOARD, ScreenRoute.APPS, ScreenRoute.CHARTS)
                    screens.forEach { screen ->
                        val selected = currentRoute == screen.route
                        val bgColor by androidx.compose.animation.animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
                            label = "navItemBg"
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(bgColor)
                                .clickable {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.title,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            if (selected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = screen.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ScreenRoute.DASHBOARD.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            composable(ScreenRoute.DASHBOARD.route) {
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToApps = {
                        navController.navigate(ScreenRoute.APPS.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(ScreenRoute.APPS.route) {
                AppUsageScreen(viewModel = viewModel)
            }
            composable(ScreenRoute.CHARTS.route) {
                AnalyticsChartsScreen(viewModel = viewModel)
            }
        }
    }
}
