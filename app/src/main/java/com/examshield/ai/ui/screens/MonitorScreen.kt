package com.examshield.ai.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.examshield.ai.ui.visualization.SpectrumWaterfallRenderer
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.core.content.FileProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    navController: NavController,
    viewModel: MonitorScreenViewModel = hiltViewModel()
) {
    val isScanning by viewModel.isScanning.collectAsState()
    val threatList by viewModel.threatList.collectAsState()
    val orbitalData by viewModel.currentOrbitalData.collectAsState()
    var showAdvisor by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var showTaskPanel by remember { mutableStateOf(false) }
    var showRangeSelector by remember { mutableStateOf(false) }
    val currentRange by viewModel.maxDetectionRange.collectAsState()
    val context = LocalContext.current

    Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Column {
                            Text("EXAMSHIELD_OPS", fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, fontSize = 16.sp)
                            Text("الذكاء الاصطناعي التكتيكي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White
                    )
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.toggleScan() },
                    containerColor = if (isScanning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black,
                    icon = { Icon(painterResource(if (isScanning) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play), contentDescription = null) },
                    text = { Text(if (isScanning) "إيقاف المسح" else "نشر الرادار", fontWeight = FontWeight.Black) }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                SystemStatusPanel(isScanning, threatList.size, orbitalData.satelliteCount, { showRangeSelector = true })
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // ORBITAL UPLINK HUB
                OrbitalUplinkHub(orbitalData)
                
                Spacer(modifier = Modifier.height(16.dp))

                if (showRangeSelector) {
                    RangeSelectorMenu(
                        currentRange = currentRange,
                        onRangeSelected = { 
                            viewModel.setMaxDetectionRange(it)
                            showRangeSelector = false
                        },
                        onDismiss = { showRangeSelector = false }
                    )
                }

                if (!isScanning && threatList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(id = R.mipmap.ic_app_logo_modern),
                                contentDescription = "App Logo",
                                modifier = Modifier.size(120.dp).padding(16.dp).alpha(0.6f)
                            )
                            Text("النظام جاهز", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), letterSpacing = 4.sp, fontWeight = FontWeight.Thin)
                            Text("بانتظار تفعيل الرادار", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), letterSpacing = 2.sp)
                        }
                    }
                } else if (isScanning) {
                    // Room Layout View Removed
                }
                
                /* Legacy Task Panel Removed */

                val criticalThreats = threatList.filter { it.riskLevel == RiskLevel.LEVEL_4_CONFIRMED_THREAT || it.discoveryReason.contains("NEXUS") }
                val activeMonitoring = threatList.filter { !criticalThreats.contains(it) }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    if (criticalThreats.isNotEmpty()) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "⚠️ الأهداف الحرجة (Critical)", 
                                    color = com.examshield.ai.ui.theme.ThreatRed, 
                                    fontWeight = FontWeight.Black
                                )
                                Button(
                                    onClick = { 
                                        val file = viewModel.generateMissionReport(context)
                                        if (file != null) {
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "application/pdf")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Open Mission Report"))
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("توليد تقرير استخباراتي", fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        items(criticalThreats.sortedByDescending { it.confidenceScore }) { threat ->
                             LaunchedEffect(threat.rawObject.macAddress) {
                                 haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                             }
                             ThreatCard(threat = threat, navController = navController, viewModel = viewModel)
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }

                    if (activeMonitoring.isNotEmpty()) {
                        item {
                            Text(
                                text = "📡 أهداف قيد التحليل (Active)", 
                                color = com.examshield.ai.ui.theme.NeonCyan, 
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(activeMonitoring.sortedByDescending { it.confidenceScore }) { threat ->
                            ThreatCard(threat = threat, navController = navController, viewModel = viewModel)
                        }
                    }
                }
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemStatusPanel(isScanning: Boolean, threatCount: Int, satCount: Int, onRangeClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(com.examshield.ai.ui.theme.DarkMatterSurface, shape = MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), MaterialTheme.shapes.medium)
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatusIndicator("SENSORS", if (isScanning) "ACTIVE" else "IDLE", if (isScanning) Color.Green else Color.Gray)
            StatusIndicator("THREATS", threatCount.toString(), if (threatCount > 0) com.examshield.ai.ui.theme.ThreatRed else Color.White)
            StatusIndicator("SAT LINK", if (satCount > 0) "LOCKED ($satCount)" else "SEARCHING", if (satCount > 0) Color.Cyan else Color.Gray)
        }
        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = onRangeClick,
            modifier = Modifier.fillMaxWidth().height(36.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(4.dp)
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("نطاق ومجال البحث المخصص (لوحة المهام)", color = Color.White, fontSize = 10.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangeSelectorMenu(currentRange: Float, onRangeSelected: (Float) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = com.examshield.ai.ui.theme.DarkMatterSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Cyan.copy(alpha = 0.5f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "تحديد نطاق البحث النشط",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                letterSpacing = 1.sp
            )
            Text(
                "اختر مدى الكشف المناسب للمهمة الحالية",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            RangeOption(label = "Close Quarter (3m)", value = 3.0f, isSelected = currentRange == 3.0f, onClick = onRangeSelected)
            Spacer(modifier = Modifier.height(12.dp))
            RangeOption(label = "Standard Precision (5m)", value = 5.0f, isSelected = currentRange == 5.0f, onClick = onRangeSelected)
            Spacer(modifier = Modifier.height(12.dp))
            RangeOption(label = "Wide Area Scan (10m)", value = 10.0f, isSelected = currentRange == 10.0f, onClick = onRangeSelected)
        }
    }
}

@Composable
fun RangeOption(label: String, value: Float, isSelected: Boolean, onClick: (Float) -> Unit) {
    val borderColor = if (isSelected) Color.Cyan else Color.Gray.copy(alpha = 0.3f)
    val bgColor = if (isSelected) Color.Cyan.copy(alpha = 0.1f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick(value) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = if (isSelected) Color.White else Color.Gray, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
        if (isSelected) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Color.Cyan, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun StatusIndicator(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        Text(value, fontWeight = FontWeight.Black, color = valueColor, fontSize = 14.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

@Composable
fun OrbitalUplinkHub(data: com.examshield.ai.domain.repository.OrbitalData) {
    val borderColor = if (data.isSecure) Color.Cyan else Color.DarkGray
    val bgColor = if (data.isSecure) Color(0xFF0A1929) else Color(0xFF1E1E1E)
    val statusText = if (data.isSecure) "ORBITAL UPLINK ESTABLISHED" else "ACQUIRING SATELLITE LOCK..."
    val lockText = if (data.isSecure) "SECURE" else "VULNERABLE"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(2.dp))
            .border(1.dp, borderColor.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(statusText, color = borderColor, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Text("[$lockText]", color = borderColor, fontSize = 9.sp, fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OrbitalInfoBlock("GPS LATITUDE", if (data.isSecure) data.latitude.toString().take(9) else "---", Color.White)
            OrbitalInfoBlock("GPS LONGITUDE", if (data.isSecure) data.longitude.toString().take(9) else "---", Color.White)
            OrbitalInfoBlock("SATELLITES", "${data.satelliteCount} LOCK", if (data.satelliteCount > 0) Color.Green else Color.Gray)
        }
    }
}

@Composable
fun OrbitalInfoBlock(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, color = Color.Gray, fontSize = 8.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

@Composable
fun ThreatCard(threat: ClassificationResult, navController: NavController, viewModel: MonitorScreenViewModel) {
    val borderColor = when (threat.riskLevel) {
        RiskLevel.LEVEL_4_CONFIRMED_THREAT -> com.examshield.ai.ui.theme.ThreatRed
        RiskLevel.LEVEL_3_PROXIMITY_MATCH -> com.examshield.ai.ui.theme.ThreatOrange
        RiskLevel.LEVEL_2_REPEATED -> com.examshield.ai.ui.theme.NebulaPurple
        else -> com.examshield.ai.ui.theme.NeonCyan
    }

    Surface(
        onClick = { navController.navigate("finder/${threat.rawObject.macAddress}") },
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = com.examshield.ai.ui.theme.DarkMatterSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Signal Strength
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(borderColor.copy(alpha = 0.1f), CircleShape)
                        .border(1.dp, borderColor.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${threat.rawObject.signalStrengthRssi}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = borderColor
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = threat.deviceType.name.replace("_", " "),
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = threat.distanceZone.name,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = borderColor.copy(alpha = 0.8f)
                        )
                        Text(
                            text = " • ",
                            color = Color.White.copy(alpha = 0.3f)
                        )
                        Text(
                            text = threat.rawObject.name ?: "UNNAMED_SIGNATURE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${threat.confidenceScore}%",
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = borderColor
                    )
                    Text(
                        text = "تطابق",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = borderColor.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            // STAGE 2: SPECTRUM WATERFALL UI
            SpectrumWaterfallRenderer(
                currentRssi = threat.rawObject.signalStrengthRssi,
                baseColor = borderColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // TACTICAL ACTIONS ROW (Arabic RTL Primary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // FRIENDLY SIGNAL BUTTON (إشارة صديقة)
                OutlinedButton(
                    onClick = { viewModel.markAsFriendly(threat) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text("إشارة صديقة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // CHEATING SIGNAL BUTTON (إشارة غش) - High Priority Red
                Button(
                    onClick = { viewModel.markAsCheating(threat) },
                    modifier = Modifier.weight(1.3f), // Slightly wider to emphasize threat
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.examshield.ai.ui.theme.ThreatRed,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text("إشارة غش", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
