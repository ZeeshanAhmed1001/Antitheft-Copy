package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.SecurityLogEntry
import com.example.data.SecurityPreferences
import com.example.service.TheftProtectionService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = SecurityPreferences(context)
            if (prefs.isArmed.value) {
                val serviceIntent = Intent(context, TheftProtectionService::class.java).apply {
                    this.action = TheftProtectionService.ACTION_ARM_SERVICE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                prefs.addLog("Auto-Start on Boot", "Anti-theft service resurrected automatically after boot.", SecurityLogEntry.LogType.INFO)
            }
        }
    }
}
