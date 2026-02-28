package com.examshield.ai.domain.model

enum class DeviceType {
    SMARTPHONE,
    SMARTWATCH,
    WIRELESS_EARBUD,
    NANO_EARPIECE,
    MAGNETIC_ANOMALY,
    ROUTER_INFRASTRUCTURE,
    SUSPICIOUS_UNKNOWN
}

enum class DistanceZone {
    FAR,       // > 5m
    MEDIUM,    // 2 - 5m
    NEAR,      // 0.5 - 2m
    IMMEDIATE  // < 0.5m
}

enum class RiskLevel {
    LEVEL_1_SUSPICIOUS,
    LEVEL_2_REPEATED,
    LEVEL_3_PROXIMITY_MATCH,
    LEVEL_4_CONFIRMED_THREAT
}
