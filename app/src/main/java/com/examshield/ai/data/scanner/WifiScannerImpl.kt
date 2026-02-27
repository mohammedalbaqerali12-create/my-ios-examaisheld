package com.examshield.ai.data.scanner

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import com.examshield.ai.domain.model.DetectedObject
import com.examshield.ai.domain.repository.Scanner
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class WifiScannerImpl @Inject constructor(
    private val context: Context
) : Scanner {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var isScanningActive = false

    @SuppressLint("MissingPermission")
    override fun startScanning(): Flow<DetectedObject> = callbackFlow {
        isScanningActive = true
        
        val wifiScanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                    if (success) {
                        try {
                            val results = wifiManager.scanResults
                            for (result in results) {
                                val detectedObj = DetectedObject(
                                    macAddress = result.BSSID ?: "UNKNOWN_MAC",
                                    name = result.SSID?.ifEmpty { "Hidden Network" } ?: "Hidden Network",
                                    signalStrengthRssi = result.level,
                                    isWifi = true,
                                    isBle = false,
                                    isClassicBluetooth = false,
                                    timestampMs = System.currentTimeMillis()
                                )
                                trySend(detectedObj)
                            }
                        } catch (e: SecurityException) {
                            // Location permission might be missing or Wi-Fi disabled
                        }
                    }
                    // Trigger another scan after a delay to respect Android OS limits
                    // (Android 9+ limits Wi-Fi scans to 4 per 2 minutes for foreground apps)
                    if (isScanningActive) {
                        launch {
                            delay(2_000)
                            try {
                                wifiManager.startScan()
                            } catch (e: Exception) {}
                        }
                    }
                }
            }
        }

        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(wifiScanReceiver, intentFilter)
        
        // Trigger first Wi-Fi scan
        try {
            wifiManager.startScan()
        } catch (e: Exception) {}

        awaitClose {
            isScanningActive = false
            try {
                context.unregisterReceiver(wifiScanReceiver)
            } catch (e: Exception) {
                // Ignore if not registered
            }
        }
    }

    override fun stopScanning() {
        isScanningActive = false
    }
}
