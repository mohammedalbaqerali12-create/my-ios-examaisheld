package com.examshield.ai.domain.repository

import com.examshield.ai.domain.model.ClassificationResult
import kotlinx.coroutines.flow.Flow

interface DetectionService {
    fun observeThreats(): Flow<ClassificationResult>
    fun observeOrientation(): Flow<Float>
    fun observeLocation(): Flow<android.location.Location>
    fun stop()
}
