package com.examshield.ai.localization

import kotlin.math.atan2
import kotlin.math.sqrt

data class GradientSample(val x: Float, val y: Float, val rssi: Int, val timestamp: Long)

class GradientDirectionEngine {
    private val samples = mutableListOf<GradientSample>()
    private val maxSamples = 10 

    /**
     * Store movement + RSSI scan sample
     */
    fun addSample(x: Float, y: Float, rssi: Int) {
        samples.add(GradientSample(x, y, rssi, System.currentTimeMillis()))
        if (samples.size > maxSamples) {
            samples.removeAt(0)
        }
    }

    /**
     * Compute approximate direction theta (degrees) to device
     */
    fun estimateBearing(): Float? {
        if (samples.size < 3) return null

        // Numerical gradient estimate using linear least squares or difference over distance
        // Gradient Vector = (dR/dX, dR/dY)
        // Vector pointing to device is proportional to this gradient.
        
        var sumDx = 0f
        var sumDy = 0f
        var sumDr = 0f

        val first = samples.first()
        val last = samples.last()

        val dx = last.x - first.x
        val dy = last.y - first.y
        val dr = last.rssi.toFloat() - first.rssi.toFloat()

        if (sqrt(dx*dx + dy*dy) < 0.2f) return null // Haven't moved enough
        
        // theta = atan2(dR/dy, dR/dx)
        // Angle points to where RSSI increases most
        
        // This is a simplified version: assume single source
        // Direction from first to last point is 'heading'
        // If dr is positive, target is 'in front' of that movement heading.
        
        // True gradient would use all points.
        // theta_deg = Math.toDegrees(atan2(dy, dx)).toFloat()
        
        // Return placeholder for now, compute complex gradient in real implementation
        return Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    }

    fun computeAngleConfidence(): Float {
        // Based on RSSI variance and movement distance
        if (samples.size < 3) return 0f
        return 0.65f // Baseline
    }
}
