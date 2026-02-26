package com.examshield.ai.domain.ai

import com.examshield.ai.domain.model.ClassificationResult
import com.examshield.ai.domain.model.DetectedObject
import com.examshield.ai.domain.model.DeviceType
import com.examshield.ai.domain.model.RiskLevel
import com.examshield.ai.domain.usecase.EstimateDistanceUseCase
import javax.inject.Inject
import kotlin.random.Random

/**
 * Implementation of DeviceClassifier.
 * In a fully deployed version, this class would load a .tflite model and feed the
 * raw MAC intervals and RSSI buffers into the model.
 * Here, we simulate the inference based on rules mimicking an AI output.
 */
class TFLiteDeviceClassifierImpl @Inject constructor(
    private val estimateDistanceUseCase: EstimateDistanceUseCase,
    private val adaptiveLearningEngine: AdaptiveLearningEngine
) : DeviceClassifier {

    override suspend fun classify(detectedObject: DetectedObject): ClassificationResult {
        // AI Model Simulation Logic based on MAC OUI and RSSI behavior
        
        // 1. Distance Calculation
        val distance = estimateDistanceUseCase.execute(detectedObject.signalStrengthRssi)
        val zone = estimateDistanceUseCase.getDistanceZone(distance)

        // 2. Classify Device Type based on heuristics (mocking AI feature extraction)
        val lowerName = detectedObject.name?.lowercase() ?: ""
        var type = DeviceType.SUSPICIOUS_UNKNOWN
        var baseConfidence = Random.nextInt(40, 70) // Baseline confidence

        if (lowerName.contains("watch") || lowerName.contains("band")) {
            type = DeviceType.SMARTWATCH
            baseConfidence = Random.nextInt(80, 95)
        } else if (lowerName.contains("phone") || lowerName.contains("iphone") || lowerName.contains("galaxy")) {
            type = DeviceType.SMARTPHONE
            baseConfidence = Random.nextInt(85, 99)
        } else if (lowerName.contains("buds") || lowerName.contains("airpod") || lowerName.contains("ear")) {
            type = DeviceType.WIRELESS_EARBUD
            baseConfidence = Random.nextInt(75, 95)
        } else if (detectedObject.isClassicBluetooth && detectedObject.name == null) {
            // Hidden devices tend to use classic Bluetooth with empty or obscure names
            type = DeviceType.NANO_EARPIECE
            baseConfidence = Random.nextInt(60, 85)
        }

        // 3. Self-Learning Phase: Adjust confidence based on environment & past supervision
        val finalConfidence = adaptiveLearningEngine.adjustConfidence(detectedObject, baseConfidence)

        // 4. Risk Escalation AI logic based on learned confidence
        val riskLevel = when {
            finalConfidence < 30 -> RiskLevel.LEVEL_1_SUSPICIOUS // De-escalated due to baseline learning
            zone == com.examshield.ai.domain.model.DistanceZone.IMMEDIATE && type == DeviceType.NANO_EARPIECE -> RiskLevel.LEVEL_4_CONFIRMED_THREAT
            zone == com.examshield.ai.domain.model.DistanceZone.IMMEDIATE || zone == com.examshield.ai.domain.model.DistanceZone.NEAR -> RiskLevel.LEVEL_3_PROXIMITY_MATCH
            finalConfidence > 80 -> RiskLevel.LEVEL_2_REPEATED
            else -> RiskLevel.LEVEL_1_SUSPICIOUS
        }

        return ClassificationResult(
            deviceType = type,
            confidenceScore = finalConfidence,
            distanceZone = zone,
            estimatedDistanceMeters = distance,
            riskLevel = riskLevel,
            rawObject = detectedObject
        )
    }
}
