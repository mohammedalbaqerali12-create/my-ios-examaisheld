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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examshield.ai.localization.Vector2D
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

import com.examshield.ai.vision.VisionTarget

@Composable
fun SignalArOverlay(
    modifier: Modifier = Modifier,
    supervisorPos: Vector2D,
    devicePos: Vector2D?,
    currentAzimuth: Float, // In degrees
    currentPitch: Float, // In degrees (Vertical tilt)
    deviceType: String = "UNKNOWN",
    rssi: Int = -100,
    visionTargets: List<VisionTarget> = emptyList()
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
        
        // --- LAYER 1: INTERSTELLAR VISION GRID ---
        val imgWidth = 480f
        val imgHeight = 640f
        
        for (visionObj in visionTargets) {
            val isPerson = visionObj.labels.any { it.contains("Fashion", ignoreCase = true) || it.contains("Person", ignoreCase = true) || it.contains("Clothing", ignoreCase = true) }
            val boxColor = if (isPerson) Color(0xFFFF5252) else Color(0xFF00E5FF)
            
            val left = (visionObj.boundingBox.left / imgWidth) * size.width
            val top = (visionObj.boundingBox.top / imgHeight) * size.height
            val right = (visionObj.boundingBox.right / imgWidth) * size.width
            val bottom = (visionObj.boundingBox.bottom / imgHeight) * size.height
            
            val boxWidth = right - left
            val boxHeight = bottom - top

            // NEURAL DATA STREAM (Faint vertical lines inside box)
            if (!isPerson) {
                val streamPulse = ((System.currentTimeMillis() % 1000) / 1000f)
                drawLine(
                    color = boxColor.copy(alpha = 0.1f * (1f - streamPulse)),
                    start = Offset(left + boxWidth * 0.2f, top),
                    end = Offset(left + boxWidth * 0.2f, bottom),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = boxColor.copy(alpha = 0.1f * streamPulse),
                    start = Offset(left + boxWidth * 0.8f, top),
                    end = Offset(left + boxWidth * 0.8f, bottom),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // INTERSTELLAR TARGETING BRACKETS (Instead of a simple box)
            val bracketLen = (boxWidth * 0.2f).coerceAtMost(20.dp.toPx())
            val stroke = 2.dp.toPx()
            
            // Top Left
            drawLine(boxColor, Offset(left, top), Offset(left + bracketLen, top), stroke)
            drawLine(boxColor, Offset(left, top), Offset(left, top + bracketLen), stroke)
            // Top Right
            drawLine(boxColor, Offset(right, top), Offset(right - bracketLen, top), stroke)
            drawLine(boxColor, Offset(right, top), Offset(right, top + bracketLen), stroke)
            // Bottom Left
            drawLine(boxColor, Offset(left, bottom), Offset(left + bracketLen, bottom), stroke)
            drawLine(boxColor, Offset(left, bottom), Offset(left, bottom - bracketLen), stroke)
            // Bottom Right
            drawLine(boxColor, Offset(right, bottom), Offset(right - bracketLen, bottom), stroke)
            drawLine(boxColor, Offset(right, bottom), Offset(right, bottom - bracketLen), stroke)

            // DYNAMIC BRAND RESOLUTION (Interstellar Edition)
            val brandLabel = when {
                isPerson -> "NEURAL_SIGNATURE: BIOMETRIC_DETECTED"
                visionObj.brandHeuristic != null -> {
                    // Correlate with signal data
                    val signalBrand = deviceType.uppercase()
                    when {
                        signalBrand.contains("SAMSUNG") -> "SAMSUNG_UNIFIED // NODE_SECURED"
                        signalBrand.contains("APPLE") || signalBrand.contains("IPHONE") -> "APPLE_INTERCEPT // ENCRYPTED_LINK"
                        signalBrand.contains("GOOGLE") || signalBrand.contains("PIXEL") -> "PIXEL_TERMINAL // SYNC_ACTIVE"
                        signalBrand.contains("HUAWEI") -> "RELIANCE_NODE // HUAWEI_DETECTED"
                        else -> "${visionObj.brandHeuristic} // UNIDENTIFIED_MAKE"
                    }
                }
                else -> "INTERSTELLAR_OBJECT // ID:${visionObj.trackingId ?: 0}"
            }

            val textResult = textMeasurer.measure(
                text = brandLabel,
                style = androidx.compose.ui.text.TextStyle(
                    color = boxColor, fontSize = 7.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp
                )
            )
            
            // Label Background (Cinematic Overlay)
            drawRect(
                color = Color.Black.copy(alpha = 0.6f),
                topLeft = Offset(left, top - textResult.size.height - 4.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(textResult.size.width.toFloat() + 8.dp.toPx(), textResult.size.height.toFloat() + 4.dp.toPx())
            )
            drawText(textResult, topLeft = Offset(left + 4.dp.toPx(), top - textResult.size.height - 2.dp.toPx()))
        }

        val target = devicePos ?: return@Canvas
        
        // --- LAYER 2: SURGICAL AR RETICLE ---
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
        val hFov = 60f
        val vFov = 90f
        val centerX = size.width / 2
        val centerY = size.height / 2

        if (Math.abs(relativeAzimuth) < hFov / 2) {
            val screenX = centerX + (relativeAzimuth / (hFov / 2)) * (size.width / 2)
            val normalizedPitch = currentPitch + 90f
            val screenY = centerY + (normalizedPitch / (vFov / 2)) * (size.height / 2)

            val scale = (1.0 + (5.0 / distance.coerceAtLeast(0.5))).coerceIn(1.0, 5.0).toFloat()
            val baseMarkerSize = 40.dp.toPx() * scale
            val alpha = (1.0 - (distance / 12.0).coerceIn(0.0, 0.8)).toFloat()

            val isFused = deviceType.startsWith("FUSED_")
            val cleanDeviceType = deviceType.replace("FUSED_", "")
            
            val signatureColor = when {
                isFused -> Color(0xFFFF1744)
                cleanDeviceType.contains("PHONE") || cleanDeviceType.contains("WATCH") -> Color(0xFFBB86FC) // Neon Purple for Tech
                else -> Color(0xFF00E5FF)
            }
            
            withTransform({
                translate(left = screenX, top = screenY)
                rotate(degrees = rotation)
            }) {
                // Outer Interstellar Ring
                drawArc(
                    color = signatureColor.copy(alpha = alpha),
                    startAngle = 0f, sweepAngle = 45f, useCenter = false,
                    style = Stroke(width = 3.dp.toPx()),
                    topLeft = Offset(-baseMarkerSize, -baseMarkerSize),
                    size = androidx.compose.ui.geometry.Size(baseMarkerSize * 2, baseMarkerSize * 2)
                )
                drawArc(
                    color = signatureColor.copy(alpha = alpha),
                    startAngle = 120f, sweepAngle = 45f, useCenter = false,
                    style = Stroke(width = 3.dp.toPx()),
                    topLeft = Offset(-baseMarkerSize, -baseMarkerSize),
                    size = androidx.compose.ui.geometry.Size(baseMarkerSize * 2, baseMarkerSize * 2)
                )
                drawArc(
                    color = signatureColor.copy(alpha = alpha),
                    startAngle = 240f, sweepAngle = 45f, useCenter = false,
                    style = Stroke(width = 3.dp.toPx()),
                    topLeft = Offset(-baseMarkerSize, -baseMarkerSize),
                    size = androidx.compose.ui.geometry.Size(baseMarkerSize * 2, baseMarkerSize * 2)
                )
            }

            // Inner Pulsing Compass
            drawCircle(
                color = signatureColor.copy(alpha = alpha * 0.4f),
                radius = baseMarkerSize * 0.6f,
                center = Offset(screenX, screenY),
                style = Stroke(width = 1.dp.toPx())
            )
            
            // Surgical Deadzone
            drawCircle(color = Color.White.copy(alpha = alpha), radius = 3.dp.toPx(), center = Offset(screenX, screenY))

            // Interstellar Telemetry Text
            val telemetry = buildString {
                append("NODE: $cleanDeviceType\n")
                append("DIST: ${"%.2f".format(distance)}M\n")
                append("AZIM: ${"%.1f".format(relativeAzimuth)}°")
                if (isFused) append("\nSTATUS: MAG_FUSION_LOCK")
            }
            
            val textLayoutResult = textMeasurer.measure(
                text = telemetry,
                style = androidx.compose.ui.text.TextStyle(
                    color = signatureColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    background = Color.Black.copy(alpha = 0.5f)
                )
            )
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(screenX + baseMarkerSize * 1.3f, screenY - textLayoutResult.size.height / 2)
            )

            if (distance < 2.0) {
                 val flash = if (System.currentTimeMillis() % 600 > 300) Color.Red else Color.Transparent
                 val warningText = textMeasurer.measure(
                     text = "⚠️ KINETIC_PROXIMITY_ALERT ⚠️",
                     style = androidx.compose.ui.text.TextStyle(
                         color = flash, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, background = Color.Black.copy(alpha = 0.7f)
                     )
                 )
                 drawText(
                     textLayoutResult = warningText,
                     topLeft = Offset(screenX - warningText.size.width / 2, screenY - baseMarkerSize * 2.2f)
                 )
            }

        } else {
            // OFF-SCREEN TELEMETRY ARROWS
            val arrowY = centerY
            val arrowColor = Color(0xFFFFD600)
            if (relativeAzimuth > 0) {
                drawLine(arrowColor, Offset(size.width - 40.dp.toPx(), arrowY), Offset(size.width - 15.dp.toPx(), arrowY), 3.dp.toPx())
                drawLine(arrowColor, Offset(size.width - 25.dp.toPx(), arrowY - 10.dp.toPx()), Offset(size.width - 15.dp.toPx(), arrowY), 3.dp.toPx())
                drawLine(arrowColor, Offset(size.width - 25.dp.toPx(), arrowY + 10.dp.toPx()), Offset(size.width - 15.dp.toPx(), arrowY), 3.dp.toPx())
            } else {
                drawLine(arrowColor, Offset(40.dp.toPx(), arrowY), Offset(15.dp.toPx(), arrowY), 3.dp.toPx())
                drawLine(arrowColor, Offset(25.dp.toPx(), arrowY - 10.dp.toPx()), Offset(15.dp.toPx(), arrowY), 3.dp.toPx())
                drawLine(arrowColor, Offset(25.dp.toPx(), arrowY + 10.dp.toPx()), Offset(15.dp.toPx(), arrowY), 3.dp.toPx())
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
