package com.examshield.ai.domain.repository

import com.examshield.ai.domain.model.DetectedObject
import kotlinx.coroutines.flow.Flow

interface Scanner {
    fun startScanning(): Flow<DetectedObject>
    fun stopScanning()
}
