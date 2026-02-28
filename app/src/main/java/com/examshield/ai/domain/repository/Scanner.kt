package com.examshield.ai.domain.repository

import com.examshield.ai.domain.model.DetectedObject
import kotlinx.coroutines.flow.Flow

enum class ScanIntensity {
    LOW_POWER,
    BALANCED,
    HIGH_PRECISION,
    ULTRA_FAST
}

interface Scanner {
    fun startScanning(): Flow<DetectedObject>
    fun stopScanning()
    fun updateScanIntensity(intensity: ScanIntensity) {}
}
