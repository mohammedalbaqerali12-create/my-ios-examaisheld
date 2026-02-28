package com.examshield.ai.ui.visualization

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.examshield.ai.localization.Vector2D
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SignalArOverlay(
    modifier: Modifier = Modifier,
    supervisorPos: Vector2D,
    devicePos: Vector2D?,
    currentAzimuth: Float // In degrees
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val target = devicePos ?: return@Canvas
        
        // Calculate relative vector
        val dx = target.x - supervisorPos.x
        val dy = target.y - supervisorPos.y
        
        // Target bearing in degrees (0 = North/Up, increases clockwise)
        // atan2(dy, dx) returns angle in radians from X-axis
        // We need to map our grid (X=Right, Y=Up) to Azimuth
        val targetBearingRad = atan2(dx.toDouble(), dy.toDouble())
        var targetBearingDeg = Math.toDegrees(targetBearingRad).toFloat()
        if (targetBearingDeg < 0) targetBearingDeg += 360f
        
        // Relative angle between current heading and target
        var relativeAngle = targetBearingDeg - currentAzimuth
        if (relativeAngle > 180) relativeAngle -= 360
        if (relativeAngle < -180) relativeAngle += 360
        
        // FOV check (Horizontal FOV approx 60 degrees)
        val hFov = 60f
        if (Math.abs(relativeAngle) < hFov / 2) {
            // Project onto screen width
            val centerX = size.width / 2
            val centerY = size.height / 2
            
            val screenX = centerX + (relativeAngle / (hFov / 2)) * (size.width / 2)
            
            // Draw AR Marker (Radar Circle)
            val markerSize = 40.dp.toPx()
            val distance = Math.sqrt(dx * dx + dy * dy.toDouble())
            val alpha = (1.0 - (distance / 5.0).coerceIn(0.0, 0.8)).toFloat()
            
            drawCircle(
                color = Color.Cyan.copy(alpha = alpha),
                radius = markerSize,
                center = Offset(screenX, centerY),
                style = Stroke(width = 4.dp.toPx())
            )
            
            drawCircle(
                color = Color.Red.copy(alpha = 0.8f),
                radius = 8.dp.toPx(),
                center = Offset(screenX, centerY)
            )
            
            // Draw crosshair
            drawLine(
                color = Color.White,
                start = Offset(screenX - 20.dp.toPx(), centerY),
                end = Offset(screenX + 20.dp.toPx(), centerY),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color.White,
                start = Offset(screenX, centerY - 20.dp.toPx()),
                end = Offset(screenX, centerY + 20.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}
