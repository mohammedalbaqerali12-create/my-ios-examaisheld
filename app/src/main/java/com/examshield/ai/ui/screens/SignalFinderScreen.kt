package com.examshield.ai.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.ui.platform.LocalContext

@Composable
fun SignalFinderScreen(
    macAddress: String,
    viewModel: MonitorScreenViewModel = hiltViewModel()
) {
    val threatList by viewModel.threatList.collectAsState()
    val targetDevice = threatList.find { it.rawObject.macAddress == macAddress }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (targetDevice == null) {
            Text("Device not found. It may be out of range.", color = MaterialTheme.colorScheme.onBackground)
        } else {
            SignalStrengthIndicator(targetDevice.rawObject, viewModel)
        }
    }
}

@Composable
fun SignalStrengthIndicator(device: DetectedObject, viewModel: MonitorScreenViewModel) {
    var liveRssi by remember { mutableIntStateOf(device.signalStrengthRssi) }
    var liveVector by remember { mutableStateOf(device.sensorVector) }
    
    // Normalize RSSI to a 0-1 float. Typical RSSI ranges from -100 (weak) to -30 (strong).
    val normalizedRssi = ((liveRssi - -100).toFloat() / (70).toFloat()).coerceIn(0.1f, 1f)
    
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

    // Pulse effect for the sonar wave
    val sonarPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseOutExpo),
            repeatMode = RepeatMode.Restart
        ),
        label = "SonarPulse"
    )

    val targetDistanceRatio by animateFloatAsState(targetValue = 1f - normalizedRssi, animationSpec = tween(300), label = "Distance")

    // --- AZIMUTH COMPASS/RSSI HOT-COLD ALGORITHM WITH SLIDING WINDOW ---
    val context = androidx.compose.ui.platform.LocalContext.current
    var currentAzimuth by remember { mutableFloatStateOf(0f) }
    
    // We store the history of signals to find the best direction in the last 12 seconds
    data class RssiRecord(val rssi: Int, val azimuth: Float, val timestamp: Long)
    val rssiHistoryList = remember { mutableStateListOf<RssiRecord>() }
    
    // The direction the radar dot points to
    var strongestRssiAzimuth by remember { mutableFloatStateOf(0f) }

    // REAL-TIME SIGNAL COLLECTION
    LaunchedEffect(device.macAddress) {
        viewModel.rawDetectionStream.collect { result ->
            if (result.rawObject.macAddress == device.macAddress) {
                val now = System.currentTimeMillis()
                liveRssi = result.rawObject.signalStrengthRssi
                liveVector = result.rawObject.sensorVector
                
                // 1. Add current reading to history
                rssiHistoryList.add(RssiRecord(liveRssi, currentAzimuth, now))
                
                // 2. Erase records older than 12 seconds
                rssiHistoryList.removeAll { now - it.timestamp > 12000 }
                
                // 3. Find the highest RSSI in our recent memory
                val bestRecord = rssiHistoryList.maxByOrNull { it.rssi }
                if (bestRecord != null) {
                    strongestRssiAzimuth = bestRecord.azimuth
                }
            }
        }
    }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientationAngles = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    val azimuthDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                    currentAzimuth = (azimuthDegrees + 360) % 360
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }

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
                text = device.name ?: "UNKNOWN_TARGET",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text(
                text = "MAC: ${device.macAddress}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.4f),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

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
                    color = Color.Cyan.copy(alpha = (1f - sonarPulse) * 0.3f),
                    radius = radius * sonarPulse,
                    center = center,
                    style = Stroke(width = 4f)
                )

                // Draw Radar Grids (Futuristic Cyan/Blue)
                drawCircle(color = Color.Cyan.copy(alpha = 0.4f), radius = radius, center = center, style = Stroke(width = 3f))
                drawCircle(color = Color.Cyan.copy(alpha = 0.2f), radius = radius * 0.66f, center = center, style = Stroke(width = 1.5f))
                drawCircle(color = Color.Cyan.copy(alpha = 0.1f), radius = radius * 0.33f, center = center, style = Stroke(width = 1f))
                
                // Draw Crosshairs
                drawLine(color = Color.Cyan.copy(alpha = 0.2f), start = Offset(0f, center.y), end = Offset(size.width, center.y), strokeWidth = 1f)
                drawLine(color = Color.Cyan.copy(alpha = 0.2f), start = Offset(center.x, 0f), end = Offset(center.x, size.height), strokeWidth = 1f)

                // Draw Sweeping Arc
                rotate(degrees = sweepAngle, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(Color.Transparent, Color.Cyan.copy(alpha = 0.05f), Color.Cyan.copy(alpha = 0.5f)),
                            center = center
                        ),
                        startAngle = 270f,
                        sweepAngle = 60f,
                        useCenter = true,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                    )
                }

                // TARGET CALCULATION
                val dotRad: Double
                val dotRadiusFinal: Float
                
                if (liveVector != null && liveVector!!.size >= 3) {
                    // PHYSICAL VECTOR MODE: Use sensor XYZ to place the dot
                    val x = liveVector!![0]
                    val y = liveVector!![1]
                    // Normalize X/Y to [-1, 1] relative to a reasonable max field change (e.g. 100uT)
                    val normX = (x / 100f).coerceIn(-1f, 1f)
                    val normY = (y / 100f).coerceIn(-1f, 1f)
                    
                    val relativeAngle = Math.toDegrees(kotlin.math.atan2(normX.toDouble(), -normY.toDouble()))
                    dotRad = Math.toRadians(relativeAngle - 90)
                    dotRadiusFinal = radius * kotlin.math.sqrt(normX*normX + normY*normY).coerceIn(0.1f, 0.9f)
                } else {
                    // RSSI HOT-COLD MODE
                    val relativeAngle = (strongestRssiAzimuth - currentAzimuth + 360) % 360
                    dotRad = Math.toRadians((relativeAngle - 90).toDouble())
                    dotRadiusFinal = radius * targetDistanceRatio
                }
                
                val dotX = center.x + (dotRadiusFinal * cos(dotRad)).toFloat()
                val dotY = center.y + (dotRadiusFinal * sin(dotRad)).toFloat()

                // Drawing Target with Glow
                val targetColor = if (normalizedRssi > 0.8f) Color.Red else Color(0xFFFF9800)
                
                // Outer Glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(targetColor.copy(alpha = 0.6f), Color.Transparent),
                        center = Offset(dotX, dotY),
                        radius = 40f
                    ),
                    radius = 40f,
                    center = Offset(dotX, dotY)
                )

                // Inner Target Dot
                drawCircle(
                    color = targetColor,
                    radius = 12f + (normalizedRssi * 8f),
                    center = Offset(dotX, dotY)
                )
                
                // Pulse ring for proximity
                if (normalizedRssi > 0.8f) {
                    drawCircle(
                        color = Color.Red.copy(alpha = 0.5f),
                        radius = 30f + (sonarPulse * 20f),
                        center = Offset(dotX, dotY),
                        style = Stroke(width = 4f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))

        // TECH STATUS READOUTS
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
        ) {
            StatusRow("SIGNAL VARIANCE", if (rssiHistoryList.size > 10) "STABLE" else "CALIBRATING...", Color.Cyan)
            StatusRow("THREAT SIGNATURE", if (normalizedRssi > 0.7f) "IDENTIFIED: LEVEL 4" else "ANALYZING...", if (normalizedRssi > 0.7f) Color.Red else Color.Gray)
            StatusRow("ESTIMATED DEPTH", "${"%.2f".format(targetDistanceRatio * 5)} METERS", Color.White)
            
            if (rssiHistoryList.size < 5) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().background(Color.Yellow.copy(alpha = 0.1f)).padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("LEARNING ENVIRONMENT: ROTATE DEVICE SLOWLY", color = Color.Yellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun StatusRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, letterSpacing = 1.sp)
        Text(text = value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    val r = (start.red + (stop.red - start.red) * fraction).coerceIn(0f, 1f)
    val g = (start.green + (stop.green - start.green) * fraction).coerceIn(0f, 1f)
    val b = (start.blue + (stop.blue - start.blue) * fraction).coerceIn(0f, 1f)
    return Color(r, g, b)
}

