package com.examshield.ai.data.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.examshield.ai.domain.model.DetectedObject
import com.examshield.ai.domain.repository.Scanner
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class ClassicBluetoothScannerImpl @Inject constructor(
    private val context: Context
) : Scanner {

    private val bluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }

    @SuppressLint("MissingPermission")
    override fun startScanning(): Flow<DetectedObject> = callbackFlow {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            // Do not throw exception, just close the flow so it doesn't crash the app
            close()
            return@callbackFlow
        }

        val discoveryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == BluetoothDevice.ACTION_FOUND) {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val rssi: Int = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, BluetoothDevice.ERROR.toShort()).toInt()
                    
                    if (device != null) {
                        val detectedObj = DetectedObject(
                            macAddress = device.address,
                            name = device.name,
                            signalStrengthRssi = rssi,
                            isWifi = false,
                            isBle = false,
                            isClassicBluetooth = true,
                            timestampMs = System.currentTimeMillis()
                        )
                        trySend(detectedObj)
                    }
                }
            }
        }

        val intentFilter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(discoveryReceiver, intentFilter)

        bluetoothAdapter?.startDiscovery()

        awaitClose {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter?.cancelDiscovery()
            }
            context.unregisterReceiver(discoveryReceiver)
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopScanning() {
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter?.cancelDiscovery()
        }
    }
}
