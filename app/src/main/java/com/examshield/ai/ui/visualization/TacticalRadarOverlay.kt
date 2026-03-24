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
import com.examshield.ai.domain.model.ClassificationResult
import com.examshield.ai.domain.model.SignalTrajectory
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.atan2
import kotlin.math.PI

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
    targets: List<ClassificationResult>,
    currentAzimuth: Float,
    maxRange: Float = 5.0f
) {
    val bioGreen = Color(0xFF00FF41)
    val neonCyan = Color(0xFF00E5FF)
    val alertRed = Color(0xFFFF1744)
    val trajectoryGold = Color(0xFFFFD700)

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

        // 5. DRAW TARGET SIGNATURES
        val angleRad = Math.toRadians(-currentAzimuth.toDouble())

        targets.forEach { result ->
            val pos = (result.rawObject.extraMetadata["location"] as? Vector2D) ?: return@forEach
            
            // Vector relative to supervisor
            val relX = pos.x.toDouble() - supervisorPos.x.toDouble()
            val relY = pos.y.toDouble() - supervisorPos.y.toDouble()
            
            // Adjust for phone rotation (Azimuth)
            val rotatedX = (relX * cos(angleRad) - relY * sin(angleRad)).toFloat()
            val rotatedY = (relX * sin(angleRad) + relY * cos(angleRad)).toFloat()

            val targetDrawPos = Offset(
                center.x + (rotatedX * rangeScale),
                center.y - (rotatedY * rangeScale) // Invert Y for UI coord system
            )

            // Confidence-based color
            val isHighThreat = result.confidenceScore > 80 || result.riskLevel == com.examshield.ai.domain.model.RiskLevel.LEVEL_4_CONFIRMED_THREAT
            val sigColor = if (isHighThreat) alertRed else bioGreen

            // Pulse on target
            val targetPulse = (pulseRadius + 0.5f) % 1.0f
            drawCircle(
                color = sigColor.copy(alpha = 1f - targetPulse),
                radius = 12f + (targetPulse * 20f),
                center = targetDrawPos,
                style = Stroke(width = 2f)
            )

            // Target Core
            drawCircle(
                color = sigColor,
                radius = 8f,
                center = targetDrawPos
            )
            
            // Draw Trajectory Arrow (Behavioral Analysis)
            if (result.trajectory == SignalTrajectory.APPROACHING || result.trajectory == SignalTrajectory.RECEDING) {
                val isApproaching = result.trajectory == SignalTrajectory.APPROACHING
                val arrowLen = 20f
                val arrowAngle = atan2(targetDrawPos.y - center.y, targetDrawPos.x - center.x)
                
                // Direction of arrow
                val dir = if (isApproaching) -1f else 1f // Inwards for approaching, outwards for receding
                
                val arrowTip = Offset(
                    targetDrawPos.x + cos(arrowAngle) * (dir * 25f),
                    targetDrawPos.y + sin(arrowAngle) * (dir * 25f)
                )
                
                drawLine(
                    color = trajectoryGold,
                    start = targetDrawPos,
                    end = arrowTip,
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
                
                // Arrow head
                val headAngle = if (isApproaching) arrowAngle else arrowAngle + PI.toFloat()
                drawLine(
                    color = trajectoryGold,
                    start = arrowTip,
                    end = Offset(arrowTip.x - cos(headAngle - 0.5f) * 10f, arrowTip.y - sin(headAngle - 0.5f) * 10f),
                    strokeWidth = 3f
                )
                drawLine(
                    color = trajectoryGold,
                    start = arrowTip,
                    end = Offset(arrowTip.x - cos(headAngle + 0.5f) * 10f, arrowTip.y - sin(headAngle + 0.5f) * 10f),
                    strokeWidth = 3f
                )
            }
        }
    }
}
