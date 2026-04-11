package com.examshield.ai.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.examshield.ai.service.AstraNexusService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            
            val prefs = context.getSharedPreferences("shield_prefs", Context.MODE_PRIVATE)
            val isPermanentBackgroundEnabled = prefs.getBoolean("permanent_background", false)
            
            if (isPermanentBackgroundEnabled) {
                AstraNexusService.start(context)
            }
        }
    }
}
