package com.examshield.ai.ui.visualization

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.examshield.ai.localization.HallModel
import com.examshield.ai.localization.Vector2D

@Composable
fun HeatmapRenderer(
    hall: HallModel,
    targetPosition: Vector2D?,
    confidence: Float, // 0.0 to 1.0
    modifier: Modifier = Modifier
) {
    if (targetPosition == null) return

    // Sci-Fi Animations Phase
    val infiniteTransition = rememberInfiniteTransition()
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val rotatePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(modifier = modifier.fillMaxSize().background(Color(0xFF020610))) {
        val scaleX = size.width / hall.width
        val scaleY = size.height / hall.height
        
        val center = Offset(targetPosition.x * scaleX, targetPosition.y * scaleY)
        val maxRadius = (hall.width / 3f) * scaleX
        val intensity = (confidence).coerceIn(0.1f, 1f)

        // 1. Draw Sci-Fi Background Grid
        val gridSize = 40.dp.toPx()
        for (i in 0..(size.width / gridSize).toInt()) {
            drawLine(
                color = Color.Cyan.copy(alpha = 0.05f),
                start = Offset(i * gridSize, 0f),
                end = Offset(i * gridSize, size.height),
                strokeWidth = 1f
            )
        }
        for (i in 0..(size.height / gridSize).toInt()) {
            drawLine(
                color = Color.Cyan.copy(alpha = 0.05f),
                start = Offset(0f, i * gridSize),
                end = Offset(size.width, i * gridSize),
                strokeWidth = 1f
            )
        }

        // 2. Main Thermal Core (Deep Red/Purple/Cyan based on intensity)
        val coreColor = if (intensity > 0.8f) Color(0xFFFF1744) // Deep Crimson for high threat
                        else if (intensity > 0.5f) Color(0xFFFF9100) // Orange
                        else Color(0xFF00E5FF) // Cyan

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    coreColor.copy(alpha = 0.8f * intensity),
                    coreColor.copy(alpha = 0.3f * intensity),
                    Color.Transparent
                ),
                center = center,
                radius = maxRadius
            ),
            radius = maxRadius,
            center = center
        )

        // 3. Pulsing Sonar Rings
        val currentPulseRadius = maxRadius * pulsePhase
        drawCircle(
            color = coreColor.copy(alpha = 1f - pulsePhase),
            radius = currentPulseRadius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
        )
        
        // 4. Inner Tactical Reticle (Rotates)
        withTransform({
            rotate(rotatePhase, center)
        }) {
            val reticleSize = 30.dp.toPx()
            drawLine(
                color = coreColor,
                start = Offset(center.x - reticleSize, center.y),
                end = Offset(center.x + reticleSize, center.y),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = coreColor,
                start = Offset(center.x, center.y - reticleSize),
                end = Offset(center.x, center.y + reticleSize),
                strokeWidth = 2.dp.toPx()
            )
            drawCircle(
                color = coreColor,
                radius = reticleSize * 0.7f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
            )
        }

        // 5. Digital Lock-on Highlight for Maximum Confidence
        if (confidence > 0.85f) {
            val cornerSize = 15.dp.toPx()
            val lockColor = Color(0xFFFF1744) // Bright Red
            
            // Draw Target Corners
            val tl = Offset(center.x - 20.dp.toPx(), center.y - 20.dp.toPx())
            drawLine(lockColor, tl, Offset(tl.x + cornerSize, tl.y), strokeWidth = 3f)
            drawLine(lockColor, tl, Offset(tl.x, tl.y + cornerSize), strokeWidth = 3f)

            val tr = Offset(center.x + 20.dp.toPx(), center.y - 20.dp.toPx())
            drawLine(lockColor, tr, Offset(tr.x - cornerSize, tr.y), strokeWidth = 3f)
            drawLine(lockColor, tr, Offset(tr.x, tr.y + cornerSize), strokeWidth = 3f)
            
            val bl = Offset(center.x - 20.dp.toPx(), center.y + 20.dp.toPx())
            drawLine(lockColor, bl, Offset(bl.x + cornerSize, bl.y), strokeWidth = 3f)
            drawLine(lockColor, bl, Offset(bl.x, bl.y - cornerSize), strokeWidth = 3f)

            val br = Offset(center.x + 20.dp.toPx(), center.y + 20.dp.toPx())
            drawLine(lockColor, br, Offset(br.x - cornerSize, br.y), strokeWidth = 3f)
            drawLine(lockColor, br, Offset(br.x, br.y - cornerSize), strokeWidth = 3f)
        }
    }
}
