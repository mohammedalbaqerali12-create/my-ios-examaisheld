package com.examshield.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import com.examshield.ai.localization.*
import com.examshield.ai.ui.visualization.*

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030A12))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ... (rest of the header remains)
            Text(
                text = "نظام التموضع الراداري (Astra Nexus AR)",
                color = Color.Cyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = targetDevice?.rawObject?.name ?: "بحث عن إشارة...",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            // MAIN VIEW AREA
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, Color.Cyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                if (isArMode) {
                    // AR CAMERA VIEW
                    CameraPreview(modifier = Modifier.fillMaxSize())
                    SignalArOverlay(
                        modifier = Modifier.fillMaxSize(),
                        supervisorPos = supervisorPos,
                        devicePos = estimatedPos,
                        currentAzimuth = azureAzimuth,
                        currentPitch = azurePitch,
                        deviceType = targetDevice?.deviceType?.name ?: "UNKNOWN",
                        rssi = targetDevice?.rawObject?.signalStrengthRssi ?: -100
                    )
                    
                    // AR HUD Info
                    Text(
                        "وضع الرؤية الرادارية: قم بالتوجيه نحو الهدف",
                        color = Color.Green,
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).background(Color.Black.copy(0.5f), RoundedCornerShape(4.dp)).padding(4.dp)
                    )
                } else {
                    // GRID VIEW
                    HallGridView(
                        hall = currentHall,
                        supervisorPos = supervisorPos,
                        devicePos = estimatedPos,
                        errorRadius = errorRadius,
                        samples = samples,
                        identifiedTargets = identifiedTargets,
                        isWalkMode = locState == LocalizationState.WALK_SAMPLING_MODE,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    HeatmapRenderer(
                        hall = currentHall,
                        targetPosition = estimatedPos,
                        confidence = (confidence.toFloat() / 100f),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // AI STATUS DASHBOARD
            LocalizationDashboard(
                confidence = confidence,
                errorRadius = errorRadius,
                locState = locState,
                isArMode = isArMode,
                onToggleWalkMode = {
                    viewModel.localizationController.startWalkSampling()
                },
                onToggleArMode = {
                    isArMode = !isArMode
                }
            )
        }
        
        // Mode Switch Floating Button Label
        if (isArMode) {
            IconButton(
                onClick = { isArMode = false },
                modifier = Modifier.align(Alignment.TopStart).padding(24.dp).background(Color.Black.copy(0.4f), RoundedCornerShape(50))
            ) {
                Icon(
                    imageVector = Icons.Default.Close, 
                    contentDescription = "Close AR", 
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun LocalizationDashboard(
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
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val confColor = when {
                confidence > 85 -> Color.Green
                confidence > 50 -> Color.Yellow
                else -> Color.Red
            }
            InfoBlock("ثقة الذكاء الاصطناعي", "$confidence%", confColor)
            InfoBlock("دقة التموضع (Error)", "± ${"%.1f".format(errorRadius)} م", Color.Red)
            
            Column {
                Text("نظام التتبع (Engine)", color = Color.Gray, fontSize = 9.sp)
                Text(
                    text = "Astra Nexus Kinetic",
                    color = Color.Cyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AR SEEK BUTTON (Relaxed thresholds for better UX)
        val isReady = errorRadius < 4.0f || confidence > 40
        
        Button(
            onClick = onToggleArMode,
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(bottom = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isArMode) Color.Red else if (isReady) Color(0xFF00E676) else Color(0xFF424242)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                if (isArMode) "إغلاق التتبع البصري بالكاميرا" 
                else if (isReady) "تفعيل رادار الكاميرا (AR Seek)" 
                else "جاري تحليل الإشارة... حرك الهاتف لتفعيل الكاميرا",
                color = if (isReady || isArMode) Color.Black else Color.Gray,
                fontWeight = FontWeight.Bold
            )
        }

        // KINETIC BUTTON (Step Detection)
        Button(
            onClick = onToggleWalkMode,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (locState == LocalizationState.WALK_SAMPLING_MODE) Color(0xFF00FF00) else Color(0xFF1E88E5)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                if (locState == LocalizationState.WALK_SAMPLING_MODE) "التتبع الحركي نشط (امشِ لتسجيل النقاط)" else "تفعيل المسح الحركي الآلي (Kinetic Mode)",
                color = Color.Black,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp
            )
        }
        
        Text(
            text = "النظام الآن يعتمد على حساب الخطوات ومستشعرات الهاتف الداخلية بنسبة 100% (صفر GPS) لضمان الدقة العالية داخل القاعات.",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp)
        )
    }
}

@Composable
fun InfoBlock(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, color = Color.Gray, fontSize = 9.sp)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
