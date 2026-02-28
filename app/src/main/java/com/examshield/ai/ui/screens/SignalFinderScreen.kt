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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030A12))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // High-Tech Localization Header
        Text(
            text = "نظام التموضع الداخلي الهجين",
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
                modifier = Modifier.fillMaxSize()
            )
            
            HeatmapRenderer(
                hall = currentHall,
                targetPosition = estimatedPos,
                confidence = (confidence.toFloat() / 100f),
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // CONTROL PANEL
        LocalizationDashboard(
            confidence = confidence,
            errorRadius = errorRadius,
            supervisorPos = supervisorPos,
            onRecordSample = {
                targetDevice?.let {
                    viewModel.localizationController.recordSample(it.rawObject.signalStrengthRssi)
                }
            }
        )
    }
}

@Composable
fun LocalizationDashboard(
    confidence: Int,
    errorRadius: Float,
    supervisorPos: Vector2D,
    onRecordSample: () -> Unit
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
            InfoBlock("الدقة (الثقة)", "$confidence%", if (confidence > 70) Color.Green else Color.Yellow)
            InfoBlock("نطاق الخطأ", "± ${"%.1f".format(errorRadius)} متر", Color.Red)
            InfoBlock("إحداثياتك", "(${supervisorPos.x.toInt()}, ${supervisorPos.y.toInt()})", Color.Cyan)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onRecordSample,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text("تسجيل عينة موقع (Record Position Sample)", color = Color.Black, fontWeight = FontWeight.Bold)
        }
        
        Text(
            text = "للحصول على أفضل النتائج، سجل 3 عينات من زوايا مختلفة",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
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
