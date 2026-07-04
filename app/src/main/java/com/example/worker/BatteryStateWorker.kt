package com.example.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ScreenPulseApplication
import kotlinx.coroutines.flow.first

/**
 * Runs periodically (every ~15 minutes, the platform-enforced minimum for periodic work)
 * regardless of whether the app's Activity is open or not.
 *
 * Why this exists: the previous approach only detected "unplugged from charger" via a
 * BroadcastReceiver registered inside MainActivity, which only ever fires while the app
 * happens to be in memory. In practice most people unplug their phone with the app
 * closed, so the recorded "last unplugged at / battery %" baseline could go stale for
 * hours or days — which is exactly why "Pil Tüketimi" could show a nonsensical 0% and
 * "Fişten Çekilme" could show a stale/misleading time.
 *
 * This worker reads the battery's own sticky broadcast (works without a live receiver)
 * and compares the current charging state to the last known state. If it flipped from
 * charging -> discharging, that's a real unplug event, captured within ~15 minutes of
 * it actually happening — far more reliable than depending on the app being open.
 */
class BatteryStateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as ScreenPulseApplication
            val settingsManager = app.settingsManager

            val batteryStatusIntent = applicationContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val status = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isChargingNow = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            val level = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val percentage = if (level >= 0 && scale > 0) (level * 100f / scale.toFloat()).toInt() else -1

            val wasCharging = settingsManager.wasCharging.first()

            if (wasCharging && !isChargingNow && percentage >= 0) {
                // Real unplug transition detected in the background.
                settingsManager.saveUnpluggedState(System.currentTimeMillis(), percentage)
            } else if (!wasCharging && isChargingNow) {
                // Real plug-in transition detected in the background.
                settingsManager.saveLastChargeTime(System.currentTimeMillis())
            }

            settingsManager.setWasCharging(isChargingNow)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
