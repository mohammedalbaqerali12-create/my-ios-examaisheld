package com.examshield.ai.domain.repository

import com.examshield.ai.domain.model.ClassificationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DetectionService {
    val currentOrbitalData: StateFlow<OrbitalData>
    val maxDetectionRange: StateFlow<Float>
    fun setMaxDetectionRange(range: Float)
    fun observeThreats(): Flow<ClassificationResult>
    fun observeOrientation(): Flow<Pair<Float, Float>>
    fun stop()
}
