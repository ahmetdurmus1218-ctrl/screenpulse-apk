package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ScreenPulseApplication
import com.example.notification.LockScreenNotificationController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        // Only restart if the person had actually chosen "Sürekli Açık" or "Periyodik" —
        // a reboot shouldn't turn the feature on for someone who left it "Kapalı".
        val app = context.applicationContext as ScreenPulseApplication
        CoroutineScope(Dispatchers.IO).launch {
            val mode = app.settingsManager.lockScreenNotificationMode.first()
            if (mode != "off") {
                LockScreenNotificationController.applyMode(context, mode)
            }
        }
    }
}
