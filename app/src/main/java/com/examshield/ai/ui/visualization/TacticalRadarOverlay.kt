package com.examshield.ai.ui.visualization

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examshield.ai.localization.Vector2D
import kotlin.math.cos
import kotlin.math.sin

/**
 * ASTRA NEXUS: TACTICAL POLAR RADAR (ALIEN EDITION)
 * 
 * A high-precision circular radar centering on the operator.
 * Maps relative device positions (x, y) into a pulsing 5m range grid.
 */
@Composable
fun TacticalRadarOverlay(
    modifier: Modifier = Modifier,
    supervisorPos: Vector2D,
    devicePos: Vector2D?,
    currentAzimuth: Float,
    targetDeviceType: String,
    confidence: Float,
    maxRange: Float = 5.0f // New parameter for dynamic scaling
) {
    val bioGreen = Color(0xFF00FF41)
    val neonCyan = Color(0xFF00E5FF)
    val alertRed = Color(0xFFFF1744)

    val infiniteTransition = rememberInfiniteTransition(label = "RadarTransitions")
    
    // SCANNING BEAM EFFECT
    val scannerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ScannerSweep"
    )

    // PULSE EFFECT
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarPulse"
    )

    Canvas(modifier = modifier.fillMaxSize().padding(24.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = size.width / 2
        val rangeScale = maxRadius / maxRange // Dynamic scale based on selected range

        // 1. DRAW ENERGY RINGS (Concentric Circles)
        for (i in 1..5) {
            val r = (i * size.width / 10)
            drawCircle(
                color = neonCyan.copy(alpha = 0.1f * (6-i)),
                radius = r,
                center = center,
                style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
            )
        }

        // 2. DRAW RADAR SWEEP BEAM
        rotate(degrees = scannerAngle, pivot = center) {
            val sweepGradient = Brush.sweepGradient(
                0.0f to Color.Transparent,
                0.9f to Color.Transparent,
                1.0f to neonCyan.copy(alpha = 0.5f)
            )
            drawCircle(
                brush = sweepGradient,
                radius = maxRadius,
                center = center
            )
        }

        // 3. DRAW PULSE WAVE
        drawCircle(
            color = neonCyan.copy(alpha = (1f - pulseRadius) * 0.3f),
            radius = pulseRadius * maxRadius,
            center = center,
            style = Stroke(width = 2f)
        )

        // 4. DRAW CROSSHAIR (Operator Center)
        drawLine(color = neonCyan, start = Offset(center.x - 10, center.y), end = Offset(center.x + 10, center.y), strokeWidth = 2f)
        drawLine(color = neonCyan, start = Offset(center.x, center.y - 10), end = Offset(center.x, center.y + 10), strokeWidth = 2f)

        // 5. DRAW TARGET SIGNATURE
        devicePos?.let { pos ->
            // Vector relative to supervisor
            val relX = pos.x - supervisorPos.x
            val relY = pos.y - supervisorPos.y
            
            // Adjust for phone rotation (Azimuth)
            val angleRad = Math.toRadians(-currentAzimuth.toDouble())
            val rotatedX = (relX * cos(angleRad) - relY * sin(angleRad)).toFloat()
            val rotatedY = (relX * sin(angleRad) + relY * cos(angleRad)).toFloat()

            val targetDrawPos = Offset(
                center.x + (rotatedX * rangeScale),
                center.y - (rotatedY * rangeScale) // Invert Y for UI coord system
            )

            // Pulse on target
            val targetPulse = (pulseRadius + 0.5f) % 1.0f
            drawCircle(
                color = if (confidence > 0.8f) alertRed.copy(alpha = 1f - targetPulse) else bioGreen.copy(alpha = 1f - targetPulse),
                radius = 15f + (targetPulse * 30f),
                center = targetDrawPos,
                style = Stroke(width = 3f)
            )

            // Target Core
            drawCircle(
                color = if (confidence > 0.8f) alertRed else bioGreen,
                radius = 10f,
                center = targetDrawPos
            )
            
            // Pulse Path (Neural Link)
            drawLine(
                color = if (confidence > 0.8f) alertRed.copy(alpha = 0.4f) else neonCyan.copy(alpha = 0.4f),
                start = center,
                end = targetDrawPos,
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
            )
        }
    }
}
