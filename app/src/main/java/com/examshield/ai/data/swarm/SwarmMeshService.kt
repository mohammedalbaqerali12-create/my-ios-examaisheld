package com.examshield.ai.data.swarm

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.examshield.ai.domain.model.ClassificationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SwarmMeshService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isMeshActive = false
    private var receiveJob: Job? = null
    private var broadcastJob: Job? = null
    private val PORT = 44556 // Cyber-secure port
    private var socket: DatagramSocket? = null

    // Stream for external components to listen to incoming Swarm Intel
    private val _swarmIntelStream = MutableSharedFlow<SwarmIntel>()
    val swarmIntelStream: SharedFlow<SwarmIntel> = _swarmIntelStream

    fun startSwarm() {
        if (isMeshActive) return
        isMeshActive = true
        Log.d("SwarmMesh", "SWARM INTELLIGENCE MESH: INITIALIZING")

        try {
            socket = DatagramSocket(PORT).apply {
                broadcast = true
            }
            startReceiver()
        } catch (e: Exception) {
            Log.e("SwarmMesh", "Failed to bind swarm socket: ${e.message}")
            isMeshActive = false
        }
    }

    private fun startReceiver() {
        receiveJob = scope.launch {
            val buffer = ByteArray(2048)
            while (isMeshActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    val jsonString = String(packet.data, 0, packet.length)
                    parseAndEmitIntel(jsonString)
                } catch (e: Exception) {
                    if (isMeshActive) {
                        Log.e("SwarmMesh", "Receiver error: ${e.message}")
                    }
                }
            }
        }
    }

    fun broadcastThreat(threat: ClassificationResult) {
        if (!isMeshActive || socket == null) return

        scope.launch {
            try {
                // Ensure only Level 4 or high confidence threats are broadcasted to prevent spam
                if (threat.confidenceScore > 80 || threat.isNexusVerified) {
                    val json = JSONObject().apply {
                        put("mac", threat.rawObject.macAddress)
                        put("rssi", threat.rawObject.signalStrengthRssi)
                        put("type", threat.deviceType.name)
                        put("confidence", threat.confidenceScore)
                    }

                    val message = json.toString().toByteArray()
                    val broadcastAddress = getBroadcastAddress()
                    
                    if (broadcastAddress != null) {
                        val packet = DatagramPacket(message, message.size, broadcastAddress, PORT)
                        socket?.send(packet)
                        Log.d("SwarmMesh", "BROADCASTED THREAT: ${threat.rawObject.macAddress}")
                    }
                }
            } catch (e: Exception) {
                 Log.e("SwarmMesh", "Broadcast error: ${e.message}")
            }
        }
    }

    private suspend fun parseAndEmitIntel(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val intel = SwarmIntel(
                macAddress = json.getString("mac"),
                rssi = json.getInt("rssi"),
                deviceType = json.getString("type"),
                confidence = json.getInt("confidence")
            )
            _swarmIntelStream.emit(intel)
            Log.d("SwarmMesh", "SWARM INTEL RECEIVED: ${intel.macAddress}")
        } catch (e: Exception) {
             Log.e("SwarmMesh", "Corrupted Swarm Data: ${e.message}")
        }
    }

    private fun getBroadcastAddress(): InetAddress? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcp = wifiManager.dhcpInfo
            val broadcast = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
            val quads = ByteArray(4)
            for (k in 0..3) {
                quads[k] = (broadcast shr k * 8 and 0xFF).toByte()
            }
            InetAddress.getByAddress(quads)
        } catch (e: Exception) {
            // Fallback to absolute local broadcast
            InetAddress.getByName("255.255.255.255")
        }
    }

    fun stopSwarm() {
        isMeshActive = false
        receiveJob?.cancel()
        broadcastJob?.cancel()
        socket?.close()
        socket = null
        Log.d("SwarmMesh", "SWARM INTELLIGENCE MESH: DEACTIVATED")
    }
}

data class SwarmIntel(
    val macAddress: String,
    val rssi: Int,
    val deviceType: String,
    val confidence: Int
)
