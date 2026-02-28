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
    
    val samples = viewModel.getTrilaterationSamples()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030A12))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // High-Tech Localization Header
        Text(
            text = "نظام التموضع الداخلي الهجين - V2",
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

        // HYBRID GRID VIEW
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color.Cyan.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
        ) {
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
            
            // Mode Indicator
            Surface(
                color = if (locState == LocalizationState.WALK_SAMPLING_MODE) Color.Green.copy(alpha = 0.8f) else Color.Cyan.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(12.dp).align(Alignment.TopStart)
            ) {
                Text(
                    text = if (locState == LocalizationState.WALK_SAMPLING_MODE) "LIVE WALK TRACKING ACTIVE" else "IDLE SCAN",
                    color = if (locState == LocalizationState.WALK_SAMPLING_MODE) Color.Black else Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // CONTROL PANEL
        LocalizationDashboard(
            confidence = confidence,
            errorRadius = errorRadius,
            supervisorPos = supervisorPos,
            locState = locState,
            onRecordSample = {
                targetDevice?.let {
                    viewModel.localizationController.recordSample(it.rawObject.signalStrengthRssi)
                }
            },
            onToggleWalkMode = {
                viewModel.localizationController.startWalkSampling()
            }
        )
    }
}

@Composable
fun LocalizationDashboard(
    confidence: Int,
    errorRadius: Float,
    supervisorPos: Vector2D,
    locState: LocalizationState,
    onRecordSample: () -> Unit,
    onToggleWalkMode: () -> Unit
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
            InfoBlock("نسبة الثقة", "$confidence%", confColor)
            InfoBlock("نطاق الخطأ", "± ${"%.1f".format(errorRadius)} م", Color.Red)
            InfoBlock("إحداثياتك", "(${supervisorPos.x.toInt()}, ${supervisorPos.y.toInt()})", Color.Cyan)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onRecordSample,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("تسجيل عينة", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            
            Button(
                onClick = onToggleWalkMode,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (locState == LocalizationState.WALK_SAMPLING_MODE) Color.Green else Color.DarkGray
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    if (locState == LocalizationState.WALK_SAMPLING_MODE) "وضع المشي نشط" else "تفعيل تتبع المشي",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun InfoBlock(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, color = Color.Gray, fontSize = 9.sp)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
