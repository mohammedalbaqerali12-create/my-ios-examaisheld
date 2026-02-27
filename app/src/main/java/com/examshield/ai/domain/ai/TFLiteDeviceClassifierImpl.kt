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

        // Fast-path cheating keywords in the Name
        if (lowerName.contains("spy") || lowerName.contains("mini") || 
            lowerName.contains("vip") || lowerName.contains("s530") ||
            lowerName.matches(Regex(".*[a-z]\\d{2,}.*"))) { // e.g., obscure model numbers
            return DeviceType.NANO_EARPIECE
        }

        // Priority 1: OUI-based internet lookup
        if (manufacturer != null) {
            val type = when (manufacturer) {
                "Apple" -> when {
                    lowerName.contains("watch") -> DeviceType.SMARTWATCH
                    lowerName.contains("airpods") -> DeviceType.WIRELESS_EARBUD
                    else -> DeviceType.SMARTPHONE // Default for Apple
                }
                "Samsung" -> when {
                    lowerName.contains("watch") -> DeviceType.SMARTWATCH
                    lowerName.contains("buds") -> DeviceType.WIRELESS_EARBUD
                    else -> DeviceType.SMARTPHONE // Default for Samsung
                }
                "Google", "Huawei", "Oppo", "Vivo", "OnePlus", "Motorola", "Realme", "Sony", "Nokia", "ZTE", "Asus", "Lenovo" -> DeviceType.SMARTPHONE
                "Xiaomi" -> when {
                    lowerName.contains("watch") || lowerName.contains("band") -> DeviceType.SMARTWATCH
                    lowerName.contains("buds") || lowerName.contains("ear") -> DeviceType.WIRELESS_EARBUD
                    else -> DeviceType.SMARTPHONE
                }
                "Anker", "Jabra", "Bose", "Sennheiser" -> DeviceType.WIRELESS_EARBUD
                else -> null // Fallthrough to heuristics
            }
            if (type != null) {
                return type
            }
        }

        // Priority 2: MAC Randomization Check (Modern OS features)
        // If the second char is 2, 6, A, or E, it's a Locally Administered (Randomized) MAC
        val isRandomizedMac = detectedObject.macAddress.length >= 2 && 
                              detectedObject.macAddress[1].lowercaseChar() in listOf('2', '6', 'a', 'e')

        // Priority 3: Deep Offline Heuristics
        return when {
            lowerName.contains("watch") || lowerName.contains("band") -> DeviceType.SMARTWATCH
            lowerName.contains("phone") || lowerName.contains("iphone") || lowerName.contains("galaxy") || lowerName.startsWith("direct-") -> DeviceType.SMARTPHONE
            lowerName.contains("buds") || lowerName.contains("airpod") || lowerName.contains("ear") -> DeviceType.WIRELESS_EARBUD
            
            // Unnamed BLE devices that are NOT randomized are highly suspicious (Earpieces/Modules)
            detectedObject.isBle && detectedObject.name.isNullOrBlank() && !isRandomizedMac -> DeviceType.NANO_EARPIECE
            
            // Randomized MACs strongly indicate a modern OS (Smartphone/Tablet) rotating its MAC for privacy
            // This applies to BLE, Classic BT, and Wi-Fi probe requests natively
            isRandomizedMac -> DeviceType.SMARTPHONE 
            
            // Classic Bluetooth with no name is highly indicative of cheap spy earpieces
            detectedObject.isClassicBluetooth && detectedObject.name.isNullOrBlank() -> DeviceType.NANO_EARPIECE
            detectedObject.macAddress == "MAGNETIC_FIELD_ANOMALY" -> DeviceType.MAGNETIC_ANOMALY
            else -> DeviceType.SUSPICIOUS_UNKNOWN
        }
    }

    private fun calculateConfidence(type: DeviceType, detectedObject: DetectedObject, zone: DistanceZone): Int {
        var score = when (type) {
            DeviceType.SMARTWATCH, DeviceType.SMARTPHONE -> 85
            DeviceType.WIRELESS_EARBUD, DeviceType.NANO_EARPIECE -> 75
            DeviceType.MAGNETIC_ANOMALY -> 90 // High confidence as it's a direct physical detection
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
