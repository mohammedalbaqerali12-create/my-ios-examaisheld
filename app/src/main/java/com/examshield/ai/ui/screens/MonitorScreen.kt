package com.examshield.ai.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.examshield.ai.R
import com.examshield.ai.domain.model.ClassificationResult
import com.examshield.ai.domain.model.DistanceZone
import com.examshield.ai.domain.model.RiskLevel
import com.examshield.ai.domain.ai.CentralNeuralLink
import com.examshield.ai.ui.visualization.SpectrumWaterfallRenderer
import androidx.core.content.FileProvider
import android.content.Intent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.zIndex

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    navController: NavController,
    viewModel: MonitorScreenViewModel = hiltViewModel()
) {
    val isScanning by viewModel.isScanning.collectAsState()
    val threatList by viewModel.threatList.collectAsState()
    val orbitalData by viewModel.currentOrbitalData.collectAsState()
    val aiNeuralState by viewModel.aiNeuralState.collectAsState()
    val swarmNodeCount by viewModel.swarmNodeCount.collectAsState()
    var showRangeSelector by remember { mutableStateOf(false) }
    val currentRange by viewModel.maxDetectionRange.collectAsState()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // LAYER 0: STARFIELD PARALLAX
        StarfieldBackground()
        
        // LAYER 1: SCANLINE EFFECT
        ScanlineOverlay()

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                AstraTopBar()
            },
            floatingActionButton = {
                OverdriveScanButton(isScanning, onToggle = { viewModel.toggleScan() })
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                // TACTICAL SYSTEM PANEL (GLASS)
                TacticalStatusPanel(
                    isScanning = isScanning,
                    threatCount = threatList.size,
                    neuralState = aiNeuralState,
                    swarmNodes = swarmNodeCount,
                    onRangeClick = { showRangeSelector = true }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ORBITAL UPLINK HUB (MODERNIZED)
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
                        EmptyMonitorState()
                    }
                }

                val criticalThreats = threatList.filter { it.riskLevel == RiskLevel.LEVEL_4_CONFIRMED_THREAT || it.discoveryReason.contains("NEXUS") || it.discoveryReason.contains("SWARM") }
                val activeMonitoring = threatList.filter { !criticalThreats.contains(it) }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    if (criticalThreats.isNotEmpty()) {
                        item {
                            ThreatSectionHeader("⚠️ الأهداف الحرجة (Critical Targets)", com.examshield.ai.ui.theme.ThreatRed)
                        }
                        items(criticalThreats.sortedByDescending { it.confidenceScore }, key = { it.rawObject.macAddress }) { threat ->
                             TacticalThreatPod(threat = threat, onClick = { navController.navigate("finder/${threat.rawObject.macAddress}") }, onFriendly = { viewModel.markAsFriendly(it) }, onCheating = { viewModel.markAsCheating(it) })
                        }
                    }

                    if (activeMonitoring.isNotEmpty()) {
                        item {
                            ThreatSectionHeader("📡 أهداف قيد المسح (Active Signals)", com.examshield.ai.ui.theme.NeonCyan)
                        }
                        items(activeMonitoring.sortedByDescending { it.confidenceScore }, key = { it.rawObject.macAddress }) { threat ->
                             TacticalThreatPod(threat = threat, onClick = { navController.navigate("finder/${threat.rawObject.macAddress}") }, onFriendly = { viewModel.markAsFriendly(it) }, onCheating = { viewModel.markAsCheating(it) })
                        }
                    }
                }
            }
        }
        
        // HUD BRACKETS
        HUDCornerBrackets()
    }
}

@Composable
fun StarfieldBackground() {
    val infiniteTransition = rememberInfiniteTransition()
    val driftX by infiniteTransition.animateFloat(0f, 100f, infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse))
    val driftY by infiniteTransition.animateFloat(0f, 80f, infiniteRepeatable(tween(25000, easing = LinearEasing), RepeatMode.Reverse))

    Canvas(modifier = Modifier.fillMaxSize()) {
        val starCount = 150
        val random = java.util.Random(42)
        for (i in 0 until starCount) {
            val x = (random.nextFloat() * size.width + driftX) % size.width
            val y = (random.nextFloat() * size.height + driftY) % size.height
            val alpha = random.nextFloat() * 0.5f + 0.1f
            val radius = random.nextFloat() * 1.5.dp.toPx()
             drawCircle(Color.White, radius, Offset(x, y), alpha = alpha)
        }
    }
}

