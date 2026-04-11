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
            drawText(textLayoutResult = textResult, topLeft = Offset(left + 5.dp.toPx(), top - textResult.size.height - 4.dp.toPx()))
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
        
        val hFov = 65f
        val vFov = 95f
        val centerX = size.width / 2
        val centerY = size.height / 2

        if (Math.abs(relativeAzimuth) < hFov / 2) {
            val rawScreenX = centerX + (relativeAzimuth / (hFov / 2)) * (size.width / 2)
            val normalizedPitch = currentPitch + 90f
            val rawScreenY = centerY + (normalizedPitch / (vFov / 2)) * (size.height / 2)

            // --- SYNERGETIC SNAPPING LOGIC ---
            var screenX = rawScreenX
            var screenY = rawScreenY
            var isStrictLocked = false
            
            val cleanDeviceTypeForMatching = deviceType.replace("FUSED_", "").uppercase()
            
            // Try to find a visual object that matches the signal metadata
            val matchingVisionObj = visionTargets.find { vt ->
                vt.brandHeuristic != null && (
                    (cleanDeviceTypeForMatching.contains("PHONE") && vt.brandHeuristic == "MOBILE_COMM_DEVICE") ||
                    (cleanDeviceTypeForMatching.contains("AUDIO") && vt.brandHeuristic == "AUDIO_TRANSCEIVER") ||
                    (cleanDeviceTypeForMatching.contains("COMPUTER") && vt.brandHeuristic == "COMPUTING_TERMINAL") ||
                    (cleanDeviceTypeForMatching.contains("CAMERA") && vt.brandHeuristic == "VISION_SENSOR") ||
                    (vt.brandHeuristic == "ELECTRONIC_CORE")
                )
            }

            if (matchingVisionObj != null) {
                val vL = (matchingVisionObj.boundingBox.left / imgWidth) * size.width
                val vT = (matchingVisionObj.boundingBox.top / imgHeight) * size.height
                val vR = (matchingVisionObj.boundingBox.right / imgWidth) * size.width
                val vB = (matchingVisionObj.boundingBox.bottom / imgHeight) * size.height
                
                val vCenterX = (vL + vR) / 2f
                val vCenterY = (vT + vB) / 2f
                
                // Snap if the signal estimated position is close to the visual object center
                val distInPixels = Math.sqrt(Math.pow((rawScreenX - vCenterX).toDouble(), 2.0) + Math.pow((rawScreenY - vCenterY).toDouble(), 2.0))
                if (distInPixels < 150.dp.toPx()) {
                    screenX = vCenterX
                    screenY = vCenterY
                    isStrictLocked = true
                }
            }

            val distance = Math.sqrt(dx * dx + dy * dy.toDouble())
            val scale = (1.0 + (6.0 / distance.coerceAtLeast(0.5))).coerceIn(1.0, 6.0).toFloat()
            val baseMarkerSize = 45.dp.toPx() * scale
            val alpha = (1.0 - (distance / 15.0).coerceIn(0.0, 0.85)).toFloat()

            val isFused = deviceType.startsWith("FUSED_")
            val cleanDeviceType = deviceType.replace("FUSED_", "")
            
            val signatureColor = when {
                isStrictLocked -> com.examshield.ai.ui.theme.PrimeGold
                neuralState == CentralNeuralLink.NeuralState.PRIME_SYNERGY -> com.examshield.ai.ui.theme.PrimeGold
                isFused -> com.examshield.ai.ui.theme.ThreatRed
                cleanDeviceType.contains("PHONE") -> com.examshield.ai.ui.theme.NeuralViolet
                else -> com.examshield.ai.ui.theme.NeonCyan
            }
            
            // --- RADIO WAVE RIPPLE (Aura) ---
            val rippleBase = (System.currentTimeMillis() % 1500) / 1500f
            for (i in 0 until 3) {
                val rScale = rippleBase + (i * 0.33f)
                val finalRScale = if (rScale > 1f) rScale - 1f else rScale
                val rAlpha = (1f - finalRScale) * alpha * 0.5f
                drawCircle(
                    color = signatureColor.copy(alpha = rAlpha),
                    radius = baseMarkerSize * (0.8f + finalRScale * 3.5f),
                    center = Offset(screenX, screenY),
                    style = Stroke(width = (2 - finalRScale).dp.toPx())
                )
            }

            // --- RF SPECTRUM MAPPERS (Small bars next to reticle) ---
            val barCount = 6
            val barSpacing = 4.dp.toPx()
            val barWidth = 3.dp.toPx()
            for (i in 0 until barCount) {
                val noise = (Math.random() * 20.dp.toPx()).toFloat()
                val barHeight = (10.dp.toPx() + noise) * alpha
                drawRect(
                    color = signatureColor.copy(alpha = alpha * 0.6f),
                    topLeft = Offset(screenX - baseMarkerSize - 15.dp.toPx() - (i * (barWidth + barSpacing)), screenY + baseMarkerSize - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                )
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
                
                if (neuralState == CentralNeuralLink.NeuralState.PRIME_SYNERGY || isStrictLocked) {
                    // Prime Synergy Multi-Hex Reticle
                     drawArc(
                        color = com.examshield.ai.ui.theme.SynchroBlue.copy(alpha = alpha * primeGlow),
                        startAngle = (System.currentTimeMillis() % 1000 / 1000f) * 360f, sweepAngle = 90f, useCenter = false,
                        style = Stroke(width = 1.dp.toPx()),
                        topLeft = Offset(-baseMarkerSize * 0.85f, -baseMarkerSize * 0.85f),
                        size = androidx.compose.ui.geometry.Size(baseMarkerSize * 1.7f, baseMarkerSize * 1.7f)
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
                append("PHASE_SHIFT: ${(Math.random() * 0.05).format(3)}rad\n")
                if (isStrictLocked) append("VISUAL_HARD_LOCK_ACTIVE\n")
                if (neuralState == CentralNeuralLink.NeuralState.PRIME_SYNERGY) append("PRIME_SYNC_STABLE")
            }
            
            val textLayoutResult = textMeasurer.measure(
                text = telemetry,
                style = androidx.compose.ui.text.TextStyle(
                    color = signatureColor,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    lineHeight = 10.sp
                )
            )
            
            // Backplate for text
            drawRect(
                color = Color.Black.copy(alpha = 0.5f * alpha),
                topLeft = Offset(screenX + baseMarkerSize + 8.dp.toPx(), screenY - textLayoutResult.size.height / 2f - 4.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(textLayoutResult.size.width.toFloat() + 8.dp.toPx(), textLayoutResult.size.height.toFloat() + 8.dp.toPx())
            )

            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(screenX + baseMarkerSize + 12.dp.toPx(), screenY - textLayoutResult.size.height / 2)
            )

            // PROXIMITY ALERT BLOOM
            if (distance < 1.5) {
                 val warningAlpha = if (System.currentTimeMillis() % 400 > 200) 1f else 0.2f
                 drawCircle(
                     color = com.examshield.ai.ui.theme.ThreatRed.copy(alpha = 0.15f * warningAlpha),
                     radius = baseMarkerSize * 1.8f,
                     center = Offset(screenX, screenY)
                 )
                 
                 val warningText = textMeasurer.measure(
                     text = "⚠️ CRITICAL_PROXIMITY_LOCK ⚠️",
                     style = androidx.compose.ui.text.TextStyle(
                         color = com.examshield.ai.ui.theme.ThreatRed, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, background = Color.Black.copy(alpha = 0.8f)
                     )
                 )
                 drawText(
                     textLayoutResult = warningText,
                     topLeft = Offset(screenX - warningText.size.width / 2, screenY - baseMarkerSize - 35.dp.toPx())
                 )
            }
        } else {
            // OFF-SCREEN DIRECTIONAL NAVIGATION
            val arrowColor = if (neuralState == CentralNeuralLink.NeuralState.PRIME_SYNERGY) com.examshield.ai.ui.theme.PrimeGold else com.examshield.ai.ui.theme.AmberWarning
            val arrowY = centerY
            val arrowPadding = 48.dp.toPx()
            
            if (relativeAzimuth > 0) {
                drawOffScreenIndicator(this, size.width - arrowPadding, arrowY, arrowColor, true, textMeasurer)
            } else {
                drawOffScreenIndicator(this, arrowPadding, arrowY, arrowColor, false, textMeasurer)
            }
        }
    }
}

private fun Double.format(digits: Int) = "%.${digits}f".format(this)

private fun drawDatalinkModule(textMeasurer: androidx.compose.ui.text.TextMeasurer, state: CentralNeuralLink.NeuralState, screenWidth: Float) {
    // Top right telemetry box implementation can go here if needed
}

private fun drawOffScreenIndicator(scope: androidx.compose.ui.graphics.drawscope.DrawScope, x: Float, y: Float, color: Color, isRight: Boolean, textMeasurer: androidx.compose.ui.text.TextMeasurer) {
    with(scope) {
        val s = 24.dp.toPx()
        val direction = if (isRight) 1f else -1f
        
        // Arrow
        drawLine(color, Offset(x, y), Offset(x - direction * s, y - s / 2), 4.dp.toPx())
        drawLine(color, Offset(x, y), Offset(x - direction * s, y + s / 2), 4.dp.toPx())
        
        // Label
        val label = textMeasurer.measure(
            if (isRight) "SCAN_RIGHT >>" else "<< SCAN_LEFT",
            style = androidx.compose.ui.text.TextStyle(color = color, fontSize = 9.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        )
        drawText(textLayoutResult = label, topLeft = Offset(x - (if (isRight) label.size.width + 10.dp.toPx() else -10.dp.toPx()), y - label.size.height / 2))

        // Animated pulse
        val p = (System.currentTimeMillis() % 1200) / 1200f
        drawCircle(color.copy(alpha = 1f - p), radius = p * s * 1.5f, center = Offset(x, y), style = Stroke(2.dp.toPx()))
    }
}

private fun relativeAngleWrap(angle: Float): Float {
    var result = angle
    while (result > 180) result -= 360
    while (result < -180) result += 360
    return result
}
