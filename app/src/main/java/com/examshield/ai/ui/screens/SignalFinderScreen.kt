package com.examshield.ai.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.ContextCompat
import com.examshield.ai.localization.*
import com.examshield.ai.ui.visualization.*
import com.examshield.ai.vision.TargetLockVisionAnalyzer

@Composable
fun SignalFinderScreen(
    macAddress: String,
    viewModel: MonitorScreenViewModel = hiltViewModel()
) {
    val threatList by viewModel.threatList.collectAsState()
    val targetDevice = threatList.find { it.rawObject.macAddress == macAddress }
    
    val supervisorPos by viewModel.supervisorPos.collectAsState()
    val estimatedPos by viewModel.estimatedDevicePos.collectAsState()
    val confidence by viewModel.localizationConfidence.collectAsState()
    val errorRadius by viewModel.errorRadius.collectAsState()
    val locState by viewModel.localizationState.collectAsState()
    
    val azureAzimuth by viewModel.azimuth.collectAsState()
    val azurePitch by viewModel.pitch.collectAsState()
    
    var isArMode by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (granted) isArMode = true
        }
    )

    val visionAnalyzer = remember { TargetLockVisionAnalyzer() }
    val visionTargets by visionAnalyzer.detectedTargets.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // LAYER 0: STARFIELD PARALLAX
        StarfieldBackground()
        
        // LAYER 1: CINEMATIC GLOW
        Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(0.0f to Color(0xFF00E5FF).copy(alpha = 0.05f), 1.0f to Color.Transparent)))

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // EXTRATERRESTRIAL HEADER
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "TARGET_ACQUISITION // NEURAL_LINK_v4",
                    color = Color.Cyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "MAC: ${macAddress.uppercase()}",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // TACTICAL CORE (RADAR / AR)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f))
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                if (isArMode) {
                    CameraPreview(modifier = Modifier.fillMaxSize(), analyzer = visionAnalyzer)
                    SignalArOverlay(
                        modifier = Modifier.fillMaxSize(),
                        supervisorPos = supervisorPos,
                        devicePos = estimatedPos,
                        currentAzimuth = azureAzimuth,
                        currentPitch = azurePitch,
                        deviceType = targetDevice?.deviceType?.name ?: "UNKNOWN",
                        rssi = targetDevice?.rawObject?.signalStrengthRssi ?: -100,
                        visionTargets = visionTargets
                    )
                } else {
                    TacticalRadarOverlay(
                        modifier = Modifier.fillMaxSize(),
                        supervisorPos = supervisorPos ?: Vector2D(0f, 0f),
                        devicePos = estimatedPos,
                        currentAzimuth = azureAzimuth,
                        targetDeviceType = targetDevice?.deviceType?.name ?: "UNKNOWN",
                        confidence = confidence.toFloat() / 100f,
                        maxRange = viewModel.maxDetectionRange.collectAsState().value
                    )
                }
                
                // HUD FRAME (Internal)
                HUDCornerBrackets()
                ScanlineOverlay()
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ALIEN DASHBOARD (OPERATIVE CONTROLS)
            OperativeDashboard(
                confidence = confidence,
                errorRadius = errorRadius,
                locState = locState,
                isArMode = isArMode,
                onToggleWalkMode = {
                    viewModel.localizationController.startWalkSampling()
                    com.examshield.ai.util.VibrationHelper.vibrateShort()
                },
                onToggleArMode = {
                    if (!isArMode) {
                        if (hasCameraPermission) isArMode = true
                        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    } else {
                        isArMode = false
                    }
                }
            )
        }
    }
}

@Composable
fun OperativeDashboard(
    confidence: Int,
    errorRadius: Float,
    locState: LocalizationState,
    isArMode: Boolean,
    onToggleWalkMode: () -> Unit,
    onToggleArMode: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, Color.Cyan.copy(alpha = 0.1f), RoundedCornerShape(2.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(2.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    val confColor = if (confidence > 80) Color(0xFF00E676) else if (confidence > 40) Color(0xFF00E5FF) else Color(0xFFFF5252)
                    Text("NEURAL_CONFIDENCE", color = Color.Gray, fontSize = 7.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${confidence}", color = confColor, fontWeight = FontWeight.Black, fontSize = 42.sp, fontFamily = FontFamily.Monospace)
                        Text("%", color = confColor.copy(alpha = 0.5f), fontSize = 16.sp, modifier = Modifier.padding(bottom = 6.dp, start = 4.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("ERROR_MARGIN", color = Color.Gray, fontSize = 7.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                    Text("±${"%.2f".format(errorRadius)}M", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, fontFamily = FontFamily.Monospace)
                    
                    val statusText = when(locState) {
                        LocalizationState.WALK_SAMPLING_MODE -> "KINETIC_SYNC"
                        LocalizationState.TRACKING_MOTION -> "POINT_ACQUIRE"
                        else -> "IDLE_LINK"
                    }
                    Text(statusText, color = Color.Cyan.copy(alpha = 0.6f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // OVERDRIVE AR TRIGGER
            val isReady = confidence > 30
            Button(
                onClick = onToggleArMode,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isArMode) Color.Red.copy(0.15f) else if (isReady) Color.Cyan.copy(0.05f) else Color.Transparent),
                shape = RoundedCornerShape(2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isArMode) Color.Red else if (isReady) Color.Cyan else Color.White.copy(0.1f))
            ) {
                Text(
                    if (isArMode) "DISCONNECT_AR_HUD" else if (isReady) "ENGAGE_INTERSTELLAR_HUD" else "WAITING_FOR_NEURAL_FIX",
                    color = if (isReady || isArMode) Color.White else Color.Gray,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp
                )
            }

            if (!isArMode) {
                Spacer(modifier = Modifier.height(12.dp))

                // KINETIC SCAN TRIGGER
                OutlinedButton(
                    onClick = onToggleWalkMode,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (locState == LocalizationState.WALK_SAMPLING_MODE) Color(0xFF00E676) else Color.Cyan.copy(0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (locState == LocalizationState.WALK_SAMPLING_MODE) Color(0xFF00E676) else Color.Cyan)
                ) {
                    Text(
                        if (locState == LocalizationState.WALK_SAMPLING_MODE) "KINETIC_SYNC_ACTIVE" else "START_KINETIC_MAPPING",
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
