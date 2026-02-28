package com.examshield.ai.domain.ai

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Extended Kalman Filter (EKF) optimized for RSSI tracking.
 * This implementation adapts to device movement to reduce lag during rotation
 * and increase stability when stationary.
 */
class ExtendedKalmanFilter(
    private var processNoise: Double = 0.015,
    private val measurementNoise: Double = 0.5,
    initialValue: Double = -100.0
) {
    private var x: Double = initialValue // State: Smoothed RSSI
    private var p: Double = 1.0        // Estimation error covariance
    private var q: Double = processNoise // Process noise covariance

    /**
     * Update the filter with a new measurement.
     * @param measurement The raw RSSI value.
     * @param motionIntensity A factor (0.0 to 1.0) representing how much the phone is moving.
     */
    fun update(measurement: Double, motionIntensity: Double = 0.0): Double {
        // PREDICTION
        // In an EKF for 1D RSSI, the state transition is usually assumed stationary F=1
        // We adjust process noise dynamically based on motion intensity.
        val dynamicQ = q * (1.0 + motionIntensity * 10.0) 
        p += dynamicQ

        // UPDATE (Kalman Gain)
        val k = p / (p + measurementNoise)
        
        // Innovation
        val innovation = measurement - x
        
        // Outlier Rejection: If the innovation is massive, dampen the grain
        val dampenedK = if (Math.abs(innovation) > 15.0) k * 0.3 else k

        x += dampenedK * innovation
        p *= (1.0 - dampenedK)

        return x
    }

    fun getState(): Double = x
}

/**
 * Composite Confidence Engine
 * Evaluates the reliability of a signal based on multiple factors.
 */
object localizationConfidenceEngine {
    fun calculateScore(
        stability: Double, // RSSI Variance
        motionConsistency: Double, // Orientation vs Signal change
        temporalPersistence: Int, // How many callbacks received
        isWithinRoom: Boolean
    ): Int {
        var score = 0
        
        // 1. Stability (up to 30 points)
        score += (30 - (stability * 2).toInt()).coerceIn(0, 30)
        
        // 2. Room Alignment (up to 20 points)
        if (isWithinRoom) score += 20
        
        // 3. Persistence (up to 30 points)
        score += (temporalPersistence * 2).coerceAtMost(30)
        
        // 4. Motion Agreement (up to 20 points)
        score += (motionConsistency * 20).toInt().coerceIn(0, 20)

        return score
    }
}
