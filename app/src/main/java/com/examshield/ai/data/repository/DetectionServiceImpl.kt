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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.ConcurrentHashMap

class DetectionServiceImpl(
    private val bleScanner: Scanner,
    private val classicBluetoothScanner: Scanner,
    private val wifiScanner: Scanner,
    private val wifiDirectScanner: Scanner,
    private val magneticFieldScanner: Scanner,
    private val classifier: DeviceClassifier
) : DetectionService {

    // RSSI Smoothing / Moving Average cache. Window size = 2 (Lightning fast updates).
    // This dramatically improves the mathematical accuracy of distance estimation
    // by reducing sudden environmental spikes without causing unacceptable UI lag.
    private val rssiHistory = ConcurrentHashMap<String, MutableList<Int>>()
    private val MAX_HISTORY_SIZE = 2

    override fun observeThreats(): Flow<ClassificationResult> {
        // Sensor Fusion: Merge all scanning methods into one consistent stream
        val mergedScanners = merge(
            bleScanner.startScanning(),
            classicBluetoothScanner.startScanning(),
            wifiScanner.startScanning(),
            wifiDirectScanner.startScanning(),
            magneticFieldScanner.startScanning()
        )

        return mergedScanners
            .buffer(capacity = 100, onBufferOverflow = BufferOverflow.DROP_OLDEST) // Handle massive spikes (e.g. walking into a crowded exam hall) without crashing or OOM
            .map { detectedObj ->
                // For non-signal based detections, we can skip smoothing
                if (detectedObj.macAddress == "MAGNETIC_FIELD_ANOMALY") {
                    return@map classifier.classify(detectedObj)
                }

                // 1. Apply Exponential/Simple Moving Average to RSSI
                val smoothedRssi = getSmoothedRssi(detectedObj.macAddress, detectedObj.signalStrengthRssi)
                
                // 2. Create optimized object with hardware signal noise reduced
                val optimizedObj = detectedObj.copy(signalStrengthRssi = smoothedRssi)

                // 3. Forward to local AI (TFLite logic) & Online API for classification
                classifier.classify(optimizedObj)
            }
            .filter { classificationResult -> 
                // FILTER: Silently drop routers and irrelevant network noise.
                // We only care about Smartphones, Smartwatches, and Earpieces.
                classificationResult.deviceType != com.examshield.ai.domain.model.DeviceType.SUSPICIOUS_UNKNOWN 
            }
            .flowOn(Dispatchers.Default) // Shift heavy mapping, lists, and AI out of the main hardware collection thread
    }

    private fun getSmoothedRssi(macAddress: String, currentRssi: Int): Int {
        val history = rssiHistory.getOrPut(macAddress) { mutableListOf() }
        synchronized(history) {
            history.add(currentRssi)
            if (history.size > MAX_HISTORY_SIZE) {
                // Drop the oldest element
                history.removeAt(0)
            }
            // Simple Moving Average
            return history.average().toInt()
        }
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
