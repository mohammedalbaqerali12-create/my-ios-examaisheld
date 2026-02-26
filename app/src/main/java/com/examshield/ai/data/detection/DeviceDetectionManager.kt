package com.examshield.ai.data.detection

import com.examshield.ai.domain.model.DetectedObject
import com.examshield.ai.domain.repository.DetectionService
import com.examshield.ai.domain.repository.Scanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge

/**
 * Manages the various scanning services and provides a unified flow of detected devices.
 *
 * @param scanners The list of scanners to use for detection.
 */
class DeviceDetectionManager(private val scanners: List<Scanner>) : DetectionService {

    private val _detectedDevices = MutableStateFlow<List<DetectedObject>>(emptyList())
    override val detectedDevices: Flow<List<DetectedObject>> = _detectedDevices.asStateFlow()

    override fun startDetection() {
        scanners.forEach { it.startScan() }
    }

    override fun stopDetection() {
        scanners.forEach { it.stopScan() }
    }
}
