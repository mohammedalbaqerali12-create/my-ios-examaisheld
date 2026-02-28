package com.examshield.ai.domain.usecase

import javax.inject.Inject
import kotlin.math.pow

/**
 * A use case for estimating the distance of a detected device based on its RSSI value.
 */
class EstimateDistanceUseCase @Inject constructor(
    private val roomModelingEngine: com.examshield.ai.domain.ai.RoomModelingEngine
) {

    /**
     * Estimates the distance in meters using an environment-aware Log-Distance Path Loss model.
     *
     * @param rssi The signal strength in dBm.
     * @param txPower The average transmit power of the device at 1 meter, in dBm.
     * @return The estimated distance in meters.
     */
    operator fun invoke(rssi: Double, txPower: Int = -59): Double {
        if (rssi >= 0.0) return 0.5 
        
        return roomModelingEngine.adjustDistanceByEnvironment(0.0, rssi.toInt())
    }

    /**
     * Calculates the signal variance score to determine stability.
     * Higher score = Less stable / More noise.
     */
    fun calculateStabilityScore(rssiHistory: List<Int>): Double {
        if (rssiHistory.size < 2) return 0.0
        val mean = rssiHistory.average()
        return rssiHistory.map { (it - mean).pow(2) }.average()
    }
}
