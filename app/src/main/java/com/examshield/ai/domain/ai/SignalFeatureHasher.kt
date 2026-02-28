package com.examshield.ai.domain.ai

import com.examshield.ai.domain.model.DetectedObject
import java.security.MessageDigest

object SignalFeatureHasher {

    /**
     * Generates a unique, anonymized hash for a signal's behavioral features.
     * Combines invariant features like Name pattern, MAC OUI, and typical signal characteristics.
     */
    fun hashSignal(obj: DetectedObject): String {
        // We use MAC OUI (first 3 bytes) + Device Name + a normalized burst intensity
        // This makes it anonymized (not tied to specific device ID but to model/type)
        val macOui = obj.macAddress.take(8).uppercase()
        val normalizedName = (obj.name ?: "Unknown").uppercase().take(20)
        
        // Feature String: "OUI:00:1A:2B|NAME:SOME_DEVICE"
        // We don't include RSSI directly in the hash as it's too variable, 
        // but we include it in the analysis phase.
        val featureString = "OUI:$macOui|NAME:$normalizedName"
        
        return sha256(featureString)
    }

    private fun sha256(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
