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

    val animatedSize by animateFloatAsState(targetValue = normalizedRssi * 300f, animationSpec = tween(500))
    val animatedColor by animateColorAsState(
        targetValue = lerpColor(Color.Blue, Color.Red, normalizedRssi),
        animationSpec = tween(500)
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Signal Finder", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(16.dp))
        Text(device.name ?: "Unknown Device", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(device.macAddress, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(64.dp))

        Box(
            modifier = Modifier
                .size(animatedSize.dp)
                .clip(CircleShape)
                .background(animatedColor)
        )
        
        Spacer(modifier = Modifier.height(64.dp))
        Text("Move closer to the device.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        Text("The circle will grow and turn red.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
    }
}

fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    val r = (start.red + (stop.red - start.red) * fraction).coerceIn(0f, 1f)
    val g = (start.green + (stop.green - start.green) * fraction).coerceIn(0f, 1f)
    val b = (start.blue + (stop.blue - start.blue) * fraction).coerceIn(0f, 1f)
    return Color(r, g, b)
}