@Composable
fun ScanlineOverlay() {
    val infiniteTransition = rememberInfiniteTransition()
    val lineOffset by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)))

    Canvas(modifier = Modifier.fillMaxSize().alpha(0.1f)) {
        val lineCount = 100
        val spacing = size.height / lineCount
        val currentOffset = lineOffset * size.height
        
        for (i in 0 until lineCount) {
             val y = (i * spacing + currentOffset) % size.height
             drawLine(Color.Cyan, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AstraTopBar() {
    TopAppBar(
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ASTRA_NEXUS", fontWeight = FontWeight.Black, letterSpacing = 3.sp, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.height(1.dp).width(40.dp).background(Color.Cyan.copy(alpha = 0.4f)))
                Spacer(modifier = Modifier.width(8.dp))
                Text("OPS_v4.0", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 8.sp)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White)
    )
}

@Composable
fun OverdriveScanButton(active: Boolean, onToggle: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition()
    val glowScale by infiniteTransition.animateFloat(1f, 1.25f, infiniteRepeatable(tween(1000), RepeatMode.Reverse))
    
    Box(contentAlignment = Alignment.Center) {
        if (active) {
            Box(modifier = Modifier.size(70.dp).scale(glowScale).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f), CircleShape).border(2.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f), CircleShape))
        }
        
        ExtendedFloatingActionButton(
            onClick = onToggle,
            containerColor = if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            contentColor = Color.Black,
            shape = CircleShape,
             modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
        ) {
            Icon(painterResource(if (active) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play), contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (active) "OFFLINE" else "DEPLOY_RADAR", fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
    }
}

@Composable
fun TacticalStatusPanel(isScanning: Boolean, threatCount: Int, neuralState: CentralNeuralLink.NeuralState, swarmNodes: Int, onRangeClick: () -> Unit) {
    val neuralColor = when(neuralState) {
        CentralNeuralLink.NeuralState.OVERDRIVE -> Color(0xFFFF00FF)
        CentralNeuralLink.NeuralState.EVOLVING -> Color(0xFFBB86FC)
        CentralNeuralLink.NeuralState.CRITICAL -> com.examshield.ai.ui.theme.ThreatRed
        CentralNeuralLink.NeuralState.STEALTH -> Color(0xFF03DAC6)
        else -> Color.Green
    }

    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.1f), MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("SYSTEM_OPERATIONAL", fontSize = 8.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).background(if (isScanning) Color.Green else Color.Red, CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isScanning) "ACTIVE" else "STANDBY", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text("SWARM_NODES", fontSize = 8.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Text("$swarmNodes ONLINE", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.Cyan)
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // NEURAL LINK PULSE (CINEMATIC)
            Row(modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                 Box(modifier = Modifier.size(12.dp).background(neuralColor, CircleShape).border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape))
                 Spacer(modifier = Modifier.width(12.dp))
                 Column {
                     Text("AI NEURAL LINK", fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                     Text(neuralState.name, fontSize = 16.sp, fontWeight = FontWeight.Black, color = neuralColor, letterSpacing = 2.sp)
                 }
                 Spacer(modifier = Modifier.weight(1f))
                 IconButton(onClick = onRangeClick, modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)) {
                     Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                 }
            }
        }
    }
}

