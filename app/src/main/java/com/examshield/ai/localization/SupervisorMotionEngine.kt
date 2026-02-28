package com.examshield.ai.localization

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.sin

data class Vector2D(val x: Float, val y: Float)

class SupervisorMotionEngine {
    private val _currentPosition = MutableStateFlow(Vector2D(0f, 0f))
    val currentPosition: StateFlow<Vector2D> = _currentPosition.asStateFlow()

    private val stepLength = 0.75f // Default step length in meters

    /**
     * Update the supervisor's position when a step is detected.
     * @param heading Degrees (Compass bearing)
     */
    fun onStepDetected(heading: Float, hall: HallModel) {
        val current = _currentPosition.value
        val headingRad = Math.toRadians(heading.toDouble())
        
        // Android Azimuth: 0 is North, clockwise to 360.
        // Math coordinates: 0 is East (+X), counter-clockwise.
        // Convert to standard math coordinates (0 at +X, counter-clockwise)
        // 0 North (Compass) = 90 deg (Math)
        // 90 East (Compass) = 0 deg (Math)
        val mathHeadingRad = Math.toRadians((90.0 - heading.toDouble()))
        
        val dx = (stepLength * cos(mathHeadingRad)).toFloat()
        val dy = (stepLength * sin(mathHeadingRad)).toFloat()

        val nextX = (current.x + dx).coerceIn(0f, hall.width)
        val nextY = (current.y + dy).coerceIn(0f, hall.height)

        _currentPosition.value = Vector2D(nextX, nextY)
    }

    fun resetPosition(x: Float = 0f, y: Float = 0f) {
        _currentPosition.value = Vector2D(x, y)
    }
}
