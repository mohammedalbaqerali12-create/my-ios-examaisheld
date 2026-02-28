package com.examshield.ai.domain.ai

import javax.inject.Inject

/**
 * Astra Nexus: Precise 1D Kalman Filter for RSSI stability.
 * Ideal for high-noise RF environments.
 */
class KalmanFilter(
    private val processNoise: Double = 0.008, // Q: How fast the underlying RSSI can change
    private val measurementNoise: Double = 0.5, // R: How much we trust the hardware sensor (lower = more trust)
) {
    private var x: Double = 0.0 // State estimate (smoothed RSSI)
    private var p: Double = 1.0 // Estimate covariance (error)
    private var isInitialized = false

    /**
     * Updates the filter with a new raw measurement and returns the smoothed value.
     */
    fun update(measurement: Double): Double {
        if (!isInitialized) {
            x = measurement
            isInitialized = true
            return x
        }

        // PREDICT
        p += processNoise

        // UPDATE
        val k = p / (p + measurementNoise) // Kalman Gain
        x += k * (measurement - x)
        p *= (1 - k)

        return x
    }

    /**
     * Injects a reset if the signal is lost or environment changes drastically.
     */
    fun reset(initialValue: Double) {
        x = initialValue
        p = 1.0
        isInitialized = true
    }
    
    fun getCurrentEstimate(): Double = x
    
    fun getVariance(): Double = p
}
