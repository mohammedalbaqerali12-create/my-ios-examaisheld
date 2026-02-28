package com.examshield.ai.localization

import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sqrt

class EnvironmentAdaptiveModel {
    private var currentN = 2.7f // Default indoor path loss exponent
    private val minN = 1.8f
    private val maxN = 5.0f

    /**
     * Update environmental path loss coefficient 'n'
     * adaptive update formula:
     * n = (TxPower - RSSI) / (10 * log10(observedDistance))
     * where observedDistance is the known ground truth (e.g., from supervisor pos to trilat pos)
     */
    fun updatePathLossCoeff(rssi: Int, txPower: Int, groundTruthDist: Float) {
        if (groundTruthDist <= 0.1f) return
        
        val calculatedN = (txPower - rssi) / (10.0 * log10(groundTruthDist.toDouble()))
        
        // Exponential moving average for smoothing
        val alpha = 0.1f
        currentN = (currentN * (1f - alpha) + calculatedN * alpha).toFloat().coerceIn(minN, maxN)
    }

    fun getCurrentN(): Float = currentN

    /**
     * ErrorRadius = VarianceFactor + TrilaterationResidual
     */
    fun estimateErrorRadius(rssiVariance: Float, trilatResidual: Float): Float {
        val varianceFactor = sqrt(rssiVariance) * 0.15f
        return (varianceFactor + trilatResidual).coerceIn(0.1f, 10.0f)
    }
}
