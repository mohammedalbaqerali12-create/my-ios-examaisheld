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
import com.examshield.ai.domain.ai.CentralNeuralLink

@Composable
fun SignalFinderScreen(
    macAddress: String,
    viewModel: MonitorScreenViewModel = hiltViewModel()
) {
    val threatList by viewModel.threatList.collectAsState()
    val targetDevice = threatList.find { it.rawObject.macAddress == macAddress }
    
    LaunchedEffect(macAddress) {
        viewModel.localizationController.targetMacAddress = macAddress
    }
    
    DisposableEffect(Unit) {
        onDispose {
            viewModel.localizationController.targetMacAddress = null
        }
    }
    
    val supervisorPos by viewModel.supervisorPos.collectAsState()
    val estimatedPos by viewModel.estimatedDevicePos.collectAsState()
    val confidence by viewModel.localizationConfidence.collectAsState()
    val errorRadius by viewModel.errorRadius.collectAsState()
    val locState by viewModel.localizationState.collectAsState()
    val aiNeuralState by viewModel.aiNeuralState.collectAsState()
    
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

    val infiniteTransition = rememberInfiniteTransition()
    val primeGlow by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse)
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // LAYER 0: STARFIELD PARALLAX
        StarfieldBackground()
        
        // LAYER 1: CINEMATIC GLOW
        val glowColor = if (aiNeuralState == CentralNeuralLink.NeuralState.PRIME_SYNERGY) com.examshield.ai.ui.theme.PrimeGold else com.examshield.ai.ui.theme.NeonCyan
        Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(0.0f to glowColor.copy(alpha = 0.08f), 1.0f to Color.Transparent)))

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // EXTRATERRESTRIAL HEADER
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = if (aiNeuralState == CentralNeuralLink.NeuralState.PRIME_SYNERGY) "ASTRA_PRIME // SYNC_LOCK_v5.2" else "TARGET_ACQUISITION // NEURAL_LINK_v4",
                    color = glowColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.alpha(0.7f + 0.3f * primeGlow)
                )
                Text(
                    text = "HARDWARE_ID: ${macAddress.uppercase()}",
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
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, glowColor.copy(alpha = 0.15f))
                    .border(if (aiNeuralState == CentralNeuralLink.NeuralState.PRIME_SYNERGY) 3.dp else 0.dp, com.examshield.ai.ui.theme.PrimeGold.copy(alpha = 0.1f * primeGlow), RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
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
                        visionTargets = visionTargets,
                        neuralState = aiNeuralState
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
                neuralState = aiNeuralState,
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
    neuralState: CentralNeuralLink.NeuralState,
    onToggleWalkMode: () -> Unit,
    onToggleArMode: () -> Unit
) {
    val layoutColor = if (neuralState == CentralNeuralLink.NeuralState.PRIME_SYNERGY) com.examshield.ai.ui.theme.PrimeGold else com.examshield.ai.ui.theme.NeonCyan
    
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, layoutColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    val confColor = if (confidence > 85) Color(0xFF00E676) else if (confidence > 50) layoutColor else com.examshield.ai.ui.theme.ThreatRed
                    Text("NEURAL_CONFIDENCE", color = Color.Gray, fontSize = 7.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${confidence}", color = confColor, fontWeight = FontWeight.Black, fontSize = 42.sp, fontFamily = FontFamily.Monospace)
                        Text("%", color = confColor.copy(alpha = 0.5f), fontSize = 16.sp, modifier = Modifier.padding(bottom = 6.dp, start = 4.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("PRECISION_MARGIN", color = Color.Gray, fontSize = 7.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                    Text("±${"%.2f".format(errorRadius)}M", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, fontFamily = FontFamily.Monospace)
                    
                    val statusText = when(locState) {
                        LocalizationState.WALK_SAMPLING_MODE -> "KINETIC_STREAM"
                        LocalizationState.TRACKING_MOTION -> "ACTIVE_LOCK"
                        else -> "SYSTEM_LINK"
                    }
                    Text(statusText, color = layoutColor.copy(alpha = 0.6f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // OVERDRIVE AR TRIGGER
            val isReady = confidence > 25
            Button(
                onClick = onToggleArMode,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isArMode) Color.Red.copy(0.15f) else if (isReady) layoutColor.copy(0.08f) else Color.Transparent),
                shape = RoundedCornerShape(2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isArMode) Color.Red else if (isReady) layoutColor else Color.White.copy(0.1f))
            ) {
                Text(
                    if (isArMode) "DISCONNECT_AR_HUD" else if (isReady) "ENGAGE_ASTRA_PRIME_HUD" else "WAITING_FOR_NEURAL_LINK",
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
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (locState == LocalizationState.WALK_SAMPLING_MODE) Color(0xFF00E676) else layoutColor.copy(0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (locState == LocalizationState.WALK_SAMPLING_MODE) Color(0xFF00E676) else layoutColor)
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
