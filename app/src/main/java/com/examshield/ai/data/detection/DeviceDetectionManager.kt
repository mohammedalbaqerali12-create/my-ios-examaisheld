package com.examshield.ai.data.detection

import com.examshield.ai.domain.model.DetectedObject
import com.examshield.ai.domain.repository.Scanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the various scanning services and provides a unified flow of detected devices.
 *
 * @param scanners The list of scanners to use for detection.
 */
class DeviceDetectionManager(private val scanners: List<Scanner>) {

    private val _detectedDevices = MutableStateFlow<List<DetectedObject>>(emptyList())
    val detectedDevices: Flow<List<DetectedObject>> = _detectedDevices.asStateFlow()

    fun startDetection() {
        scanners.forEach { it.startScanning() }
    }

    fun stopDetection() {
        scanners.forEach { it.stopScanning() }
    }
}
