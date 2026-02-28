package com.examshield.ai.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.examshield.ai.domain.model.DetectedObject
import com.examshield.ai.domain.model.ClassificationResult
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs

@Composable
fun SignalFinderScreen(
    macAddress: String,
    viewModel: MonitorScreenViewModel = hiltViewModel()
) {
    val threatList by viewModel.threatList.collectAsState()
    val targetDevice = threatList.find { it.rawObject.macAddress == macAddress }
    
    // Astra V7: Dynamic Background for Lock Status
    val isLocked = targetDevice?.rawObject?.let { it.signalStrengthRssi > -65 } ?: false
    val backgroundColor by animateColorAsState(
        targetValue = if (isLocked) Color(0xFF1A0505) else MaterialTheme.colorScheme.background,
        animationSpec = tween(500),
        label = "BgLock"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (targetDevice == null) {
            Text("DEVICE_SILENT_OR_REMOVED", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
        } else {
            SignalStrengthIndicator(targetDevice, viewModel)
        }
    }
}

@Composable
fun SignalStrengthIndicator(classification: ClassificationResult, viewModel: MonitorScreenViewModel) {
    val device = classification.rawObject
    
    // ASTRA V9: Deep UI Sync. Use key 'classification' to re-initialize if parent updates.
    var liveRssi by remember(classification) { mutableIntStateOf(device.signalStrengthRssi) }
    var liveDistance by remember(classification) { mutableFloatStateOf(classification.estimatedDistanceMeters) }
    var liveVector by remember(classification) { mutableStateOf(device.sensorVector) }
    var liveReason by remember(classification) { mutableStateOf(classification.discoveryReason) }
    var liveConfidence by remember(classification) { mutableIntStateOf(classification.confidenceScore) }
    var aiStatus by remember { mutableStateOf("INITIALIZING_AI_DNA...") }
    
    // Normalize distance for radar mapping (Max range shown on radar: 10 meters)
    val maxRadarRange = 10f
    val distanceRatio = (liveDistance / maxRadarRange).coerceIn(0.05f, 0.95f)

    // Animation for the sweeping radar
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepAngle"
    )

    val sonarPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseOutExpo),
            repeatMode = RepeatMode.Restart
        ),
        label = "SonarPulse"
    )

    val animatedDistanceRatio by animateFloatAsState(targetValue = distanceRatio, animationSpec = tween(400), label = "DistRatio")

    // Target Point Animation (Orbital + Noise)
    val dotRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "DotRotation"
    )
    
    val dotJiggle by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(70, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DotJiggle"
    )

    // --- INTENSITY & LOCK TRACKING ---
    val signalContext = androidx.compose.ui.platform.LocalContext.current
    data class RssiRecord(val rssi: Int, val timestamp: Long)
    val rssiHistoryList = remember { mutableStateListOf<RssiRecord>() }
    var maxHistoryRssi by remember { mutableIntStateOf(-100) }
    
    val currentIntensity = ((liveRssi + 100) / (maxHistoryRssi + 100).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val isNearPeak = liveRssi >= maxHistoryRssi - 3 && maxHistoryRssi != -100
    val isLocked = isNearPeak && liveRssi > -65

    // --- ASTRA V8: TACTICAL BEARING TRACKING ---
    var peakAzimuth by remember { mutableFloatStateOf(0f) }
    var lastPeakRssi by remember { mutableIntStateOf(-100) }
    var calibrationSamples by remember { mutableIntStateOf(0) }
    val isCalibrating = calibrationSamples < 15

    // --- SPATIAL STABILIZATION ---
    val currentAzimuth by viewModel.azimuth.collectAsState()
    
    // The target is at (Peak Direction - Current Phone Orientation)
    // We subtract 90 degrees because 0 radians in math is right, but we want 0 (Top) for the Radar.
    val targetAngle = ((peakAzimuth - currentAzimuth) - 90f) % 360f
    val dotRad = Math.toRadians(targetAngle.toDouble())
    
    // Normalized Offset (center = 0.0, 0.0)
    val targetOffset = Offset(
        x = (distanceRatio * kotlin.math.cos(dotRad).toFloat()),
        y = (distanceRatio * kotlin.math.sin(dotRad).toFloat())
    )
    
    val animatedTargetOffset by animateOffsetAsState(
        targetValue = targetOffset,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
        label = "TargetOffset"
    )

    // --- DEBUG METRICS ---
    var callbackCount by remember { mutableIntStateOf(0) }
    var refreshRate by remember { mutableIntStateOf(0) }
    var lastRefreshTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(device.macAddress) {
        viewModel.rawDetectionStream.collect { result ->
            if (result.rawObject.macAddress == device.macAddress) {
                val now = System.currentTimeMillis()
                
                // Refresh Rate Calculation
                callbackCount++
                if (now - lastRefreshTime >= 1000) {
                    refreshRate = callbackCount
                    callbackCount = 0
                    lastRefreshTime = now
                }

                liveRssi = result.rawObject.signalStrengthRssi
                liveDistance = result.estimatedDistanceMeters
                liveVector = result.rawObject.sensorVector
                liveReason = result.discoveryReason
                liveConfidence = result.confidenceScore
                
                // --- ASTRA V8 PEAK TRACKING ---
                if (liveRssi > lastPeakRssi || (now - (rssiHistoryList.firstOrNull()?.timestamp ?: 0) > 10000)) {
                    lastPeakRssi = liveRssi
                    peakAzimuth = currentAzimuth
                }
                calibrationSamples++

                // --- ASTRA NEXUS SYNERGY FEED ---
                val rHistory = rssiHistoryList.map { it.rssi }
                val stabilityScore = if (rHistory.size > 2) {
                    val mean = rHistory.average()
                    rHistory.map { Math.pow((it - mean).toDouble(), 2.0) }.average()
                } else 0.0
                
                aiStatus = when {
                    liveReason.contains("PRECISION_LOCK") -> ">>> MICRO-TRILATERATION ACTIVE <<<"
                    liveReason.contains("EVOLVED") -> "AI: EVOLVING_LOCAL_INTELLIGENCE..."
                    classification.isNexusVerified -> "NEXUS: MULTI-SYSTEM_MATCH_CONFIRMED"
                    isCalibrating -> "AI: CALIBRATING_BEARING_DATA..."
                    isLocked -> "TARGET_AI_LOCKED (CONFIRMED)"
                    stabilityScore > 15.0 -> "SIGNAL_FLUCTUATING: TRACE_STREAK"
                    stabilityScore < 2.0 && rHistory.size > 5 -> "SIGNAL_STABLE: LOCALIZED"
                    else -> "AI: TRACKING_PEAK_SURFACE..."
                }

                rssiHistoryList.add(RssiRecord(liveRssi, now))
                rssiHistoryList.removeAll { now - it.timestamp > 15000 }
                
                val variance = if (rssiHistoryList.size > 2) {
                    val mean = rssiHistoryList.map { it.rssi }.average()
                    rssiHistoryList.map { Math.pow((it.rssi - mean), 2.0) }.average()
                } else 0.0
                
                // --- ASTRA PERFORMANCE ADVISOR FEED ---
                viewModel.performanceAdvisor.updateMetrics(
                    refreshRate = refreshRate,
                    callbackFrequency = callbackCount,
                    bluetoothEnabled = true,
                    rssiVariance = variance
                )

                val bestRssi = rssiHistoryList.maxOfOrNull { it.rssi } ?: -100
                if (bestRssi > maxHistoryRssi || now - (rssiHistoryList.firstOrNull()?.timestamp ?: 0) > 10000) {
                    maxHistoryRssi = bestRssi
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally, 
            verticalArrangement = Arrangement.Top
        ) {
            // High-Tech Header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                 Text(
                    text = "ASTRA INTERCEPTOR V4",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Cyan.copy(alpha = 0.6f),
                    letterSpacing = 4.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = device.name ?: "N/A",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isLocked) Color.Red else Color.White
                )
                if (isLocked) {
                    Text(
                        text = ">>> TARGET LOCKED <<<",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
    
            Spacer(modifier = Modifier.height(32.dp))
    
            // THE RADAR
            Box(
                modifier = Modifier
                    .size(340.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.width / 2
                    
                    // Draw Sonar Pulse Wave
                    drawCircle(
                        color = if (isLocked) Color.Red.copy(alpha = (1f - sonarPulse) * 0.4f) else Color.Cyan.copy(alpha = (1f - sonarPulse) * 0.3f),
                        radius = radius * sonarPulse,
                        center = center,
                        style = Stroke(width = 4f)
                    )
    
                    // Draw Radar Grids (Futuristic Cyan/Blue)
                    val gridColor = if (isLocked) Color.Red.copy(alpha = 0.5f) else Color.Cyan.copy(alpha = 0.4f)
                    drawCircle(color = gridColor, radius = radius, center = center, style = Stroke(width = 3f))
                    drawCircle(color = gridColor.copy(alpha = 0.2f), radius = radius * 0.75f, center = center, style = Stroke(width = 1.5f))
                    drawCircle(color = gridColor.copy(alpha = 0.1f), radius = radius * 0.5f, center = center, style = Stroke(width = 1f))
                    drawCircle(color = gridColor.copy(alpha = 0.05f), radius = radius * 0.25f, center = center, style = Stroke(width = 1f))
                    
                    // Draw Intensity Heat Ring
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(Color.Transparent, gridColor.copy(alpha = 0.1f), gridColor.copy(alpha = 0.8f * currentIntensity)),
                            center = center
                        ),
                        radius = radius * currentIntensity,
                        center = center,
                        style = Stroke(width = 20f * currentIntensity, cap = StrokeCap.Round)
                    )
    
                    // Draw Crosshairs
                    drawLine(color = gridColor.copy(alpha = 0.3f), start = Offset(0f, center.y), end = Offset(size.width, center.y), strokeWidth = 2f)
                    drawLine(color = gridColor.copy(alpha = 0.3f), start = Offset(center.x, 0f), end = Offset(center.x, size.height), strokeWidth = 2f)
    
    
                    // Draw Sweeping Arc
                    rotate(degrees = sweepAngle, pivot = center) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(Color.Transparent, Color.Cyan.copy(alpha = 0.05f), Color.Cyan.copy(alpha = 0.5f)),
                                center = center
                            ),
                            startAngle = 270f, sweepAngle = 60f, useCenter = true,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                        )
                    }
    
                    // AI LOCK SCANNER OVERLAY
                    if (isLocked) {
                        val scanLineY = center.y + (radius * kotlin.math.sin(System.currentTimeMillis() / 200.0).toFloat())
                        drawLine(
                            color = Color.Red.copy(alpha = 0.5f),
                            start = Offset(center.x - radius, scanLineY),
                            end = Offset(center.x + radius, scanLineY),
                            strokeWidth = 2f
                        )
                    }
    
                    // TARGET POSITIONING (Surgical RELOCATION)
                    val dotX = center.x + (animatedTargetOffset.x * radius) + dotJiggle
                    val dotY = center.y + (animatedTargetOffset.y * radius) + dotJiggle
    
                    val targetColor = if (liveDistance < 1.5f) Color.Red else Color(0xFFFF9800)
                    
                    // Target Glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(targetColor.copy(alpha = 0.6f * currentIntensity), Color.Transparent),
                            center = Offset(dotX, dotY), radius = 50f
                        ),
                        radius = 50f * currentIntensity, center = Offset(dotX, dotY)
                    )
    
                    // Target Core
                    drawCircle(color = targetColor.copy(alpha = currentIntensity), radius = 14f, center = Offset(dotX, dotY))
                    
                    // Target Proximity Pulse
                    if (isLocked) {
                        drawCircle(
                            color = Color.Red.copy(alpha = 0.5f),
                            radius = 25f + (sonarPulse * 30f),
                            center = Offset(dotX, dotY),
                            style = Stroke(width = 4f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
    
            // INTENSITY HEAT BAR
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(currentIntensity)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Cyan, Color.Yellow, Color.Red)
                            )
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
    
            // TECH STATUS READOUTS (SURGICAL)
            Column(
                modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth()
            ) {
                val rHistory = rssiHistoryList.map { it.rssi }
                val stabilityVal = if (rHistory.size > 2) {
                    val mean = rHistory.average()
                    rHistory.map { Math.pow((it - mean).toDouble(), 2.0) }.average()
                } else 10.0
                
                StatusRow("CALIBRATED DISTANCE", "${"%.2f".format(liveDistance)} METERS", Color.White)
                StatusRow("SIGNAL STABILITY", if (stabilityVal < 3.0) "STABLE" else if (stabilityVal < 10.0) "FLUCTUATING" else "UNSTABLE", if (stabilityVal < 3.0) Color.Green else if (stabilityVal < 10.0) Color.Yellow else Color.Red)
                StatusRow("SIGNAL INTENSITY", "${(currentIntensity * 100).toInt()}%", if (isLocked) Color.Red else Color.Cyan)
                StatusRow("SIGNAL POWER", "$liveRssi dBm", if (liveRssi > -60) Color.Red else Color.Gray)
                StatusRow("HUNT STATUS", if (liveReason.contains("PRECISION_LOCK")) "PRECISION_LOCK" else if (isLocked) "PEAK INTENSITY" else "SCANNING AREA", if (isLocked || liveReason.contains("PRECISION_LOCK")) Color.Red else Color.Gray)
                
                if (rssiHistoryList.size < 5) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().background(Color.Yellow.copy(alpha = 0.1f)).padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("CALIBRATING SENSORS: MOVE AROUND", color = Color.Yellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                if (classification.isNexusVerified) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFD700), RoundedCornerShape(4.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "NEXUS_CORE_VERIFIED",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                if (isCalibrating) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().background(Color.Yellow.copy(alpha = 0.15f)).padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ">>> ROTATE 360° TO PINPOINT TARGET <<<",
                            color = Color.Yellow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // AI STATUS FEED (ASTRA V7)
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color.Red.copy(alpha = if(isLocked) 0.2f else 0.05f)).padding(4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = ">> $aiStatus <<",
                        style = MaterialTheme.typography.labelSmall,
                        color = if(isLocked) Color.Red else Color.Cyan,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp
                    )
                }
    
                Spacer(modifier = Modifier.height(24.dp))
    
                // TACTICAL ACTIONS (Arabic RTL)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.markAsFriendly(classification) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
                    ) {
                        Text("إشارة صديقة", fontWeight = FontWeight.Bold)
                    }
    
                    Button(
                        onClick = { viewModel.markAsCheating(classification) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = com.examshield.ai.ui.theme.ThreatRed)
                    ) {
                        Text("إشارة غش", fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
    
                Spacer(modifier = Modifier.height(16.dp))
    
                // SIGNAL SIGNATURE VISUALIZER (COMPLETION)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "SIGNAL_SIGNATURE_ANALYSIS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.3f)
                    )
                    Text(
                        liveReason,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Cyan.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                    val step = size.width / 50f
                    for (i in 0 until 50) {
                        val barHeight = (kotlin.math.sin((i + sweepAngle / 10).toDouble()) * 15f + 20f).toFloat() * currentIntensity
                        drawRect(
                            color = if (isLocked) Color.Red.copy(alpha = 0.4f) else Color.Cyan.copy(alpha = 0.3f),
                            topLeft = Offset(i * step, size.height - barHeight),
                            size = androidx.compose.ui.geometry.Size(step * 0.6f, barHeight)
                        )
                    }
                }
    
                Spacer(modifier = Modifier.height(16.dp))
                StatusRow("IDENT_CONFIDENCE", "$liveConfidence%", if (liveConfidence > 80) Color.Green else Color.Yellow)
            }
        }

        // --- DIAGNOSTIC OVERLAY (PHASE 1) ---
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                .border(1.dp, Color.Green.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ASTRA_HEALTH_DIAGNOSTIC", color = Color.Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Canvas(modifier = Modifier.fillMaxWidth().height(1.dp)) {
                    drawLine(Color.Green.copy(alpha = 0.5f), Offset.Zero, Offset(size.width, 0f), 1f)
                }
                DiagnosticItem("REFRESH_RATE", "${refreshRate}Hz", if (refreshRate > 0) Color.Green else Color.Red)
                DiagnosticItem("CALLBACKS/S", "$callbackCount", if (callbackCount > 2) Color.Green else Color.Yellow)
                DiagnosticItem("CURRENT_RSSI", "$liveRssi dBm", Color.Cyan)
                
                val rHistory = rssiHistoryList.map { it.rssi }
                val variance = if (rHistory.size > 2) {
                    val mean = rHistory.average()
                    rHistory.map { Math.pow((it - mean).toDouble(), 2.0) }.average()
                } else 0.0
                DiagnosticItem("RSSI_VARIANCE", "%.2f".format(variance), if (variance < 10.0) Color.Green else Color.Yellow)
                
                val scanMode = device.extraMetadata["scan_mode"] as? String ?: "BALANCED"
                DiagnosticItem("SCAN_MODE", scanMode, Color.White)
                
                val updateRate = device.extraMetadata["sensorUpdateRate"] as? Int ?: 0
                DiagnosticItem("SENSOR_RATE", "${updateRate}ms", Color.Yellow)
                
                Spacer(modifier = Modifier.height(4.dp))
                Text("PIPELINE: ACTIVE", color = if (refreshRate > 0) Color.Green else Color.Red, fontSize = 9.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun DiagnosticItem(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(0.35f),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("$label:", color = Color.Gray, fontSize = 9.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        Text(value, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

@Composable
fun StatusRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
        Text(text = value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    val r = (start.red + (stop.red - start.red) * fraction).coerceIn(0f, 1f)
    val g = (start.green + (stop.green - start.green) * fraction).coerceIn(0f, 1f)
    val b = (start.blue + (stop.blue - start.blue) * fraction).coerceIn(0f, 1f)
    return Color(r, g, b)
}

