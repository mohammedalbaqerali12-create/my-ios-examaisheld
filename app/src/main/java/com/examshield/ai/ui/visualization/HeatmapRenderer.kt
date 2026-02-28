package com.examshield.ai.ui.visualization

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

    Canvas(modifier = modifier.fillMaxSize()) {
        val scaleX = size.width / hall.width
        val scaleY = size.height / hall.height
        
        val center = Offset(targetPosition.x * scaleX, targetPosition.y * scaleY)
        val maxRadius = (hall.width / 4f) * scaleX
        val intensity = (confidence).coerceIn(0.1f, 1f)

        // Draw radial intensity
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Red.copy(alpha = 0.6f * intensity),
                    Color.Yellow.copy(alpha = 0.3f * intensity),
                    Color.Transparent
                ),
                center = center,
                radius = maxRadius
            ),
            radius = maxRadius,
            center = center
        )

        // Draw individual seat highlights if confidence is HIGH
        if (confidence > 0.8f) {
            drawRect(
                color = Color.Red.copy(alpha = 0.4f),
                topLeft = Offset(center.x - 10.dp.toPx(), center.y - 10.dp.toPx()),
                size = Size(20.dp.toPx(), 20.dp.toPx())
            )
        }
    }
}
