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
    
    val supervisorGps by viewModel.localizationController.currentGpsLoc.collectAsState()
    val targetGps by viewModel.localizationController.targetGps.collectAsState()
    
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
            text = "نظام التموضع العالمي الهجين (Hybrid GPT-Engine)",
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

        // HYBRID GRID VIEW (GPS Mapped)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, Color.Cyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
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
            
            // GPS Tracker Pin
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(12.dp).align(Alignment.BottomStart)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("إحداثيات المشرف (GPS):", color = Color.Gray, fontSize = 8.sp)
                    Text("${supervisorGps?.first ?: 0.0}, ${supervisorGps?.second ?: 0.0}", color = Color.Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // GPS & AI STATUS DASHBOARD
        LocalizationDashboard(
            confidence = confidence,
            errorRadius = errorRadius,
            targetGps = targetGps,
            locState = locState,
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
    targetGps: Pair<Double, Double>?,
    locState: LocalizationState,
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
            InfoBlock("ثقة الذكاء الاصطناعي", "$confidence%", confColor)
            InfoBlock("دقة التموضع", "± ${"%.1f".format(errorRadius)} م", Color.Red)
            
            Column {
                Text("إحداثيات الهدف (Target GPS)", color = Color.Gray, fontSize = 9.sp)
                Text(
                    text = if (targetGps != null) "${"%.5f".format(targetGps.first)}, ${"%.5f".format(targetGps.second)}" else "جاري الحساب...",
                    color = Color.Yellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SINGLE AUTOMATIC BUTTON
        Button(
            onClick = onToggleWalkMode,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (locState == LocalizationState.WALK_SAMPLING_MODE) Color(0xFF00FF00) else Color.Cyan
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                if (locState == LocalizationState.WALK_SAMPLING_MODE) "التتبع الآلي للهدف نشط (GPS Enabled)" else "تفعيل المسح الجغرافي الآلي (GPS Mode)",
                color = Color.Black,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp
            )
        }
        
        Text(
            text = "النظام الآن يسجل العينات آلياً عبر الجيبيس عند المشي لتحليل الذكاء الاصطناعي.",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
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
