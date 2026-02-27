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

    LaunchedEffect(macAddress) {
        // You might want to trigger a more frequent, single-device-focused scan here
        // For now, we rely on the main scanning flow.
    }

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
            SignalStrengthIndicator(targetDevice.rawObject)
        }
    }
}

@Composable
fun SignalStrengthIndicator(device: DetectedObject) {
    val rssi = device.signalStrengthRssi
    // Normalize RSSI to a 0-1 float. Typical RSSI ranges from -100 (weak) to -30 (strong).
    val normalizedRssi = ((rssi - -100).toFloat() / (70).toFloat()).coerceIn(0f, 1f)
    
    // Animation for the sweeping radar
    val infiniteTransition = rememberInfiniteTransition()
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val targetDistanceRatio by animateFloatAsState(targetValue = 1f - normalizedRssi, animationSpec = tween(150))

    // --- AZIMUTH COMPASS/RSSI HOT-COLD ALGORITHM WITH SLIDING WINDOW ---
    val context = androidx.compose.ui.platform.LocalContext.current
    var currentAzimuth by remember { mutableFloatStateOf(0f) }
    
    // We store the history of signals to find the best direction in the last 8 seconds
    data class RssiRecord(val rssi: Int, val azimuth: Float, val timestamp: Long)
    val rssiHistoryList = remember { mutableStateListOf<RssiRecord>() }
    
    // The direction the radar dot points to
    var strongestRssiAzimuth by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(device.signalStrengthRssi) {
        val now = System.currentTimeMillis()
        
        // 1. Add current reading to history
        rssiHistoryList.add(RssiRecord(device.signalStrengthRssi, currentAzimuth, now))
        
        // 2. Erase records older than 8 seconds (8000 ms) so the radar can "forget" old places
        rssiHistoryList.removeAll { now - it.timestamp > 8000 }
        
        // 3. Find the highest RSSI in our recent 8-second memory
        val bestRecord = rssiHistoryList.maxByOrNull { it.rssi }
        if (bestRecord != null) {
            strongestRssiAzimuth = bestRecord.azimuth
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

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Radar Locator", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(16.dp))
        Text(device.name ?: "Unknown Device", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(device.macAddress, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(320.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.width / 2
                
                // Draw Radar Grids (Outer, Mid, Inner)
                drawCircle(color = Color.Green.copy(alpha = 0.3f), radius = radius, center = center, style = Stroke(width = 2f))
                drawCircle(color = Color.Green.copy(alpha = 0.2f), radius = radius * 0.66f, center = center, style = Stroke(width = 1.5f))
                drawCircle(color = Color.Green.copy(alpha = 0.1f), radius = radius * 0.33f, center = center, style = Stroke(width = 1f))
                
                // Draw Crosshairs
                drawLine(color = Color.Green.copy(alpha = 0.3f), start = Offset(0f, center.y), end = Offset(size.width, center.y), strokeWidth = 1f)
                drawLine(color = Color.Green.copy(alpha = 0.3f), start = Offset(center.x, 0f), end = Offset(center.x, size.height), strokeWidth = 1f)

                // Draw Sweeping Arc
                rotate(degrees = sweepAngle, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(Color.Transparent, Color.Green.copy(alpha = 0.1f), Color.Green.copy(alpha = 0.6f)),
                            center = center
                        ),
                        startAngle = 0f,
                        sweepAngle = 90f,
                        useCenter = true,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                    )
                }

                // Smoothly animate the dot to point towards the direction of strongest historical signal
                val relativeAngle = (strongestRssiAzimuth - currentAzimuth + 360) % 360
                val targetRad = Math.toRadians((relativeAngle - 90).toDouble()) // -90 so 0 is North/Top
                
                val dotRadius = radius * targetDistanceRatio
                val dotX = center.x + (dotRadius * cos(targetRad)).toFloat()
                val dotY = center.y + (dotRadius * sin(targetRad)).toFloat()

                // Dot color depends on distance (closer = red, farther = yellow)
                val dotColor = lerpColor(Color.Yellow, Color.Red, normalizedRssi)
                drawCircle(
                    color = dotColor,
                    radius = 8f + (normalizedRssi * 8f), // Pulses slightly larger when close
                    center = Offset(dotX, dotY)
                )
                
                // Optional: Draw a pulse ring around the target if it's very close
                if (normalizedRssi > 0.8f) {
                    drawCircle(
                        color = Color.Red.copy(alpha = 0.5f),
                        radius = 20f,
                        center = Offset(dotX, dotY),
                        style = Stroke(width = 3f, cap = StrokeCap.Round)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Text("Move around to locate the device.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        Text("Distance indicator updates in real-time.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
    }
}
fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    val r = (start.red + (stop.red - start.red) * fraction).coerceIn(0f, 1f)
    val g = (start.green + (stop.green - start.green) * fraction).coerceIn(0f, 1f)
    val b = (start.blue + (stop.blue - start.blue) * fraction).coerceIn(0f, 1f)
    return Color(r, g, b)
}

