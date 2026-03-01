package com.examshield.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    
    val currentHall by viewModel.currentHall.collectAsState()
    val supervisorPos by viewModel.supervisorPos.collectAsState()
    val estimatedPos by viewModel.estimatedDevicePos.collectAsState()
    val confidence by viewModel.localizationConfidence.collectAsState()
    val errorRadius by viewModel.errorRadius.collectAsState()
    val locState by viewModel.localizationState.collectAsState()
    val identifiedTargets by viewModel.localizationController.targetPoints.collectAsState()
    
    val azureAzimuth by viewModel.azimuth.collectAsState()
    val azurePitch by viewModel.pitch.collectAsState()
    val samples = viewModel.getTrilaterationSamples()
    
    // Auto-enable AR Mode if we are highly confident
    var isArMode by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (granted) {
                isArMode = true
                com.examshield.ai.util.VibrationHelper.vibrateSuccess()
            } else {
                com.examshield.ai.util.VibrationHelper.vibrateWarning()
            }
        }
    )

    // COMPUTER VISION (ML Kit)
    val visionAnalyzer = remember { TargetLockVisionAnalyzer() }
    val visionTargets by visionAnalyzer.detectedTargets.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF010409)) // Deepest Space Black
    ) {
        // BACKGROUND COSMIC GLOW (Simulated)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        0.0f to Color(0xFF00E5FF).copy(alpha = 0.05f),
                        1.0f to Color.Transparent,
                        radius = 2000f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ALIEN HEADER
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "ASTRA NEXUS // ALIEN INTELLIGENCE",
                    color = Color(0xFF00FF41), // Biological Green
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                // val deviceName: String = targetDevice?.rawObject?.name ?: "SCANNING FOR ALPHA SIGNALS..."
                /*
                androidx.compose.material3.Text(
                    text = deviceName.uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.dp
                )
                */
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // MAIN HUD AREA
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .border(
                        width = 1.dp,
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color(0xFF00E5FF).copy(alpha = 0.5f), Color.Transparent)
                        ),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .background(Color.Black.copy(alpha = 0.2f)) // Glass base
            ) {
                if (isArMode) {
                    // AR CAMERA VIEW
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        analyzer = visionAnalyzer
                    )
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
                    
                    // AR HUD Info Overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp)
                            .background(Color(0xFF00FF41).copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                            .border(1.dp, Color(0xFF00FF41).copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "OPTICAL NEURAL LINK ACTIVE // TARGETING...",
                            color = Color(0xFF00FF41),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                } else {
                    // TACTICAL POLAR RADAR (REPLACED SATELLITE GPS)
                    TacticalRadarOverlay(
                        modifier = Modifier.fillMaxSize(),
                        supervisorPos = supervisorPos ?: Vector2D(0f, 0f),
                        devicePos = estimatedPos,
                        currentAzimuth = azureAzimuth,
                        targetDeviceType = targetDevice?.deviceType?.name ?: "UNKNOWN",
                        confidence = confidence.toFloat() / 100f,
                        maxRange = viewModel.maxDetectionRange.collectAsState().value
                    )
                    
                    // RADAR STATIC EFFECT (Visual Polish)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.5f to Color(0xFF00E5FF).copy(alpha = 0.02f),
                                    1f to Color.Transparent
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ALIEN DASHBOARD
            AlienDashboard(
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
                        if (hasCameraPermission) {
                            isArMode = true
                            com.examshield.ai.util.VibrationHelper.vibrateSuccess()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    } else {
                        isArMode = false
                        com.examshield.ai.util.VibrationHelper.vibrateShort()
                    }
                }
            )
        }
    }
}

@Composable
fun AlienDashboard(
    confidence: Int,
    errorRadius: Float,
    locState: LocalizationState,
    isArMode: Boolean,
    onToggleWalkMode: () -> Unit,
    onToggleArMode: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0D1117).copy(alpha = 0.8f)) // Alien Dark Matte
            .border(0.5.dp, Color(0xFF00E5FF).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val confColor = when {
                confidence > 85 -> Color(0xFF00FF41) // Bio Green
                confidence > 50 -> Color(0xFF00E5FF) // Neon Cyan
                else -> Color(0xFFFF1744) // Danger Red
            }
            
            Column {
                Text("INTEGRITY", color = Color.Gray, fontSize = 8.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, letterSpacing = 1.sp)
                Text("${confidence}%", color = confColor, fontWeight = FontWeight.Black, fontSize = 24.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("VAR_MARGIN", color = Color.Gray, fontSize = 8.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text("±${"%.1f".format(errorRadius)}M", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text("CORE_OS", color = Color.Gray, fontSize = 8.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text(
                    text = "ASTRA.V11",
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ALIEN ACTION BUTTONS
        val isReady = errorRadius < 4.0f || confidence > 40
        
        Button(
            onClick = onToggleArMode,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isArMode) Color(0xFFFF1744).copy(0.2f) else if (isReady) Color(0xFF00E5FF).copy(0.1f) else Color.Transparent
            ),
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, 
                if (isArMode) Color(0xFFFF1744) else if (isReady) Color(0xFF00E5FF) else Color.Gray.copy(0.3f)
            )
        ) {
            Text(
                if (isArMode) ">> DISENGAGE NEURAL_AR" 
                else if (isReady) ">> ENGAGE NEURAL_AR LINK" 
                else "// AWAITING SIGNAL LOCK...",
                color = if (isReady || isArMode) Color.White else Color.Gray,
                fontWeight = FontWeight.Black,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 12.sp,
                letterSpacing = 2.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // KINETIC BUTTON
        OutlinedButton(
            onClick = onToggleWalkMode,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, 
                if (locState == LocalizationState.WALK_SAMPLING_MODE) Color(0xFF00FF41) else Color(0xFF00E5FF).copy(0.4f)
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (locState == LocalizationState.WALK_SAMPLING_MODE) Color(0xFF00FF41) else Color(0xFF00E5FF)
            )
        ) {
            Text(
                if (locState == LocalizationState.WALK_SAMPLING_MODE) ">> KINETIC_SCAN: ACTIVE" else ">> INITIALIZE KINETIC_SCAN",
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun InfoBlock(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, color = Color.Gray, fontSize = 8.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}
