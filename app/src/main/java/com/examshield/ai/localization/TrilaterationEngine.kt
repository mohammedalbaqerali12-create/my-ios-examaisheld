package com.examshield.ai.localization

import kotlin.math.pow
import kotlin.math.sqrt

data class TrilaterationSample(val x: Float, val y: Float, val distanceObserved: Float)

class TrilaterationEngine {
    private val samples = mutableListOf<TrilaterationSample>()
    
    // Incremental Least Squares Matrices (A^T A and A^T b)
    private var m_ata_00 = 0.0
    private var m_ata_01 = 0.0
    private var m_ata_11 = 0.0
    private var v_atb_0 = 0.0
    private var v_atb_1 = 0.0

    fun addSample(x: Float, y: Float, distance: Float) {
        val newSample = TrilaterationSample(x, y, distance)
        
        if (samples.isNotEmpty()) {
            val p0 = samples[0]
            val ai0 = 2.0 * (x - p0.x)
            val ai1 = 2.0 * (y - p0.y)
            val bi = p0.distanceObserved.toDouble().pow(2.0) - distance.toDouble().pow(2.0) + 
                     x.toDouble().pow(2.0) - p0.x.toDouble().pow(2.0) + 
                     y.toDouble().pow(2.0) - p0.y.toDouble().pow(2.0)

            m_ata_00 += ai0 * ai0
            m_ata_01 += ai0 * ai1
            m_ata_11 += ai1 * ai1
            v_atb_0 += ai0 * bi
            v_atb_1 += ai1 * bi
        }
        
        samples.add(newSample)
    }

    fun canTrilaterate(): Boolean = samples.size >= 3

    fun getSamples(): List<TrilaterationSample> = samples

    /**
     * Efficiently recompute device position using maintained A^T A and A^T b.
     */
    fun computeDevicePosition(): Vector2D? {
        if (samples.size < 3) return null

        val det = m_ata_00 * m_ata_11 - m_ata_01 * m_ata_01
        if (det == 0.0) return null

        val x = (m_ata_11 * v_atb_0 - m_ata_01 * v_atb_1) / det
        val y = (m_ata_00 * v_atb_1 - m_ata_01 * v_atb_0) / det

        return Vector2D(x.toFloat(), y.toFloat())
    }

    fun computeRefinementResidual(pos: Vector2D): Float {
        if (samples.isEmpty()) return 10f
        var sumSqError = 0.0
        for (s in samples) {
            val distCalc = sqrt((pos.x - s.x).toDouble().pow(2.0) + (pos.y - s.y).toDouble().pow(2.0))
            sumSqError += (s.distanceObserved - distCalc).pow(2.0)
        }
        return sqrt(sumSqError / samples.size).toFloat()
    }

    fun clear() {
        samples.clear()
        m_ata_00 = 0.0
        m_ata_01 = 0.0
        m_ata_11 = 0.0
        v_atb_0 = 0.0
        v_atb_1 = 0.0
    }
}
