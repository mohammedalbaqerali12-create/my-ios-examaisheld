package com.examshield.ai.ui.visualization

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.examshield.ai.localization.HallModel
import com.examshield.ai.localization.Vector2D

@Composable
fun HallGridView(
    hall: HallModel,
    supervisorPos: Vector2D,
    devicePos: Vector2D?,
    errorRadius: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(16.dp)
            .aspectRatio(hall.width / hall.height)
            .background(Color.Black.copy(alpha = 0.4f))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scaleX = size.width / hall.width
            val scaleY = size.height / hall.height
            
            // Draw Grid
            val gridColor = Color.Cyan.copy(alpha = 0.2f)
            for (x in 0..hall.width.toInt()) {
                drawLine(gridColor, Offset(x * scaleX, 0f), Offset(x * scaleX, size.height), 1.dp.toPx())
            }
            for (y in 0..hall.height.toInt()) {
                drawLine(gridColor, Offset(0f, y * scaleY), Offset(size.width, y * scaleY), 1.dp.toPx())
            }

            // Draw Bounds
            drawRect(Color.Cyan.copy(alpha = 0.4f), Offset.Zero, size, style = Stroke(2.dp.toPx()))

            // Draw Supervisor Position
            drawCircle(
                color = Color.Green,
                radius = 8.dp.toPx(),
                center = Offset(supervisorPos.x * scaleX, supervisorPos.y * scaleY)
            )

            // Draw Device Position & Accuracy Circle
            devicePos?.let { pos ->
                val center = Offset(pos.x * scaleX, pos.y * scaleY)
                
                // Confidence radius (Error range)
                drawCircle(
                    color = Color.Red.copy(alpha = 0.15f),
                    radius = errorRadius * scaleX,
                    center = center
                )
                
                drawCircle(
                    color = Color.Red,
                    radius = 12.dp.toPx(),
                    center = center
                )
                
                // Crosshair at device
                val chLen = 15.dp.toPx()
                drawLine(Color.White, Offset(center.x - chLen, center.y), Offset(center.x + chLen, center.y), 2.dp.toPx())
                drawLine(Color.White, Offset(center.x, center.y - chLen), Offset(center.x, center.y + chLen), 2.dp.toPx())
            }
        }
    }
}
