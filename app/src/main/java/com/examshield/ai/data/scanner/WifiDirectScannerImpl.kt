package com.examshield.ai.data.scanner

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import com.examshield.ai.domain.model.DetectedObject
import com.examshield.ai.domain.repository.Scanner
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class WifiDirectScannerImpl @Inject constructor(
    private val context: Context
) : Scanner {

    private val wifiP2pManager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }

    private var channel: WifiP2pManager.Channel? = null

    @SuppressLint("MissingPermission")
    override fun startScanning(): Flow<DetectedObject> = callbackFlow {
        if (wifiP2pManager == null) {
            close(Exception("Wi-Fi P2P is not available on this device."))
            return@callbackFlow
        }

        channel = wifiP2pManager?.initialize(context, Looper.getMainLooper(), null)

        val peerListListener = WifiP2pManager.PeerListListener { peers ->
            peers.deviceList.forEach { device ->
                val detectedObj = DetectedObject(
                    macAddress = device.deviceAddress,
                    name = device.deviceName,
                    signalStrengthRssi = 0, // RSSI is not directly available for P2P devices in this API
                    isWifi = true, // Categorize as Wi-Fi
                    isBle = false,
                    isClassicBluetooth = false,
                    timestampMs = System.currentTimeMillis()
                )
                trySend(detectedObj)
            }
        }

        wifiP2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                // Discovery initiated. Results will be delivered to the PeerListListener.
                // We need to periodically request peers to get updates.
                // However, for simplicity here, we rely on the initial discovery trigger.
            }

            override fun onFailure(reasonCode: Int) {
                close(Exception("Failed to start Wi-Fi P2P discovery: $reasonCode"))
            }
        })

        awaitClose {
            wifiP2pManager?.stopPeerDiscovery(channel, null)
        }
    }

    override fun stopScanning() {
        wifiP2pManager?.stopPeerDiscovery(channel, null)
    }
}
