package com.examshield.ai.domain.usecase

import kotlin.math.pow

/**
 * A use case for estimating the distance of a detected device based on its RSSI value.
 */
class EstimateDistanceUseCase {

    operator fun invoke(rssi: Double, txPower: Int = -59): Double {
        if (rssi >= 0.0) return 0.5 
        
        // n = 2.7 for indoor
        val n = 2.7
        val power = (txPower - rssi) / (10.0 * n)
        return 10.0.pow(power)
    }

    /**
     * Calculates the signal variance score to determine stability.
     * Higher score = Less stable / More noise.
     */
    fun calculateStabilityScore(rssiHistory: List<Int>): Double {
        if (rssiHistory.size < 2) return 0.0
        val mean = rssiHistory.map { it.toDouble() }.sum() / rssiHistory.size
        var sumSq = 0.0
        for (item in rssiHistory) {
            sumSq += (item.toDouble() - mean).pow(2.0)
        }
        return sumSq / rssiHistory.size
    }
}
