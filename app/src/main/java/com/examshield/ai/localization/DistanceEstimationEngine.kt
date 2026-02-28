package com.examshield.ai.localization

import kotlin.math.pow

class DistanceEstimationEngine {
    
    /**
     * Log-distance path loss model:
     * d = 10 ^ ((TxPower - RSSI) / (10 * n))
     * 
     * @param txPower The RSSI at 1m distance. Default -59 to -62 for BLE.
     * @param rssi The measured signal strength.
     * @param n Environmental path loss exponent (2.0 = free space, 3.0-4.0 = indoor)
     */
    fun estimateDistance(rssi: Int, txPower: Int = -59, n: Float = 2.7f): Float {
        val power = (txPower - rssi) / (10.0 * n)
        return 10.0.pow(power).toFloat()
    }

    /**
     * Multi-sample distance estimation with Kalman filtering or normalization
     */
    fun getFilteredDistance(rssiHistory: List<Int>, n: Float): Float {
        if (rssiHistory.isEmpty()) return 10f
        val avgRssi = rssiHistory.average().toInt()
        return estimateDistance(avgRssi, n = n)
    }
}
