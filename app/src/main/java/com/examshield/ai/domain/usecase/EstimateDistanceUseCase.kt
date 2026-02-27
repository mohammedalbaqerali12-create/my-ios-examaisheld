package com.examshield.ai.domain.usecase

import javax.inject.Inject
import kotlin.math.pow

/**
 * A use case for estimating the distance of a detected device based on its RSSI value.
 */
class EstimateDistanceUseCase @Inject constructor() {

    /**
     * Estimates the distance in meters.
     *
     * @param rssi The signal strength in dBm.
     * @param txPower The average transmit power of the device at 1 meter, in dBm.
     *                Common values are -59 for BLE, but can vary.
     * @return The estimated distance in meters.
     */
    operator fun invoke(rssi: Double, txPower: Int = -59): Double {
        if (rssi == 0.0) {
            return -1.0 // Cannot determine distance
        }
        // Environmental factor (N): ranges from 2.0 (open space) to 4.0 (obstructed)
        val n = 2.5 
        val ratio = rssi * 1.0 / txPower
        if (ratio < 1.0) {
            return ratio.pow(10)
        } else {
            return (0.89976) * ratio.pow(7.7095) + 0.111
        }
    }
}
