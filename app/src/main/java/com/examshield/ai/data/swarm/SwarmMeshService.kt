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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val PORT = 44556
    private var socket: DatagramSocket? = null

    private val _swarmIntelStream = MutableSharedFlow<SwarmMessage>()
    val swarmIntelStream: SharedFlow<SwarmMessage> = _swarmIntelStream

    private val _activeNodes = MutableStateFlow<Set<String>>(emptySet())
    val activeNodes: StateFlow<Set<String>> = _activeNodes.asStateFlow()

    fun startSwarm() {
        if (isMeshActive) return
        isMeshActive = true
        Log.d("SwarmMesh", "SWARM INTELLIGENCE MESH: INITIALIZING")

        try {
            socket = DatagramSocket(PORT).apply {
                broadcast = true
            }
            startReceiver()
            startStatusHeartbeat()
        } catch (e: Exception) {
            Log.e("SwarmMesh", "Failed to bind swarm socket: ${e.message}")
            isMeshActive = false
        }
    }

    private fun startStatusHeartbeat() {
        broadcastJob = scope.launch {
            while (isMeshActive) {
                broadcastStatus()
                delay(3000)
            }
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

    fun broadcastStatus() {
        if (!isMeshActive || socket == null) return
        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("type", "NODE_STATUS")
                    put("nodeId", android.os.Build.MODEL)
                }
                sendPacket(json.toString())
            } catch (e: Exception) {
                Log.e("SwarmMesh", "Status Broadcast Error: ${e.message}")
            }
        }
    }

    fun broadcastThreat(threat: ClassificationResult, azimuth: Float = 0f, lat: Double = 0.0, lng: Double = 0.0) {
        if (!isMeshActive || socket == null) return
        scope.launch {
            try {
                if (threat.confidenceScore > 80 || threat.isNexusVerified) {
                    val json = JSONObject().apply {
                        put("type", "THREAT_INTEL")
                        put("mac", threat.rawObject.macAddress)
                        put("rssi", threat.rawObject.signalStrengthRssi)
                        put("deviceType", threat.deviceType.name)
                        put("confidence", threat.confidenceScore)
                        put("sourceNode", android.os.Build.MODEL)
                        put("azimuth", azimuth)
                        put("lat", lat)
                        put("lng", lng)
                    }
                    sendPacket(json.toString())
                }
            } catch (e: Exception) {
                 Log.e("SwarmMesh", "Broadcast error: ${e.message}")
            }
        }
    }

    private fun sendPacket(message: String) {
        try {
            val bytes = message.toByteArray()
            val address = getBroadcastAddress()
            if (address != null) {
                val packet = DatagramPacket(bytes, bytes.size, address, PORT)
                socket?.send(packet)
            }
        } catch (e: Exception) {
            Log.e("SwarmMesh", "Send failed: ${e.message}")
        }
    }

    private suspend fun parseAndEmitIntel(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val type = json.optString("type")
            
            when(type) {
                "NODE_STATUS" -> {
                    val nodeId = json.getString("nodeId")
                    if (nodeId != android.os.Build.MODEL) {
                        _activeNodes.value = _activeNodes.value + nodeId
                    }
                    _swarmIntelStream.emit(SwarmMessage.NodeStatus(nodeId))
                }
                "THREAT_INTEL" -> {
                    val intel = SwarmMessage.ThreatIntel(
                        macAddress = json.getString("mac"),
                        rssi = json.getInt("rssi"),
                        deviceType = json.getString("deviceType"),
                        confidence = json.getInt("confidence"),
                        sourceNode = json.getString("sourceNode"),
                        azimuth = json.optDouble("azimuth", 0.0).toFloat(),
                        latitude = json.optDouble("lat", 0.0),
                        longitude = json.optDouble("lng", 0.0)
                    )
                    _swarmIntelStream.emit(intel)
                }
            }
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
            InetAddress.getByName("255.255.255.255")
        }
    }

    fun stopSwarm() {
        isMeshActive = false
        receiveJob?.cancel()
        broadcastJob?.cancel()
        socket?.close()
        socket = null
        _activeNodes.value = emptySet()
        Log.d("SwarmMesh", "SWARM INTELLIGENCE MESH: DEACTIVATED")
    }
}

sealed class SwarmMessage {
    data class NodeStatus(val nodeId: String) : SwarmMessage()
    data class ThreatIntel(
        val macAddress: String,
        val rssi: Int,
        val deviceType: String,
        val confidence: Int,
        val sourceNode: String,
        val azimuth: Float = 0f,
        val latitude: Double = 0.0,
        val longitude: Double = 0.0
    ) : SwarmMessage()
}
