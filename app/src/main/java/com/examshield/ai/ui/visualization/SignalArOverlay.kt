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
import com.examshield.ai.domain.ai.CentralNeuralLink

@Composable
fun SignalArOverlay(
    modifier: Modifier = Modifier,
    supervisorPos: Vector2D,
    devicePos: Vector2D?,
    currentAzimuth: Float, // In degrees
    currentPitch: Float, // In degrees (Vertical tilt)
    deviceType: String = "UNKNOWN",
    rssi: Int = -100,
    visionTargets: List<VisionTarget> = emptyList(),
    neuralState: CentralNeuralLink.NeuralState = CentralNeuralLink.NeuralState.STABLE
) {
    val textMeasurer = rememberTextMeasurer()
    val infiniteTransition = rememberInfiniteTransition()
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (neuralState == CentralNeuralLink.NeuralState.PRIME_SYNERGY) 1500 else 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val primeGlow by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        
        // --- LAYER 0: HUD DATALINK (Top Right) ---
        drawDatalinkModule(textMeasurer, neuralState, size.width)

        // --- LAYER 1: INTERSTELLAR VISION GRID ---
        val imgWidth = 480f
        val imgHeight = 640f
        
        for (visionObj in visionTargets) {
            val isPerson = visionObj.labels.any { it.contains("Fashion", ignoreCase = true) || it.contains("Person", ignoreCase = true) || it.contains("Clothing", ignoreCase = true) }
            val boxColor = when {
                isPerson -> com.examshield.ai.ui.theme.ThreatRed
                neuralState == CentralNeuralLink.NeuralState.PRIME_SYNERGY -> com.examshield.ai.ui.theme.PrimeGold
                else -> com.examshield.ai.ui.theme.NeonCyan
            }
            
            val left = (visionObj.boundingBox.left / imgWidth) * size.width
            val top = (visionObj.boundingBox.top / imgHeight) * size.height
            val right = (visionObj.boundingBox.right / imgWidth) * size.width
            val bottom = (visionObj.boundingBox.bottom / imgHeight) * size.height
            
            val boxWidth = right - left
            val boxHeight = bottom - top
            
            // SPECTRAL SCANLINE (Inside Box)
            val scanlineY = top + (boxHeight * ((System.currentTimeMillis() % 2000) / 2000f))
            drawLine(
                color = boxColor.copy(alpha = 0.4f),
                start = Offset(left, scanlineY),
                end = Offset(right, scanlineY),
                strokeWidth = 1.dp.toPx()
            )

            // INTERSTELLAR TARGETING BRACKETS
            val bracketLen = (boxWidth * 0.25f).coerceAtMost(25.dp.toPx())
            val stroke = (if (neuralState == CentralNeuralLink.NeuralState.PRIME_SYNERGY) 3.dp else 2.dp).toPx()
            
            // Brackets with slight glow if PRIME
            val finalBoxColor = if (neuralState == CentralNeuralLink.NeuralState.PRIME_SYNERGY) {
                boxColor.copy(alpha = 0.7f + (0.3f * primeGlow))
            } else boxColor

            // Top Left
            drawLine(finalBoxColor, Offset(left, top), Offset(left + bracketLen, top), stroke)
            drawLine(finalBoxColor, Offset(left, top), Offset(left, top + bracketLen), stroke)
            // Top Right
            drawLine(finalBoxColor, Offset(right, top), Offset(right - bracketLen, top), stroke)
            drawLine(finalBoxColor, Offset(right, top), Offset(right, top + bracketLen), stroke)
            // Bottom Left
            drawLine(finalBoxColor, Offset(left, bottom), Offset(left + bracketLen, bottom), stroke)
            drawLine(finalBoxColor, Offset(left, bottom), Offset(left, bottom - bracketLen), stroke)
            // Bottom Right
            drawLine(finalBoxColor, Offset(right, bottom), Offset(right - bracketLen, bottom), stroke)
            drawLine(finalBoxColor, Offset(right, bottom), Offset(right, bottom - bracketLen), stroke)

            // DYNAMIC IDENTITY RESOLUTION
            val idLabel = when {
                isPerson -> "BIOMETRIC_ID: POSITIVE_MATCH"
                visionObj.brandHeuristic != null -> "NEXUS_QUERY: ${visionObj.brandHeuristic}"
                else -> "SCANNING_OBJECT // ID_PENDING"
            }

            val textResult = textMeasurer.measure(
                text = idLabel,
                style = androidx.compose.ui.text.TextStyle(
                    color = boxColor, fontSize = 8.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp
                )
            )
            
            drawRect(
                color = Color.Black.copy(alpha = 0.7f),
                topLeft = Offset(left, top - textResult.size.height - 6.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(textResult.size.width.toFloat() + 10.dp.toPx(), textResult.size.height.toFloat() + 4.dp.toPx())
            )
            drawText(textResult, topLeft = Offset(left + 5.dp.toPx(), top - textResult.size.height - 4.dp.toPx()))
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
        val hFov = 65f
        val vFov = 95f
        val centerX = size.width / 2
        val centerY = size.height / 2

        if (Math.abs(relativeAzimuth) < hFov / 2) {
            val screenX = centerX + (relativeAzimuth / (hFov / 2)) * (size.width / 2)
            val normalizedPitch = currentPitch + 90f
            val screenY = centerY + (normalizedPitch / (vFov / 2)) * (size.height / 2)

            val scale = (1.0 + (6.0 / distance.coerceAtLeast(0.5))).coerceIn(1.0, 6.0).toFloat()
            val baseMarkerSize = 45.dp.toPx() * scale
            val alpha = (1.0 - (distance / 15.0).coerceIn(0.0, 0.85)).toFloat()

            val isFused = deviceType.startsWith("FUSED_")
            val cleanDeviceType = deviceType.replace("FUSED_", "")
            
            val signatureColor = when {
                neuralState == CentralNeuralLink.NeuralState.PRIME_SYNERGY -> com.examshield.ai.ui.theme.PrimeGold
                isFused -> com.examshield.ai.ui.theme.ThreatRed
                cleanDeviceType.contains("PHONE") -> com.examshield.ai.ui.theme.NeuralViolet
                else -> com.examshield.ai.ui.theme.NeonCyan
            }
            
            withTransform({
                translate(left = screenX, top = screenY)
                rotate(degrees = rotation)
            }) {
                // Outer Interstellar Ring (Segmented)
                for (i in 0 until 4) {
                    drawArc(
                        color = signatureColor.copy(alpha = alpha * pulse),
                        startAngle = i * 90f + 10f, sweepAngle = 70f, useCenter = false,
                        style = Stroke(width = 2.dp.toPx()),
                        topLeft = Offset(-baseMarkerSize, -baseMarkerSize),
                        size = androidx.compose.ui.geometry.Size(baseMarkerSize * 2, baseMarkerSize * 2)
                    )
                }
                
                if (neuralState == CentralNeuralLink.NeuralState.PRIME_SYNERGY) {
                    // Prime Synergy Multi-Hex Reticle
                     drawArc(
                        color = com.examshield.ai.ui.theme.SynchroBlue.copy(alpha = alpha * primeGlow),
                        startAngle = 45f, sweepAngle = 270f, useCenter = false,
                        style = Stroke(width = 1.dp.toPx()),
                        topLeft = Offset(-baseMarkerSize * 0.8f, -baseMarkerSize * 0.8f),
                        size = androidx.compose.ui.geometry.Size(baseMarkerSize * 1.6f, baseMarkerSize * 1.6f)
                    )
                }
            }

            // Inner Stabilization Circle
            drawCircle(
                color = signatureColor.copy(alpha = alpha * 0.3f),
                radius = baseMarkerSize * 0.5f,
                center = Offset(screenX, screenY),
                style = Stroke(width = 1.dp.toPx())
            )
            
            // Surgical Impact point
            drawCircle(color = Color.White.copy(alpha = alpha), radius = 4.dp.toPx(), center = Offset(screenX, screenY))

            // DYNAMIC TELEMETRY HUD (Side of Reticle)
            val telemetry = buildString {
                append("NODE_TYPE_ID: $cleanDeviceType\n")
                append("RANGE_VECT: ${"%.2f".format(distance)}m\n")
                append("AZIMUTH_OFF: ${"%.1f".format(relativeAzimuth)}°\n")
                append("SIG_POWER: ${rssi}dBm\n")
                if (neuralState == CentralNeuralLink.NeuralState.PRIME_SYNERGY) append("PRIME_LOCK_ACTIVE")
            }
            
            val textLayoutResult = textMeasurer.measure(
                text = telemetry,
                style = androidx.compose.ui.text.TextStyle(
                    color = signatureColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    background = Color.Black.copy(alpha = 0.6f)
                )
            )
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(screenX + baseMarkerSize + 10.dp.toPx(), screenY - textLayoutResult.size.height / 2)
            )

            // PROXIMITY ALERT BLOOM
            if (distance < 1.5) {
                 val warningAlpha = if (System.currentTimeMillis() % 400 > 200) 1f else 0.2f
                 drawCircle(
                     color = com.examshield.ai.ui.theme.ThreatRed.copy(alpha = 0.1f * warningAlpha),
                     radius = baseMarkerSize * 1.5f,
                     center = Offset(screenX, screenY)
                 )
                 
                 val warningText = textMeasurer.measure(
                     text = "⚠️ CRITICAL_PROXIMITY_VIOLATION ⚠️",
                     style = androidx.compose.ui.text.TextStyle(
                         color = com.examshield.ai.ui.theme.ThreatRed, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, background = Color.Black.copy(alpha = 0.8f)
                     )
                 )
                 drawText(
                     textLayoutResult = warningText,
                     topLeft = Offset(screenX - warningText.size.width / 2, screenY - baseMarkerSize - 30.dp.toPx())
                 )
            }

        } else {
            // OFF-SCREEN DIRECTIONAL NAVIGATION
            val arrowColor = if (neuralState == CentralNeuralLink.NeuralState.PRIME_SYNERGY) com.examshield.ai.ui.theme.PrimeGold else com.examshield.ai.ui.theme.AmberWarning
            val arrowY = centerY
            val arrowPadding = 40.dp.toPx()
            
            if (relativeAzimuth > 0) {
                drawOffScreenIndicator(this, size.width - arrowPadding, arrowY, arrowColor, true)
            } else {
                drawOffScreenIndicator(this, arrowPadding, arrowY, arrowColor, false)
            }
        }
    }
}

private fun drawDatalinkModule(textMeasurer: androidx.compose.ui.text.TextMeasurer, state: CentralNeuralLink.NeuralState, screenWidth: Float) {
    // Top right telemetry box
}

private fun drawOffScreenIndicator(scope: androidx.compose.ui.graphics.drawscope.DrawScope, x: Float, y: Float, color: Color, isRight: Boolean) {
    with(scope) {
        val size = 20.dp.toPx()
        val direction = if (isRight) 1f else -1f
        
        drawLine(color, Offset(x, y), Offset(x - direction * size, y - size / 2), 3.dp.toPx())
        drawLine(color, Offset(x, y), Offset(x - direction * size, y + size / 2), 3.dp.toPx())
        
        // Animated pulse
        val p = (System.currentTimeMillis() % 1000) / 1000f
        drawCircle(color.copy(alpha = 1f - p), radius = p * size, center = Offset(x - direction * size * 0.5f, y))
    }
}

private fun relativeAngleWrap(angle: Float): Float {
    var result = angle
    while (result > 180) result -= 360
    while (result < -180) result += 360
    return result
}
