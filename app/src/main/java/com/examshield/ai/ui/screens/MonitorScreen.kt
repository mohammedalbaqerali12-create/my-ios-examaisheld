package com.examshield.ai.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.examshield.ai.R
import com.examshield.ai.domain.model.ClassificationResult
import com.examshield.ai.domain.model.DistanceZone
import com.examshield.ai.domain.model.RiskLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    navController: NavController,
    viewModel: MonitorScreenViewModel = hiltViewModel()
) {
    val isScanning by viewModel.isScanning.collectAsState()
    val threatList by viewModel.threatList.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.toggleScan() },
                containerColor = if (isScanning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(if (isScanning) "STOP SCAN" else "INITIATE SCAN", fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                )
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.monitor_dashboard),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.onBackground
            )

            if (isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface
                )
                Text(stringResource(R.string.scan_active).uppercase(), color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(16.dp))
            } else if (threatList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(id = R.mipmap.ic_app_logo_modern),
                            contentDescription = "App Logo",
                            modifier = Modifier.size(150.dp).padding(16.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("SYSTEM IDLE", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), letterSpacing = 3.sp)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(threatList.sortedByDescending { it.confidenceScore }) { threat ->
                    ThreatCard(threat = threat, navController = navController)
                }
            }
        }
    }
}

@Composable
fun ThreatCard(threat: ClassificationResult, navController: NavController) {
    val borderColor = when (threat.riskLevel) {
        RiskLevel.LEVEL_4_CONFIRMED_THREAT -> com.examshield.ai.ui.theme.ThreatRed
        RiskLevel.LEVEL_3_PROXIMITY_MATCH -> com.examshield.ai.ui.theme.ThreatOrange
        RiskLevel.LEVEL_2_REPEATED -> com.examshield.ai.ui.theme.NebulaPurple
        else -> com.examshield.ai.ui.theme.NeonCyan
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { navController.navigate("finder/${threat.rawObject.macAddress}") }
            .background(com.examshield.ai.ui.theme.GlassSurface, shape = MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor.copy(alpha = 0.8f)),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = threat.deviceType.name.replace("_", " "),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${threat.confidenceScore}% MATCH",
                    fontWeight = FontWeight.SemiBold,
                    color = if (threat.confidenceScore > 80) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                stringResource(R.string.device_mac, threat.rawObject.macAddress), 
                fontSize = 12.sp, 
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                stringResource(R.string.rssi_value, threat.rawObject.signalStrengthRssi), 
                fontSize = 12.sp, 
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            if (threat.estimatedDistanceMeters > 0) {
                Text(
                    stringResource(R.string.estimated_distance, threat.estimatedDistanceMeters), 
                    fontSize = 12.sp, 
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DistanceBadge(threat.distanceZone)
                RiskBadge(threat.riskLevel)
            }
        }
    }
}

@Composable
fun DistanceBadge(zone: DistanceZone) {
    val color = when (zone) {
        DistanceZone.IMMEDIATE -> com.examshield.ai.ui.theme.ThreatRed
        DistanceZone.NEAR -> com.examshield.ai.ui.theme.ThreatOrange
        DistanceZone.MEDIUM -> com.examshield.ai.ui.theme.NeonCyan
        DistanceZone.FAR -> Color.Gray
    }
    BadgeBox(text = zone.name, borderColor = color, textColor = color)
}

@Composable
fun RiskBadge(level: RiskLevel) {
    val color = when (level) {
        RiskLevel.LEVEL_4_CONFIRMED_THREAT -> com.examshield.ai.ui.theme.ThreatRed
        RiskLevel.LEVEL_3_PROXIMITY_MATCH -> com.examshield.ai.ui.theme.ThreatOrange
        RiskLevel.LEVEL_2_REPEATED -> com.examshield.ai.ui.theme.NebulaPurple
        RiskLevel.LEVEL_1_SUSPICIOUS -> com.examshield.ai.ui.theme.NeonCyan
    }
    BadgeBox(text = level.name.replace("_", " "), borderColor = color, textColor = color)
}

@Composable
fun BadgeBox(text: String, borderColor: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.CircleShape)
            .border(1.dp, borderColor, androidx.compose.foundation.shape.CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = text, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}
