package com.examshield.ai.domain.ai

import java.util.*
import com.examshield.ai.domain.ai.ExtendedKalmanFilter
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * ASTRA NEXUS: SENSOR FUSION ENGINE (ALIEN TECH EDITION)
 * 
 * Surgical precision signal processing combining:
 * 1. Adaptive Median Filtering (Non-linear outlier rejection)
 * 2. Extended Kalman Filtering (EKF) with Dynamic Motion-Aware Covariance
 * 3. Hysteresis Smoothing (Jitter reduction)
 * 4. Signal Flux Analysis (Temporal consistency scoring)
 */
class SensorFusionEngine(
    private val neuralLink: CentralNeuralLink? = null
) {

    private val filters = ConcurrentHashMap<String, ExtendedKalmanFilter>()
    private val rawHistories = ConcurrentHashMap<String, MutableList<Int>>()
    private val smoothedValues = ConcurrentHashMap<String, Float>()
    
    // Config Constants
    private val WINDOW_SIZE = 9
    private val R_MEASUREMENT_NOISE = 0.45
    private val Q_BASE_PROCESS_NOISE = 0.012

    /**
     * Injects a raw signal into the fusion pipeline and returns a hyper-stable estimate.
     */
    fun process(macAddress: String, rawRssi: Int, motionIntensity: Double = 0.0): Int {
        // 1. ADAPTIVE OUTLIER REJECTION (Median Filter)
        val history = rawHistories.getOrPut(macAddress) { Collections.synchronizedList(mutableListOf()) }
        synchronized(history) {
            history.add(rawRssi)
            if (history.size > WINDOW_SIZE) history.removeAt(0)
        }

        val filteredMeasurement = if (history.size >= 3) {
            val sorted = history.sorted()
            sorted[sorted.size / 2].toDouble()
        } else {
            rawRssi.toDouble()
        }

        // 2. EXTENDED KALMAN FILTERING (EKF)
        val filter = filters.getOrPut(macAddress) {
            val directives = neuralLink?.directives?.value
            ExtendedKalmanFilter(
                processNoise = directives?.kalmanProcessNoise ?: Q_BASE_PROCESS_NOISE,
                measurementNoise = directives?.kalmanMeasurementNoise ?: R_MEASUREMENT_NOISE,
                initialValue = filteredMeasurement
            )
        }

        // Update EKF with motion awareness
        val ekfValue = filter.update(filteredMeasurement, motionIntensity)

        // 3. HYSTERESIS & EMA (Final Jitter Reduction)
        val lastStored = smoothedValues[macAddress] ?: ekfValue.toFloat()
        
        // Dynamic Alpha: More motion = faster reaction, less motion = more stability
        val alpha = if (motionIntensity > 0.5) 0.7f else 0.35f
        val finalSmooth = (alpha * ekfValue.toFloat()) + (1.0f - alpha) * lastStored
        
        smoothedValues[macAddress] = finalSmooth
        
        return finalSmooth.toInt()
    }

    /**
     * Calculates the "Signal Integrity Index" (SII)
     * Returns a score from 0.0 to 1.0 based on signal stability.
     */
    fun getSignalIntegrity(macAddress: String): Float {
        val history = rawHistories[macAddress] ?: return 0f
        if (history.size < 4) return 0.5f
        
        val avg = history.average()
        val variance = history.map { (it - avg).pow(2.0) }.sum() / history.size
        
        // 0 variance = 1.0 score, 40+ variance = 0.0 score
        return (1.0f - (variance.toFloat() / 40.0f).coerceIn(0.0f, 1.0f))
    }

    fun purge(macAddress: String) {
        filters.remove(macAddress)
        rawHistories.remove(macAddress)
        smoothedValues.remove(macAddress)
    }

    fun purgeAll() {
        filters.clear()
        rawHistories.clear()
        smoothedValues.clear()
    }
}
