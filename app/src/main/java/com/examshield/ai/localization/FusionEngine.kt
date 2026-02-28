package com.examshield.ai.localization

import kotlin.math.cos
import kotlin.math.sin

class FusionEngine {
    
    /**
     * Compute weighted final position
     * @param gradientBearing Degrees (estimated heading to device)
     * @param gradientDist Estimate from RSSI at current point
     * @param trilatPos Estimated (x,y) from multi-point
     * @param supervisorPos Current location
     */
    fun fuseEstimates(
        supervisorPos: Vector2D,
        gradientBearing: Float?,
        gradientDist: Float,
        trilatPos: Vector2D?,
        trilatResidual: Float,
        rssiVariance: Float
    ): Vector2D {
        if (trilatPos != null && trilatResidual < 1.0f) {
            // If trilat is very stable, trust it significantly
            return trilatPos
        }

        if (gradientBearing != null) {
            // Compute candidate from gradient (pointing to device from current pos)
            val rad = Math.toRadians(gradientBearing.toDouble())
            val gradCandidateX = supervisorPos.x + gradientDist * cos(rad).toFloat()
            val gradCandidateY = supervisorPos.y + gradientDist * sin(rad).toFloat()

            if (trilatPos != null) {
                // Weighted average
                val wTrilat = 1.0f / (trilatResidual + 0.1f)
                val wGrad = 1.0f / (rssiVariance + 1.0f)
                
                val weightSum = wTrilat + wGrad
                return Vector2D(
                    (trilatPos.x * wTrilat + gradCandidateX * wGrad) / weightSum,
                    (trilatPos.y * wTrilat + gradCandidateY * wGrad) / weightSum
                )
            }
            return Vector2D(gradCandidateX, gradCandidateY)
        }

        return trilatPos ?: supervisorPos
    }

    fun fuseWithWalkLogic(
        supervisorPos: Vector2D,
        gradientBearing: Float?,
        gradientDist: Float,
        trilatPos: Vector2D?,
        trilatResidual: Float,
        isWalkMode: Boolean
    ): Vector2D {
        if (trilatPos != null) {
            if (isWalkMode) {
                // In Walk Mode, we trust the multi-point triangulation results significantly more
                // as long as the residual is manageable.
                val weightTrilat = 1.0f / (trilatResidual + 0.1f)
                val weightGrad = 0.2f // Dampen gradient in pure walk mode
                
                if (gradientBearing != null) {
                    val rad = Math.toRadians(gradientBearing.toDouble())
                    val gx = (supervisorPos.x + gradientDist * cos(rad).toFloat())
                    val gy = (supervisorPos.y + gradientDist * sin(rad).toFloat())
                    
                    val wSum = weightTrilat + weightGrad
                    return Vector2D(
                        (trilatPos.x * weightTrilat + gx * weightGrad) / wSum,
                        (trilatPos.y * weightTrilat + gy * weightGrad) / wSum
                    )
                }
                return trilatPos
            }
            return fuseEstimates(supervisorPos, gradientBearing, gradientDist, trilatPos, trilatResidual, 5.0f)
        }
        return supervisorPos
    }

    fun computeConfidenceScore(
        sampleCount: Int,
        rssiVariance: Float,
        trilatResidual: Float
    ): Int {
        var score = (sampleCount * 12).coerceAtMost(60) // Linear growth up to 60% with samples
        
        if (trilatResidual < 0.5f) score += 30
        else if (trilatResidual < 1.0f) score += 20
        else if (trilatResidual < 2.0f) score += 10
        
        if (rssiVariance < 4.0f) score += 10
        
        return score.coerceIn(0, 100)
    }
}
