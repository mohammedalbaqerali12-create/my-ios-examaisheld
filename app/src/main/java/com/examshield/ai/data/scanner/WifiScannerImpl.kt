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
                    processScanResults()
                }
            }

            private fun processScanResults() {
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
                            timestampMs = System.currentTimeMillis(),
                            extraMetadata = mapOf(
                                "frequency" to result.frequency,
                                "capabilities" to result.capabilities,
                                "centerFreq0" to result.centerFreq0,
                                "centerFreq1" to result.centerFreq1,
                                "channelWidth" to result.channelWidth,
                                "is80211mc" to (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) result.is80211mcResponder else false),
                                "isPasspoint" to (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) result.isPasspointNetwork else false)
                            )
                        )
                        trySend(detectedObj)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WIFI_SCANNER", "Error processing scan results: ${e.message}")
                }
            }
        }

        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(wifiScanReceiver, intentFilter)
        
        // --- OVERDRIVE EXPLORATION LOOP ---
        val scannerJob = launch {
            while (isScanningActive) {
                try {
                    val success = wifiManager.startScan()
                    if (!success) {
                        // Throttled: Force emission of last known results from cache
                        val results = wifiManager.scanResults
                        for (result in results) {
                            trySend(DetectedObject(
                                macAddress = result.BSSID ?: "UNKNOWN_MAC",
                                name = result.SSID?.ifEmpty { "Cached: ${result.BSSID}" } ?: "Cached: ${result.BSSID}",
                                signalStrengthRssi = result.level,
                                isWifi = true,
                                timestampMs = System.currentTimeMillis()
                            ))
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WIFI_SCANNER", "StartScan failed: ${e.message}")
                }
                
                // Android Pie+ throttling limit: 4 scans every 120 seconds for foreground
                // We pulse every 10 seconds to stay within a reasonable range while maintaining "High Sensitivity"
                delay(10_000) 
            }
        }

        awaitClose {
            isScanningActive = false
            scannerJob.cancel()
            try {
                context.unregisterReceiver(wifiScanReceiver)
            } catch (e: Exception) {}
        }
    }

    override fun stopScanning() {
        isScanningActive = false
    }
}
