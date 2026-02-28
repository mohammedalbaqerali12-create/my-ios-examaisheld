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
import com.examshield.ai.localization.TrilaterationSample

@Composable
fun HallGridView(
    hall: HallModel,
    supervisorPos: Vector2D,
    devicePos: Vector2D?,
    errorRadius: Float,
    samples: List<TrilaterationSample> = emptyList(),
    identifiedTargets: List<Vector2D> = emptyList(),
    isWalkMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(16.dp)
            .aspectRatio(hall.width / hall.height)
            .background(Color(0xFF010810))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scaleX = size.width / hall.width
            val scaleY = size.height / hall.height
            
            // Draw Grid (Subtle Blue)
            val gridColor = Color.Cyan.copy(alpha = 0.1f)
            for (x in 0..hall.width.toInt()) {
                drawLine(gridColor, Offset(x * scaleX, 0f), Offset(x * scaleX, size.height), 1.dp.toPx())
            }
            for (y in 0..hall.height.toInt()) {
                drawLine(gridColor, Offset(0f, y * scaleY), Offset(size.width, y * scaleY), 1.dp.toPx())
            }

            // Draw Bounds
            drawRect(Color.Cyan.copy(alpha = 0.3f), Offset.Zero, size, style = Stroke(2.dp.toPx()))

            // --- PART 6: DRAW WALK SAMPLES ---
            samples.forEach { sample ->
                val sCenter = Offset(sample.x * scaleX, sample.y * scaleY)
                val sRadius = sample.distanceObserved * scaleX
                
                // Draw Sample Point
                drawCircle(Color.Yellow.copy(alpha = 0.6f), 4.dp.toPx(), sCenter)
                
                // Draw Intersection Circle
                drawCircle(
                    color = Color.Yellow.copy(alpha = 0.1f),
                    radius = sRadius,
                    center = sCenter,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // --- DRAW PERSISTENT IDENTIFIED TARGETS ---
            identifiedTargets.forEach { target ->
                val tCenter = Offset(target.x * scaleX, target.y * scaleY)
                val tSize = 12.dp.toPx()
                
                // Draw Target Glow
                drawCircle(Color.Red.copy(alpha = 0.2f), tSize * 1.5f, tCenter)
                
                // Draw Dynamic Diamond Target
                drawRect(
                    color = Color.Red,
                    topLeft = Offset(tCenter.x - tSize/2, tCenter.y - tSize/2),
                    size = Size(tSize, tSize)
                )
            }

            // Draw Supervisor Position
            drawCircle(
                color = Color.Green,
                radius = 7.dp.toPx(),
                center = Offset(supervisorPos.x * scaleX, supervisorPos.y * scaleY)
            )

            // Draw Device Position & Accuracy Circle
            devicePos?.let { pos ->
                val center = Offset(pos.x * scaleX, pos.y * scaleY)
                
                // Error range (Pulse animation if WalkMode)
                drawCircle(
                    color = Color.Red.copy(alpha = 0.1f),
                    radius = errorRadius * scaleX,
                    center = center
                )
                
                // Intersection Target
                drawCircle(Color.Red, radius = 10.dp.toPx(), center = center)
                
                // Crosshair at device
                val chLen = 14.dp.toPx()
                drawLine(Color.White, Offset(center.x - chLen, center.y), Offset(center.x + chLen, center.y), 1.5.dp.toPx())
                drawLine(Color.White, Offset(center.x, center.y - chLen), Offset(center.x, center.y + chLen), 1.5.dp.toPx())
            }
        }
    }
}
