package com.examshield.ai.domain.ai

import com.examshield.ai.domain.model.ClassificationResult
import com.examshield.ai.domain.model.DetectedObject
import com.examshield.ai.domain.model.DeviceType
import com.examshield.ai.domain.model.DistanceZone
import com.examshield.ai.domain.model.RiskLevel
import com.examshield.ai.domain.usecase.EstimateDistanceUseCase
import javax.inject.Inject

class TFLiteDeviceClassifierImpl @Inject constructor(
    private val estimateDistanceUseCase: EstimateDistanceUseCase,
    private val adaptiveLearningEngine: AdaptiveLearningEngine
) : DeviceClassifier {

    override suspend fun classify(detectedObject: DetectedObject): ClassificationResult {
        // Step 1: Get historical context from the learning engine
        val context = adaptiveLearningEngine.analyze(detectedObject)

        // Step 2: Estimate distance and zone
        val distance = estimateDistanceUseCase(detectedObject.signalStrengthRssi.toDouble())
        val zone = getDistanceZone(distance)

        // Step 3: Determine device type using advanced logic (now suspended for internet lookup)
        val type = determineDeviceType(detectedObject)

        // Step 4: Calculate base confidence score
        var confidence = calculateConfidence(type, detectedObject, zone)

        // Step 5: Adjust confidence based on historical context
        if (context.isFrequentlySeen) {
            confidence -= 20 // Reduce confidence for familiar devices
        }
        if (context.isNew) {
            confidence += 15 // Increase confidence for new devices
        }
        if (context.isRepeatedlySeenRecently) {
            confidence += 10 // Increase confidence for devices seen multiple times recently
        }

        // Step 6: Determine final risk level based on all factors
        val riskLevel = determineRiskLevel(type, zone, confidence, context)
        
        return ClassificationResult(
            deviceType = type,
            confidenceScore = confidence.coerceIn(0, 100), // Ensure confidence is between 0 and 100
            distanceZone = zone,
            estimatedDistanceMeters = distance.toFloat(),
            riskLevel = riskLevel,
            rawObject = detectedObject
        )
    }

    private suspend fun determineDeviceType(detectedObject: DetectedObject): DeviceType {
        val lowerName = detectedObject.name?.lowercase() ?: ""
        val manufacturer = OuiLookup.lookup(detectedObject.macAddress)
        val extra = detectedObject.extraMetadata
        
        // 1. PHYSICAL THREAT DETECTION (Magnetometer)
        if (detectedObject.macAddress == "MAGNETIC_FIELD_ANOMALY") {
             val vector = detectedObject.sensorVector
             if (vector != null) {
                 val magnitude = kotlin.math.sqrt(vector[0]*vector[0] + vector[1]*vector[1] + vector[2]*vector[2])
                 return if (magnitude > 150) DeviceType.NANO_EARPIECE else DeviceType.MAGNETIC_ANOMALY
             }
             return DeviceType.MAGNETIC_ANOMALY
        }

        // 2. CHEATING GEAR SIGNATURES
        if (lowerName.contains("spy") || lowerName.contains("mini") || 
            lowerName.contains("vip") || lowerName.contains("s530") ||
            lowerName.matches(Regex(".*[a-z]\\d{2,}.*"))) {
            return DeviceType.NANO_EARPIECE
        }

        // 3. INFRASTRUCTURE SCANNING (Routers/Towers)
        val isInfrastructure = lowerName.contains("tower") || lowerName.contains("booster") || 
                               lowerName.contains("mesh") || lowerName.contains("extender") || 
                               lowerName.contains("router") || lowerName.contains("tp-link") ||
                               lowerName.contains("tenda") || lowerName.contains("mercys") ||
                               (manufacturer?.lowercase()?.contains("tp-link") == true)
        
        if (isInfrastructure) return DeviceType.ROUTER_INFRASTRUCTURE

        // 4. SMART-PERIPHERAL SIGNATURES (Watches & Buds)
        val isAudioSignature = lowerName.contains("buds") || lowerName.contains("ear") || 
                               lowerName.contains("airpod") || lowerName.contains("audio") ||
                               lowerName.contains("headset")
                               
        val isWearableSignature = lowerName.contains("watch") || lowerName.contains("band") || 
                                 lowerName.contains("fitbit") || lowerName.contains("garmin")

        // 5. SMARTPHONE SIGNATURE (The "Astra" Algorithm)
        // Phones typically have Randomized MACs AND are connectable AND often broadcast multiple services
        val isRandomizedMac = detectedObject.macAddress.length >= 2 && 
                              detectedObject.macAddress[1].lowercaseChar() in listOf('2', '6', 'a', 'e')
        
        val txPower = extra["txPower"] as? Int ?: -1
        val isConnectable = extra["isConnectable"] as? Boolean ?: false
        
        // High confidence Phone signature: Randomized MAC + High Power/Connectable
        val isModernPhone = isRandomizedMac && (isConnectable || txPower > -50)

        // 6. MULTI-FACTOR CLASSIFICATION
        return when {
            // Priority 1: Direct matches
            isWearableSignature -> DeviceType.SMARTWATCH
            isAudioSignature -> DeviceType.WIRELESS_EARBUD
            
            // Priority 2: Manufacturer Intent
            manufacturer != null -> {
                val mLower = manufacturer.lowercase()
                when {
                    mLower.contains("apple") || mLower.contains("samsung") || mLower.contains("google") ||
                    mLower.contains("huawei") || mLower.contains("oppo") || mLower.contains("vivo") ||
                    mLower.contains("oneplus") || mLower.contains("motorola") || mLower.contains("xiaomi") -> {
                        // Refine based on specific signatures inside known phone brands
                        if (isAudioSignature) DeviceType.WIRELESS_EARBUD
                        else if (isWearableSignature) DeviceType.SMARTWATCH
                        else DeviceType.SMARTPHONE
                    }
                    mLower.contains("anker") || mLower.contains("jabra") || mLower.contains("bose") -> DeviceType.WIRELESS_EARBUD
                    else -> if (isModernPhone) DeviceType.SMARTPHONE else DeviceType.SUSPICIOUS_UNKNOWN
                }
            }

            // Priority 3: Heuristic fallbacks
            isModernPhone -> DeviceType.SMARTPHONE
            lowerName.contains("phone") || lowerName.contains("iphone") || lowerName.contains("galaxy") || lowerName.startsWith("direct-") -> DeviceType.SMARTPHONE
            
            // Unnamed non-randomized BLE is highly suspicious for hidden modules
            detectedObject.isBle && detectedObject.name.isNullOrBlank() && !isRandomizedMac -> DeviceType.NANO_EARPIECE
            
            isRandomizedMac -> {
                if (detectedObject.isWifi && (detectedObject.name.isNullOrBlank() || detectedObject.name == "Hidden Network")) {
                    DeviceType.SUSPICIOUS_UNKNOWN
                } else {
                    DeviceType.SMARTPHONE
                }
            }
            
            detectedObject.isClassicBluetooth && detectedObject.name.isNullOrBlank() -> DeviceType.NANO_EARPIECE
            else -> DeviceType.SUSPICIOUS_UNKNOWN
        }
    }

    private fun calculateConfidence(type: DeviceType, detectedObject: DetectedObject, zone: DistanceZone): Int {
        var score = when (type) {
            DeviceType.SMARTWATCH, DeviceType.SMARTPHONE -> 85
            DeviceType.WIRELESS_EARBUD, DeviceType.NANO_EARPIECE -> 75
            DeviceType.MAGNETIC_ANOMALY -> 90 // High confidence as it's a direct physical detection
            DeviceType.ROUTER_INFRASTRUCTURE -> 30 // Low confidence as these are filtered out anyway
            DeviceType.SUSPICIOUS_UNKNOWN -> 50
        }

        if (zone == DistanceZone.IMMEDIATE) {
            score += 15
        } else if (zone == DistanceZone.NEAR) {
            score += 5
        }

        return score
    }

    private fun determineRiskLevel(type: DeviceType, zone: DistanceZone, confidence: Int, context: AnalysisContext): RiskLevel {
        return when {
            type == DeviceType.MAGNETIC_ANOMALY && zone == DistanceZone.IMMEDIATE -> RiskLevel.LEVEL_4_CONFIRMED_THREAT
            type == DeviceType.NANO_EARPIECE && zone == DistanceZone.IMMEDIATE -> RiskLevel.LEVEL_4_CONFIRMED_THREAT
            confidence > 90 && (zone == DistanceZone.IMMEDIATE || zone == DistanceZone.NEAR) -> RiskLevel.LEVEL_3_PROXIMITY_MATCH
            context.isRepeatedlySeenRecently && confidence > 75 -> RiskLevel.LEVEL_2_REPEATED
            else -> RiskLevel.LEVEL_1_SUSPICIOUS
        }
    }

    private fun getDistanceZone(distance: Double): DistanceZone {
        return when {
            distance < 0 -> DistanceZone.FAR // Or unknown
            distance <= 1.0 -> DistanceZone.IMMEDIATE
            distance <= 3.0 -> DistanceZone.NEAR
            distance <= 10.0 -> DistanceZone.MEDIUM
            else -> DistanceZone.FAR
        }
    }
}
