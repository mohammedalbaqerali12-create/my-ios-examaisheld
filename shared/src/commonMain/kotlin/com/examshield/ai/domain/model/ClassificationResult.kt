package com.examshield.ai.domain.model

enum class SignalTrajectory {
    APPROACHING, // RSSI increasing
    RECEDING,    // RSSI decreasing
    STATIC,      // RSSI stable
    FLUCTUATING, // RSSI wild variance
    UNKNOWN
}

data class ClassificationResult(
    val deviceType: DeviceType,
    val confidenceScore: Int, // 0 to 100
    val distanceZone: DistanceZone,
    val estimatedDistanceMeters: Float,
    val riskLevel: RiskLevel,
    val discoveryReason: String = "",
    val rawObject: DetectedObject,
    // ASTRA NEXUS: Synergy components
    val isNexusVerified: Boolean = false,
    val synergyScore: Int = 0,
    val feedback: SupervisorFeedback = SupervisorFeedback.PENDING,
    val trajectory: SignalTrajectory = SignalTrajectory.UNKNOWN
)