@Composable
fun TacticalThreatPod(threat: ClassificationResult, onClick: () -> Unit, onFriendly: (ClassificationResult) -> Unit, onCheating: (ClassificationResult) -> Unit) {
    val borderColor = when (threat.riskLevel) {
        RiskLevel.LEVEL_4_CONFIRMED_THREAT -> com.examshield.ai.ui.theme.ThreatRed
        RiskLevel.LEVEL_3_PROXIMITY_MATCH -> com.examshield.ai.ui.theme.ThreatOrange
        else -> com.examshield.ai.ui.theme.NeonCyan
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D0D)),
        shape = RoundedCornerShape(2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // TACTICAL GLYPH
                Box(modifier = Modifier.size(45.dp).background(borderColor.copy(alpha = 0.1f)).border(1.dp, borderColor.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                    Text(threat.rawObject.macAddress.takeLast(4), color = borderColor, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    // Decorative corners
                    Box(modifier = Modifier.align(Alignment.TopStart).size(6.dp).border(1.dp, borderColor, RoundedCornerShape(topStart = 2.dp)))
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(threat.deviceType.name, fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White)
                    Text(threat.discoveryReason, fontSize = 9.sp, color = Color.Gray, maxLines = 1)
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text("${threat.confidenceScore}%", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = borderColor)
                    Text("MATCH", fontSize = 7.sp, color = borderColor.copy(alpha = 0.7f), fontWeight = FontWeight.Black)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ANALYTICS PREVIEW
            Box(modifier = Modifier.fillMaxWidth().height(40.dp).background(Color.Black).alpha(0.6f)) {
                SpectrumWaterfallRenderer(currentRssi = threat.rawObject.signalStrengthRssi, baseColor = borderColor, modifier = Modifier.fillMaxSize())
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onFriendly(threat) }, modifier = Modifier.weight(1f).height(32.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray), shape = RoundedCornerShape(2.dp)) {
                    Text("SAFE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Button(onClick = { onCheating(threat) }, modifier = Modifier.weight(1f).height(32.dp), colors = ButtonDefaults.buttonColors(containerColor = com.examshield.ai.ui.theme.ThreatRed), shape = RoundedCornerShape(2.dp)) {
                    Text("MARK_THREAT", fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun HUDCornerBrackets() {
    Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Bracket(Modifier.align(Alignment.TopStart))
        Bracket(Modifier.align(Alignment.TopEnd).graphicsLayer(rotationY = 180f))
        Bracket(Modifier.align(Alignment.BottomStart).graphicsLayer(rotationX = 180f))
        Bracket(Modifier.align(Alignment.BottomEnd).graphicsLayer(rotationZ = 180f))
    }
}

@Composable
fun Bracket(modifier: Modifier) {
    Canvas(modifier = modifier.size(30.dp).alpha(0.3f)) {
        val stroke = 2.dp.toPx()
        drawLine(Color.Cyan, Offset(0f, 0f), Offset(30.dp.toPx(), 0f), strokeWidth = stroke)
        drawLine(Color.Cyan, Offset(0f, 0f), Offset(0f, 30.dp.toPx()), strokeWidth = stroke)
    }
}

@Composable
fun ThreatSectionHeader(title: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(4.dp).background(color))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, color = color, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.height(1.dp).weight(1f).background(color.copy(alpha = 0.2f)))
    }
}

@Composable
fun EmptyMonitorState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
             val infiniteTransition = rememberInfiniteTransition()
             val pulseScale by infiniteTransition.animateFloat(1f, 1.1f, infiniteRepeatable(tween(2000), RepeatMode.Reverse))
             
             Box(modifier = Modifier.size(150.dp).scale(pulseScale).border(1.dp, Color.Cyan.copy(alpha = 0.1f), CircleShape))
             Image(painterResource(R.mipmap.ic_app_logo_modern), contentDescription = null, modifier = Modifier.size(80.dp).alpha(0.3f))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("NO_SIGNALS_ACQUIRED", color = Color.Cyan.copy(alpha = 0.4f), fontWeight = FontWeight.Thin, letterSpacing = 4.sp, fontSize = 12.sp)
    }
}

@Composable
fun OrbitalUplinkHub(data: com.examshield.ai.domain.repository.OrbitalData) {
    val alpha by animateFloatAsState(if (data.isSecure) 1f else 0.5f)
    
    Card(
        modifier = Modifier.fillMaxWidth().alpha(alpha),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = if (data.isSecure) Color.Cyan else Color.Red, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("ORBITAL_TELEMETRY", fontSize = 7.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                Row {
                    Text(data.latitude.toString().take(8), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(" / ", color = Color.Gray)
                    Text(data.longitude.toString().take(8), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.background(if (data.isSecure) Color.Cyan.copy(alpha = 0.1f) else Color.Red.copy(alpha = 0.1f), RoundedCornerShape(2.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(if (data.isSecure) "SECURE" else "LOCKING", color = if (data.isSecure) Color.Cyan else Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangeSelectorMenu(currentRange: Float, onRangeSelected: (Float) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A0A0A),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Cyan.copy(alpha = 0.3f)) }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
            Text("DETECTION_PAYLOAD_RANGE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(24.dp))
            
            RangeOption("CQB_MODE (3m)", 3.0f, currentRange == 3.0f, onRangeSelected)
            Spacer(modifier = Modifier.height(12.dp))
            RangeOption("STANDARD_OPS (5m)", 5.0f, currentRange ==  5.0f, onRangeSelected)
            Spacer(modifier = Modifier.height(12.dp))
            RangeOption("EXTENDED_SCAN (10m)", 10.0f, currentRange == 10.0f, onRangeSelected)
        }
    }
}

@Composable
fun RangeOption(label: String, value: Float, isSelected: Boolean, onClick: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(if (isSelected) Color.Cyan.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.03f), RoundedCornerShape(4.dp))
            .border(1.dp, if (isSelected) Color.Cyan else Color.Transparent, RoundedCornerShape(4.dp))
            .clickable { onClick(value) }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(label, color = if (isSelected) Color.Cyan else Color.White, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal, fontSize = 12.sp)
    }
}
