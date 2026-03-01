package com.examshield.ai.ui.visualization

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    currentAzimuth: Float, // In degrees
    currentPitch: Float, // In degrees (Vertical tilt)
    deviceType: String = "UNKNOWN",
    rssi: Int = -100
) {
    val textMeasurer = rememberTextMeasurer()
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val target = devicePos ?: return@Canvas
        
        // Calculate relative vector
        val dx = target.x - supervisorPos.x
        val dy = target.y - supervisorPos.y
        
        val targetBearingRad = atan2(dx.toDouble(), dy.toDouble())
        var targetBearingDeg = Math.toDegrees(targetBearingRad).toFloat()
        if (targetBearingDeg < 0) targetBearingDeg += 360f
        
        var relativeAzimuth = targetBearingDeg - currentAzimuth
        if (relativeAngleWrap(relativeAzimuth) > 180) relativeAzimuth -= 360
        if (relativeAngleWrap(relativeAzimuth) < -180) relativeAzimuth += 360
        relativeAzimuth = relativeAngleWrap(relativeAzimuth)
        
        val distance = Math.sqrt(dx * dx + dy * dy.toDouble())
        
        // Horizontal FOV ~ 60 deg, Vertical FOV ~ 90 deg
        val hFov = 60f
        val vFov = 90f
        
        val centerX = size.width / 2
        val centerY = size.height / 2

        if (Math.abs(relativeAzimuth) < hFov / 2) {
            // Screen mapping
            val screenX = centerX + (relativeAzimuth / (hFov / 2)) * (size.width / 2)
            
            // Pitch mapping: 0 is flat forwarding. If phone tilts UP (Pitch < 0), marker goes DOWN.
            // Adjust based on typical holding angle (-70 to -110 is standing up).
            // Let's assume -90 is looking straight ahead.
            val normalizedPitch = currentPitch + 90f
            val screenY = centerY + (normalizedPitch / (vFov / 2)) * (size.height / 2)

            // Dynamic Scaling based on distance (closer = much bigger)
            val scale = (1.0 + (5.0 / distance.coerceAtLeast(0.5))).coerceIn(1.0, 5.0).toFloat()
            val baseMarkerSize = 40.dp.toPx() * scale
            val alpha = (1.0 - (distance / 10.0).coerceIn(0.0, 0.8)).toFloat()

            // Main Reticle Color based on Threat
            val signatureColor = if (deviceType.contains("PHONE") || deviceType.contains("WATCH")) Color.Red else Color.Cyan

            withTransform({
                translate(left = screenX, top = screenY)
                rotate(degrees = rotation)
            }) {
                // Outer rotating dashed ring
                drawArc(
                    color = signatureColor.copy(alpha = alpha),
                    startAngle = 0f, sweepAngle = 90f, useCenter = false,
                    style = Stroke(width = 3.dp.toPx()),
                    topLeft = Offset(-baseMarkerSize, -baseMarkerSize),
                    size = androidx.compose.ui.geometry.Size(baseMarkerSize * 2, baseMarkerSize * 2)
                )
                drawArc(
                    color = signatureColor.copy(alpha = alpha),
                    startAngle = 180f, sweepAngle = 90f, useCenter = false,
                    style = Stroke(width = 3.dp.toPx()),
                    topLeft = Offset(-baseMarkerSize, -baseMarkerSize),
                    size = androidx.compose.ui.geometry.Size(baseMarkerSize * 2, baseMarkerSize * 2)
                )
            }

            // Inner solid ring
            drawCircle(
                color = signatureColor.copy(alpha = alpha * 0.8f),
                radius = baseMarkerSize * 0.7f,
                center = Offset(screenX, screenY),
                style = Stroke(width = 2.dp.toPx())
            )
            
            // Center Dot
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = Offset(screenX, screenY)
            )

            // Crosshairs
            drawLine(signatureColor, Offset(screenX - baseMarkerSize * 1.5f, screenY), Offset(screenX - baseMarkerSize * 0.8f, screenY), 2.dp.toPx())
            drawLine(signatureColor, Offset(screenX + baseMarkerSize * 1.5f, screenY), Offset(screenX + baseMarkerSize * 0.8f, screenY), 2.dp.toPx())
            drawLine(signatureColor, Offset(screenX, screenY - baseMarkerSize * 1.5f), Offset(screenX, screenY - baseMarkerSize * 0.8f), 2.dp.toPx())
            drawLine(signatureColor, Offset(screenX, screenY + baseMarkerSize * 1.5f), Offset(screenX, screenY + baseMarkerSize * 0.8f), 2.dp.toPx())

            // Sci-Fi Text Metrics
            val distanceText = "RANGE: ${"%.1f".format(distance)}m"
            val textLayoutResult = textMeasurer.measure(
                text = "$deviceType\n$distanceText",
                style = androidx.compose.ui.text.TextStyle(
                    color = signatureColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    background = Color.Black.copy(alpha = 0.5f)
                )
            )
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(screenX + baseMarkerSize * 1.2f, screenY - textLayoutResult.size.height / 2)
            )

            // Threat Warning if very close
            if (distance < 2.0) {
                 val flash = if (System.currentTimeMillis() % 1000 > 500) Color.Red else Color.Yellow
                 val warningText = textMeasurer.measure(
                     text = "⚠️ CRITICAL PROXIMITY ⚠️",
                     style = androidx.compose.ui.text.TextStyle(
                         color = flash, fontSize = 16.sp, fontWeight = FontWeight.Black, background = Color.Black.copy(alpha = 0.8f)
                     )
                 )
                 drawText(
                     textLayoutResult = warningText,
                     topLeft = Offset(screenX - warningText.size.width / 2, screenY - baseMarkerSize * 2f)
                 )
            }

        } else {
            // OUT OF VIEW INDICATORS (Arrows)
            val arrowY = centerY
            if (relativeAzimuth > 0) {
                drawLine(Color.Yellow, Offset(size.width - 40.dp.toPx(), arrowY), Offset(size.width - 10.dp.toPx(), arrowY), 4.dp.toPx())
                drawLine(Color.Yellow, Offset(size.width - 20.dp.toPx(), arrowY - 10.dp.toPx()), Offset(size.width - 10.dp.toPx(), arrowY), 4.dp.toPx())
                drawLine(Color.Yellow, Offset(size.width - 20.dp.toPx(), arrowY + 10.dp.toPx()), Offset(size.width - 10.dp.toPx(), arrowY), 4.dp.toPx())
            } else {
                drawLine(Color.Yellow, Offset(40.dp.toPx(), arrowY), Offset(10.dp.toPx(), arrowY), 4.dp.toPx())
                drawLine(Color.Yellow, Offset(20.dp.toPx(), arrowY - 10.dp.toPx()), Offset(10.dp.toPx(), arrowY), 4.dp.toPx())
                drawLine(Color.Yellow, Offset(20.dp.toPx(), arrowY + 10.dp.toPx()), Offset(10.dp.toPx(), arrowY), 4.dp.toPx())
            }
        }
    }
}

private fun relativeAngleWrap(angle: Float): Float {
    var result = angle
    while (result > 180) result -= 360
    while (result < -180) result += 360
    return result
}
