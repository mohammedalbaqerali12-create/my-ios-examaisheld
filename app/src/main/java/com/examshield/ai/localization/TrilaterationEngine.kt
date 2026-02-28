package com.examshield.ai.localization

import kotlin.math.pow
import kotlin.math.sqrt

data class TrilaterationSample(val x: Float, val y: Float, val distanceObserved: Float)

class TrilaterationEngine {
    private val samples = mutableListOf<TrilaterationSample>()

    fun addSample(x: Float, y: Float, distance: Float) {
        samples.add(TrilaterationSample(x, y, distance))
    }

    fun canTrilaterate(): Boolean = samples.size >= 3

    /**
     * Compute least-squares intersection of circles.
     * Ax = b
     */
    fun computeDevicePosition(): Vector2D? {
        if (samples.size < 3) return null

        // System of linear equations:
        // 2(xk - xi)x + 2(yk - yi)y = di^2 - dk^2 - xi^2 + xk^2 - yi^2 + yk^2
        
        val k = samples.last()
        val n = samples.size - 1
        
        // Matrix A (n x 2) and vector b (n x 1)
        // Since we're in Kotlin on Android without heavy linear algebra lib by default,
        // we'll implement a 2x2 solve for the minimal case (3 circles) or 
        // a simple multi-point least squares solver.
        
        var sum_2x_k_i = 0.0
        var sum_2y_k_i = 0.0
        var sum_rhs = 0.0

        // For simplicity with 3 or more points, we solve the linear system:
        // (A^T * A) * X = A^T * B
        
        var m_ata_00 = 0.0
        var m_ata_01 = 0.0
        var m_ata_11 = 0.0
        var v_atb_0 = 0.0
        var v_atb_1 = 0.0

        for (i in 0 until n) {
            val s = samples[i]
            val ai0 = 2.0 * (k.x - s.x)
            val ai1 = 2.0 * (k.y - s.y)
            val bi = (s.distanceObserved.toDouble().pow(2.0) - k.distanceObserved.toDouble().pow(2.0) 
                     - s.x.toDouble().pow(2.0) + k.x.toDouble().pow(2.0) 
                     - s.y.toDouble().pow(2.0) + k.y.toDouble().pow(2.0))

            m_ata_00 += ai0 * ai0
            m_ata_01 += ai0 * ai1
            m_ata_11 += ai1 * ai1
            v_atb_0 += ai0 * bi
            v_atb_1 += ai1 * bi
        }

        // Solve 2x2 system: [m_ata_00 m_ata_01; m_ata_01 m_ata_11] * [x; y] = [v_atb_0; v_atb_1]
        val det = m_ata_00 * m_ata_11 - m_ata_01 * m_ata_01
        if (det == 0.0) return null

        val x = (m_ata_11 * v_atb_0 - m_ata_01 * v_atb_1) / det
        val y = (m_ata_00 * v_atb_1 - m_ata_01 * v_atb_0) / det

        return Vector2D(x.toFloat(), y.toFloat())
    }

    fun computeRefinementResidual(pos: Vector2D): Float {
        // Sigma (Distance - sqrt((x-xi)^2 + (y-yi)^2))^2 / n
        if (samples.isEmpty()) return 0f
        var sumSqError = 0.0
        for (s in samples) {
            val distCalc = sqrt((pos.x - s.x).toDouble().pow(2.0) + (pos.y - s.y).toDouble().pow(2.0))
            sumSqError += (s.distanceObserved - distCalc).pow(2.0)
        }
        return sqrt(sumSqError / samples.size).toFloat()
    }

    fun clear() {
        samples.clear()
    }
}
