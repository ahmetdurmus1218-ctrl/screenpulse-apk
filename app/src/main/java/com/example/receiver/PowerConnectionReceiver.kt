package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.worker.BatteryStateWorker

/**
 * ACTION_POWER_CONNECTED / ACTION_POWER_DISCONNECTED are on Android's short list of
 * implicit broadcasts exempted from the Android 8+ background-broadcast restrictions
 * (like BOOT_COMPLETED), so this manifest-registered receiver fires immediately —
 * even if the app isn't running — the instant the cable is plugged in or pulled out.
 *
 * Without this, the only battery sample near a charge event was whatever
 * BatteryStateWorker's own ~15-minute periodic tick happened to catch, e.g. reading
 * 97% instead of the real 100% peak, or reading 100% a few minutes late after unplug.
 * This enqueues an immediate one-off run of the same worker (which both logs the
 * current % and updates the unplug/plug-in transition), so the battery curve always
 * starts right at the real value the moment you unplug/plug in — not just up to 15
 * minutes later. The regular 15-min periodic worker keeps sampling after that as before.
 */
class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_POWER_CONNECTED &&
            intent.action != Intent.ACTION_POWER_DISCONNECTED
        ) {
            return
        }
        val request = OneTimeWorkRequestBuilder<BatteryStateWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "battery_state_immediate",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
