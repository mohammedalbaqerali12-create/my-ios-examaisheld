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

    @SuppressLint("MissingPermission")
    override fun startScanning(): Flow<DetectedObject> = callbackFlow {
        if (bleScanner == null || bluetoothAdapter?.isEnabled == false) {
            close()
            return@callbackFlow
        }

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                
                // Exclude randomized MACs if needed (here we keep them for ML grouping)
                val deviceName = result.device.name ?: result.scanRecord?.deviceName
                val macAddress = result.device.address
                val rssi = result.rssi
                val isConnectable = result.isConnectable

                val detectedObj = DetectedObject(
                    macAddress = macAddress,
                    name = deviceName,
                    signalStrengthRssi = rssi,
                    isWifi = false,
                    isBle = true,
                    isClassicBluetooth = false, // You'd need a separate BroadcastReceiver for Classic BT discovery
                    rawData = result.scanRecord?.bytes,
                    timestampMs = System.currentTimeMillis()
                )

                trySend(detectedObj)
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                // Just close without exception to avoid crashing the Flow
                close()
            }
        }

        bleScanner?.startScan(null, android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build(), scanCallback)

        awaitClose {
            bleScanner?.stopScan(scanCallback)
        }
    }

    override fun stopScanning() {
        // Intentionally left blank as awaitClose handles cleanup
    }
}
