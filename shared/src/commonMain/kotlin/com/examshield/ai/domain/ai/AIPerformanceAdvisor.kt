package com.examshield.ai.domain.ai

import com.examshield.ai.domain.model.ClassificationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AIPerformanceAdvisor {

    data class SystemHealth(
        val refreshRate: Int = 0,
        val callbackFrequency: Int = 0,
        val isBluetoothEnabled: Boolean = true,
        val isPipelineFrozen: Boolean = false,
        val batteryOptimized: Boolean = false,
        val rssiStability: Double = 0.0,
        val advice: List<String> = emptyList()
    )

    private val _healthState = MutableStateFlow(SystemHealth())
    val healthState: StateFlow<SystemHealth> = _healthState.asStateFlow()

    fun updateMetrics(
        refreshRate: Int,
        callbackFrequency: Int,
        bluetoothEnabled: Boolean,
        rssiVariance: Double
    ) {
        val advice = mutableListOf<String>()
        
        if (refreshRate == 0) {
            advice.add("Check ScanMode: Pipeline appears frozen (0Hz).")
            advice.add("Restart Bluetooth: Adaptive driver might be stuck.")
        }
        
        if (callbackFrequency < 2) {
            advice.add("Increase Scan Priority: Low callback rate detected.")
            advice.add("Verify Foreground Service: UI might be throttled.")
        }
        
        if (rssiVariance > 15.0) {
            advice.add("Enable Kalman Filter: High signal variance detected.")
            advice.add("Recalibrate Environment: Possible multipath interference.")
        }

        if (!bluetoothEnabled) {
            advice.add("CRITICAL: Bluetooth is DISABLED. System is blind.")
        }

        _healthState.value = SystemHealth(
            refreshRate = refreshRate,
            callbackFrequency = callbackFrequency,
            isBluetoothEnabled = bluetoothEnabled,
            isPipelineFrozen = refreshRate == 0,
            rssiStability = rssiVariance,
            advice = advice
        )
    }
    
    fun getDiagnosticChatResponse(query: String): String {
        val health = _healthState.value
        return when {
            query.contains("مشكلة", ignoreCase = true) || query.contains("problem", ignoreCase = true) || query.contains("فحص", ignoreCase = true) -> {
                if (health.advice.isNotEmpty()) {
                    "ASTRA_DIAGNOSTIC_PROTOCOL: Issues Found\n" + health.advice.joinToString("\n- ")
                } else {
                    "ASTRA_SYSTEM_CHECK: All systems nominal. Signal pipeline stabilized."
                }
            }
            query.contains("refresh", ignoreCase = true) || query.contains("تحديث", ignoreCase = true) || query.contains("سرعة", ignoreCase = true) -> {
                "CURRENT_PULSE_RATE: ${health.refreshRate}Hz. Operational threshold: >5Hz."
            }
            query.contains("cheating", ignoreCase = true) || query.contains("غش", ignoreCase = true) -> {
                "THREAT_ENGINE_STATUS: Active. Monitoring for micro-transmitters and RF signatures in vicinity."
            }
            else -> "Astra Nexus Advisor Online. System Health: ${if (health.isPipelineFrozen) "CRITICAL" else "OPTIMAL"}. Deployment ready."
        }
    }
}
