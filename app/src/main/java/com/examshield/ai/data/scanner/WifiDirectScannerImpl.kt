package com.examshield.ai.data.scanner

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    private var p2pChannel: WifiP2pManager.Channel? = null

    @SuppressLint("MissingPermission")
    override fun startScanning(): Flow<DetectedObject> = callbackFlow {
        val p2pManager = wifiP2pManager ?: run {
            close()
            return@callbackFlow
        }

        // Initialize the channel
        val channel = p2pManager.initialize(context, Looper.getMainLooper(), null)
        if (channel == null) {
            close()
            return@callbackFlow
        }
        p2pChannel = channel

        val peerListListener = WifiP2pManager.PeerListListener { peers ->
            peers.deviceList.forEach { device ->
                val detectedObj = DetectedObject(
                    macAddress = device.deviceAddress ?: "UNKNOWN_P2P_MAC",
                    name = device.deviceName ?: "Unknown P2P Device",
                    signalStrengthRssi = -1, // RSSI not available for P2P
                    isWifi = true, // Categorize as Wi-Fi
                    isBle = false,
                    isClassicBluetooth = false,
                    timestampMs = System.currentTimeMillis()
                )
                trySend(detectedObj)
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        p2pManager.requestPeers(channel, peerListListener)
                    }
                }
            }
        }

        val intentFilter = IntentFilter(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        context.registerReceiver(receiver, intentFilter)

        p2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { /* Discovery initiated */ }
            override fun onFailure(reasonCode: Int) {
                // Don't close the flow, just log the error or handle it.
                // Log.e("WifiDirectScanner", "Failed to start discovery: $reasonCode")
            }
        })

        awaitClose {
            p2pManager.stopPeerDiscovery(channel, null)
            context.unregisterReceiver(receiver)
        }
    }

    override fun stopScanning() {
        p2pChannel?.let { wifiP2pManager?.stopPeerDiscovery(it, null) }
    }
}
