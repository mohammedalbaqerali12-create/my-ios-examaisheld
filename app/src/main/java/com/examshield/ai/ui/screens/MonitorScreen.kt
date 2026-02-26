package com.examshield.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.examshield.ai.R
import com.examshield.ai.domain.model.ClassificationResult
import com.examshield.ai.domain.model.DistanceZone
import com.examshield.ai.domain.model.RiskLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    viewModel: MonitorScreenViewModel = hiltViewModel()
) {
    val isScanning by viewModel.isScanning.collectAsState()
    val threatList by viewModel.threatList.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.toggleScan() },
                containerColor = if (isScanning) Color.Red else MaterialTheme.colorScheme.primary
            ) {
                Text(if (isScanning) "Stop" else "Scan", modifier = Modifier.padding(16.dp))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.monitor_dashboard),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                Text(stringResource(R.string.scan_active), color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
            } else if (threatList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Ready to scan", style = MaterialTheme.typography.bodyLarge)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(threatList.sortedByDescending { it.confidenceScore }) { threat ->
                    ThreatCard(threat)
                }
            }
        }
    }
}

@Composable
fun ThreatCard(threat: ClassificationResult) {
    val cardColor = when (threat.riskLevel) {
        RiskLevel.LEVEL_4_CONFIRMED_THREAT -> Color(0xFFFFCCCC)
        RiskLevel.LEVEL_3_PROXIMITY_MATCH -> Color(0xFFFFE0B2)
        RiskLevel.LEVEL_2_REPEATED -> Color(0xFFFFF9C4)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = threat.deviceType.name.replace("_", " "),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = stringResource(R.string.confidence_score, threat.confidenceScore),
                    fontWeight = FontWeight.SemiBold,
                    color = if (threat.confidenceScore > 80) Color.Red else Color.DarkGray
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(stringResource(R.string.device_mac, threat.rawObject.macAddress), fontSize = 14.sp)
            Text(stringResource(R.string.rssi_value, threat.rawObject.signalStrengthRssi), fontSize = 14.sp)
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DistanceBadge(threat.distanceZone)
                RiskBadge(threat.riskLevel)
            }
        }
    }
}

@Composable
fun DistanceBadge(zone: DistanceZone) {
    val color = when (zone) {
        DistanceZone.IMMEDIATE -> Color.Red
        DistanceZone.NEAR -> Color(0xFFFFA000)
        DistanceZone.MEDIUM -> Color.Blue
        DistanceZone.FAR -> Color.Gray
    }
    BadgeBox(text = zone.name, bgColor = color)
}

@Composable
fun RiskBadge(level: RiskLevel) {
    val color = when (level) {
        RiskLevel.LEVEL_4_CONFIRMED_THREAT -> Color.Black
        RiskLevel.LEVEL_3_PROXIMITY_MATCH -> Color.Red
        RiskLevel.LEVEL_2_REPEATED -> Color(0xFFE65100)
        RiskLevel.LEVEL_1_SUSPICIOUS -> Color.DarkGray
    }
    BadgeBox(text = level.name.replace("_", " "), bgColor = color)
}

@Composable
fun BadgeBox(text: String, bgColor: Color) {
    Box(
        modifier = Modifier
            .background(bgColor, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
