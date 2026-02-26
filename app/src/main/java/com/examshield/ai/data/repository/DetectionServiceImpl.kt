package com.examshield.ai.data.repository

import com.examshield.ai.domain.ai.DeviceClassifier
import com.examshield.ai.domain.model.ClassificationResult
import com.examshield.ai.domain.repository.DetectionService
import com.examshield.ai.domain.repository.Scanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.util.concurrent.ConcurrentHashMap

class DetectionServiceImpl(
    private val bleScanner: Scanner,
    private val wifiScanner: Scanner,
    private val classifier: DeviceClassifier
) : DetectionService {

    // RSSI Smoothing / Moving Average cache. Window size = 5.
    // This dramatically improves the mathematical accuracy of distance estimation
    // by reducing sudden environmental spikes.
    private val rssiHistory = ConcurrentHashMap<String, MutableList<Int>>()
    private val MAX_HISTORY_SIZE = 5

    override fun observeThreats(): Flow<ClassificationResult> {
        // Sensor Fusion: Merge Bluetooth LE and Wi-Fi scanning into one consistent stream
        val mergedScanners = merge(
            bleScanner.startScanning(),
            wifiScanner.startScanning()
        )

        return mergedScanners.map { detectedObj ->
            // 1. Apply Exponential/Simple Moving Average to RSSI
            val smoothedRssi = getSmoothedRssi(detectedObj.macAddress, detectedObj.signalStrengthRssi)
            
            // 2. Create optimized object with hardware signal noise reduced
            val optimizedObj = detectedObj.copy(signalStrengthRssi = smoothedRssi)

            // 3. Forward to local AI (TFLite logic) for classification
            classifier.classify(optimizedObj)
        }
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
        wifiScanner.stopScanning()
        rssiHistory.clear()
    }
}
