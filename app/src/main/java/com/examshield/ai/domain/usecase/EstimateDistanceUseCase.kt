package com.examshield.ai.domain.usecase

import com.examshield.ai.domain.model.DistanceZone
import kotlin.math.pow

class EstimateDistanceUseCase {

    /**
     * Estimates distance using the Log-Distance Path Loss Model.
     * d = 10 ^ ((TxPower - RSSI) / (10 * N))
     * N is the path loss exponent.
     */
    fun execute(rssi: Int, txPower: Int = -59, environmentalFactor: Double = 3.0): Float {
        if (rssi == 0) return -1f
        
        val ratio = (txPower - rssi).toDouble() / (10.0 * environmentalFactor)
        return 10.0.pow(ratio).toFloat()
    }

    fun getDistanceZone(distance: Float): DistanceZone {
        return when {
            distance < 0f -> DistanceZone.FAR // invalid
            distance < 0.5f -> DistanceZone.IMMEDIATE
            distance < 2.0f -> DistanceZone.NEAR
            distance < 5.0f -> DistanceZone.MEDIUM
            else -> DistanceZone.FAR
        }
    }
}
