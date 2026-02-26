package com.examshield.ai.domain.model

data class ClassificationResult(
    val deviceType: DeviceType,
    val confidenceScore: Int, // 0 to 100
    val distanceZone: DistanceZone,
    val estimatedDistanceMeters: Float,
    val riskLevel: RiskLevel,
    val rawObject: DetectedObject
)
