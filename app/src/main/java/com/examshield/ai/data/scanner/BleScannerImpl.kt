package com.examshield.ai.data.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import com.examshield.ai.domain.model.DetectedObject
import com.examshield.ai.domain.repository.Scanner
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class BleScannerImpl @Inject constructor(
    private val context: Context
) : Scanner {

    private val bluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    private val bleScanner by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    private var currentIntensity = com.examshield.ai.domain.repository.ScanIntensity.BALANCED
    private var activeCallback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    override fun updateScanIntensity(intensity: com.examshield.ai.domain.repository.ScanIntensity) {
        if (currentIntensity == intensity) return
        currentIntensity = intensity
        
        // Restart scan with new settings if active
        activeCallback?.let { callback ->
            bleScanner?.stopScan(callback)
            val settings = android.bluetooth.le.ScanSettings.Builder()
                .setScanMode(when(intensity) {
                    com.examshield.ai.domain.repository.ScanIntensity.LOW_POWER -> android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_POWER
                    com.examshield.ai.domain.repository.ScanIntensity.BALANCED -> android.bluetooth.le.ScanSettings.SCAN_MODE_BALANCED
                    com.examshield.ai.domain.repository.ScanIntensity.HIGH_PRECISION -> android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY
                    com.examshield.ai.domain.repository.ScanIntensity.ULTRA_FAST -> android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY
                })
                .build()
            bleScanner?.startScan(null, settings, callback)
        }
    }

    private var callbackCountResetTime = System.currentTimeMillis()
    private var callbackCounter = 0

    @SuppressLint("MissingPermission")
    override fun startScanning(): Flow<DetectedObject> = callbackFlow {
        if (bleScanner == null || bluetoothAdapter?.isEnabled == false) {
            android.util.Log.e("ASTRA_BLE", "BLE Adapter disabled or scanner null")
            close()
            return@callbackFlow
        }

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                
                callbackCounter++
                val now = System.currentTimeMillis()
                if (now - callbackCountResetTime >= 1000) {
                    android.util.Log.d("ASTRA_BLE", "BLE Callbacks/sec: $callbackCounter")
                    callbackCounter = 0
                    callbackCountResetTime = now
                }

                val deviceName = result.device.name ?: result.scanRecord?.deviceName
                val macAddress = result.device.address
                val rssi = result.rssi

                val serviceUuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList<String>()

                val detectedObj = DetectedObject(
                    macAddress = macAddress,
                    name = deviceName,
                    signalStrengthRssi = rssi,
                    isWifi = false,
                    isBle = true,
                    isClassicBluetooth = false, 
                    rawData = result.scanRecord?.bytes,
                    extraMetadata = mapOf(
                        "txPower" to (result.scanRecord?.txPowerLevel ?: -1),
                        "isConnectable" to result.isConnectable,
                        "serviceUuids" to serviceUuids,
                        "scan_mode" to currentIntensity.name,
                        "sensorUpdateRate" to 1000/Math.max(1, callbackCounter) // Placeholder
                    ),
                    timestampMs = System.currentTimeMillis()
                )

                trySend(detectedObj)
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                android.util.Log.e("ASTRA_BLE", "Scan failed: $errorCode")
                close()
            }
        }

        activeCallback = scanCallback
        
        val settings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY) 
            .setCallbackType(android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(android.bluetooth.le.ScanSettings.MATCH_MODE_AGGRESSIVE)
            .build()
            
        try {
            bleScanner?.startScan(null, settings, scanCallback)
            android.util.Log.d("ASTRA_BLE", "Scan started in LOW_LATENCY mode")
        } catch (e: Exception) {
            android.util.Log.e("ASTRA_BLE", "Scan failed to start: ${e.message}")
            close()
        }

        awaitClose {
            try {
                bleScanner?.stopScan(scanCallback)
            } catch (e: Exception) {}
            activeCallback = null
        }
    }

    override fun stopScanning() {
        // Intentionally left blank as awaitClose handles cleanup
    }
}
