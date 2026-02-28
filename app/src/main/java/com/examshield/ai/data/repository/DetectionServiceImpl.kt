package com.examshield.ai.data.repository

import com.examshield.ai.domain.ai.DeviceClassifier
import com.examshield.ai.domain.model.ClassificationResult
import com.examshield.ai.domain.repository.DetectionService
import com.examshield.ai.domain.repository.Scanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.ConcurrentHashMap

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DetectionServiceImpl(
    private val bleScanner: Scanner,
    private val classicBluetoothScanner: Scanner,
    private val wifiScanner: Scanner,
    private val wifiDirectScanner: Scanner,
    private val magneticFieldScanner: Scanner,
    private val orientationScanner: com.examshield.ai.data.scanner.OrientationScannerImpl,
    private val classifier: DeviceClassifier
) : DetectionService {

    // ASTRA NEXUS: HIGH-PRECISION TRACKING ENGINE
    private val ekfFilters = ConcurrentHashMap<String, com.examshield.ai.domain.ai.ExtendedKalmanFilter>()
    private val rssiHistory = ConcurrentHashMap<String, MutableList<Int>>()
    private val MAX_HISTORY_SIZE = 15
    private val callbackCounts = ConcurrentHashMap<String, Int>()

    private val lastLogTime = ConcurrentHashMap<String, Long>()

    override fun observeThreats(): Flow<ClassificationResult> {
        var lastMagneticAnomalyTime = 0L
        
        val mergedScanners = merge(
            bleScanner.startScanning(),
            classicBluetoothScanner.startScanning(),
            wifiScanner.startScanning(),
            wifiDirectScanner.startScanning(),
            magneticFieldScanner.startScanning()
        )

        return mergedScanners
            .buffer(capacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            .map { detectedObj ->
                // Memory Management
                if (Math.random() < 0.001) {
                    if (ekfFilters.size > 200) {
                        ekfFilters.clear()
                        rssiHistory.clear()
                    }
                }

                if (detectedObj.macAddress == "MAGNETIC_FIELD_ANOMALY") {
                    lastMagneticAnomalyTime = System.currentTimeMillis()
                    return@map classifier.classify(detectedObj)
                }

                // ASTRA NEXUS: SURGICAL SIGNAL SMOOTHING
                val smoothedRssi = getSmoothedRssi(detectedObj.macAddress, detectedObj.signalStrengthRssi)
                
                // Real-time Logging (Throttled per MAC)
                val now = System.currentTimeMillis()
                val lastLog = lastLogTime[detectedObj.macAddress] ?: 0L
                if (now - lastLog > 500) {
                    android.util.Log.d("ASTRA_NEXUS", "MAC: ${detectedObj.macAddress} | RSSI: ${detectedObj.signalStrengthRssi} | SMOOTHED: $smoothedRssi")
                    lastLogTime[detectedObj.macAddress] = now
                }

                val optimizedObj = detectedObj.copy(signalStrengthRssi = smoothedRssi)
                val baseClassification = classifier.classify(optimizedObj)
                
                // SENSOR FUSION & PRECISION MODE
                val timeSinceMagnet = now - lastMagneticAnomalyTime
                val isNearRF = baseClassification.distanceZone == com.examshield.ai.domain.model.DistanceZone.IMMEDIATE || 
                             baseClassification.distanceZone == com.examshield.ai.domain.model.DistanceZone.NEAR
                
                var finalResult = baseClassification
                if (timeSinceMagnet < 3000 && isNearRF) {
                    finalResult = baseClassification.copy(
                        riskLevel = com.examshield.ai.domain.model.RiskLevel.LEVEL_4_CONFIRMED_THREAT,
                        confidenceScore = 100,
                        discoveryReason = "${baseClassification.discoveryReason} SENSOR_FUSION_MATCH".trim()
                    )
                }

                // Hardware Intensity Orchestration
                if (finalResult.distanceZone == com.examshield.ai.domain.model.DistanceZone.IMMEDIATE || 
                    finalResult.riskLevel == com.examshield.ai.domain.model.RiskLevel.LEVEL_4_CONFIRMED_THREAT) {
                    updateAllScannersIntensity(com.examshield.ai.domain.repository.ScanIntensity.ULTRA_FAST)
                    finalResult = finalResult.copy(
                        discoveryReason = "${finalResult.discoveryReason} [PRECISION_LOCK]".trim()
                    )
                } else if (finalResult.distanceZone == com.examshield.ai.domain.model.DistanceZone.NEAR) {
                    updateAllScannersIntensity(com.examshield.ai.domain.repository.ScanIntensity.HIGH_PRECISION)
                } else {
                    updateAllScannersIntensity(com.examshield.ai.domain.repository.ScanIntensity.BALANCED)
                }

                // COMPOSITE CONFIDENCE SCORING
                val stability = calculateStability(rssiHistory[detectedObj.macAddress] ?: emptyList())
                val callbacks = callbackCounts.getOrDefault(detectedObj.macAddress, 0)
                
                val tempConfidenceBase = if (baseClassification.confidenceScore > 0) baseClassification.confidenceScore else 50
                var confidenceMod = 0
                if (stability < 3.0) confidenceMod += 15
                if (finalResult.estimatedDistanceMeters < 3.0) confidenceMod += 10
                if (callbacks > 5) confidenceMod += 5
                
                val confidence = (tempConfidenceBase + confidenceMod).coerceIn(0, 100)

                finalResult = finalResult.copy(
                    confidenceScore = confidence,
                    discoveryReason = "${finalResult.discoveryReason} [EKF_ACTIVE]".trim()
                )

                // Hardware Intensity Orchestration based on confidence
                if (finalResult.confidenceScore > 80 && finalResult.distanceZone == com.examshield.ai.domain.model.DistanceZone.IMMEDIATE) {
                    updateAllScannersIntensity(com.examshield.ai.domain.repository.ScanIntensity.ULTRA_FAST)
                } else if (finalResult.distanceZone == com.examshield.ai.domain.model.DistanceZone.NEAR) {
                    updateAllScannersIntensity(com.examshield.ai.domain.repository.ScanIntensity.HIGH_PRECISION)
                } else {
                    updateAllScannersIntensity(com.examshield.ai.domain.repository.ScanIntensity.BALANCED)
                }

                finalResult
            }
            .filter { result -> 
                val type = result.deviceType
                type == com.examshield.ai.domain.model.DeviceType.SMARTPHONE ||
                type == com.examshield.ai.domain.model.DeviceType.SMARTWATCH ||
                type == com.examshield.ai.domain.model.DeviceType.WIRELESS_EARBUD ||
                type == com.examshield.ai.domain.model.DeviceType.NANO_EARPIECE ||
                type == com.examshield.ai.domain.model.DeviceType.MAGNETIC_ANOMALY
            }
            .flowOn(Dispatchers.Default)
    }

    private var lastAzimuth = 0f

    private fun getSmoothedRssi(macAddress: String, currentRssi: Int): Int {
        // Increment callback for persistence tracking
        callbackCounts[macAddress] = (callbackCounts[macAddress] ?: 0) + 1

        val deltaAzimuth = Math.abs(orientationScanner.currentAzimuth - lastAzimuth).toDouble()
        lastAzimuth = orientationScanner.currentAzimuth
        
        // EKF Update: Intensity proportional to change in orientation velocity
        val filter = ekfFilters.getOrPut(macAddress) { 
            com.examshield.ai.domain.ai.ExtendedKalmanFilter(initialValue = currentRssi.toDouble()) 
        }
        
        // OUTLIER REJECTION: If RSSI jumps more than 8dBm instantly, dampen the input
        val history = rssiHistory.getOrPut(macAddress) { mutableListOf() }
        var inputRssi = currentRssi.toDouble()
        if (history.size > 3) {
            val lastAvg = history.takeLast(3).map { it.toDouble() }.average()
            if (Math.abs(inputRssi - lastAvg) > 8.0) {
                inputRssi = lastAvg * 0.7 + inputRssi * 0.3 // Dampened spike
                android.util.Log.d("ASTRA_NEXUS", "Outlier Rejected for $macAddress: Delta=${Math.abs(currentRssi - lastAvg)}")
            }
        }

        val smoothed = filter.update(inputRssi, (deltaAzimuth / 45.0).coerceIn(0.0, 1.0)).toInt()
        
        // ADAPTIVE WINDOW MOVING AVERAGE
        // Window size changes based on motion. 
        // More motion = smaller window (less lag). Stable = larger window (less noise).
        val windowSize = if (deltaAzimuth > 10.0) 5 else 12
        
        synchronized(history) {
            history.add(currentRssi)
            while (history.size > windowSize) {
                history.removeAt(0)
            }
        }
        
        val alpha = if (deltaAzimuth > 10.0) 0.8 else 0.4
        val mean = history.map { it.toDouble() }.average()
        
        return (smoothed * alpha + mean * (1.0 - alpha)).toInt()
    }

    private fun calculateStability(history: List<Int>): Double {
        if (history.size < 2) return 5.0
        val avg = history.map { it.toDouble() }.sum() / history.size
        var sumSq = 0.0
        for (item in history) {
            sumSq += Math.pow(item.toDouble() - avg, 2.0)
        }
        return kotlin.math.sqrt(sumSq / history.size)
    }

    private fun updateAllScannersIntensity(intensity: com.examshield.ai.domain.repository.ScanIntensity) {
        bleScanner.updateScanIntensity(intensity)
        classicBluetoothScanner.updateScanIntensity(intensity)
        wifiScanner.updateScanIntensity(intensity)
        wifiDirectScanner.updateScanIntensity(intensity)
        magneticFieldScanner.updateScanIntensity(intensity)
    }

    override fun observeOrientation(): Flow<Float> {
        return orientationScanner.observeOrientation()
    }

    fun observeSteps(): Flow<Float> {
        return orientationScanner.observeSteps()
    }

    override fun stop() {
        bleScanner.stopScanning()
        classicBluetoothScanner.stopScanning()
        wifiScanner.stopScanning()
        wifiDirectScanner.stopScanning()
        magneticFieldScanner.stopScanning()
        rssiHistory.clear()
    }
}
