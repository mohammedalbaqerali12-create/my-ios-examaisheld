package com.examshield.ai.ui.visualization

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SpectrumWaterfallRenderer(
    currentRssi: Int,
    baseColor: Color,
    modifier: Modifier = Modifier
) {
    // Keep a rolling history of the last 40 data points
    val historySize = 40
    val rssiHistory = remember { mutableStateListOf<Int>() }

    LaunchedEffect(currentRssi) {
        rssiHistory.add(currentRssi)
        if (rssiHistory.size > historySize) {
            rssiHistory.removeAt(0)
        }
    }

    // Scroll animation effect
    val infiniteTransition = rememberInfiniteTransition()
    val scrollOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = modifier
            .background(Color(0xFF0D0D15), RoundedCornerShape(4.dp))
            .border(1.dp, baseColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            
            // Draw Grid Lines (Neon Cyberpunk Style)
            val gridColor = baseColor.copy(alpha = 0.1f)
            for (i in 1..4) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, height * (i / 4f)),
                    end = Offset(width, height * (i / 4f)),
                    strokeWidth = 1f
                )
            }
            for (i in 1..8) {
                val xPos = width * (i / 8f) - (scrollOffset * (width / 8f))
                if (xPos > 0) {
                    drawLine(
                        color = gridColor,
                        start = Offset(xPos, 0f),
                        end = Offset(xPos, height),
                        strokeWidth = 1f
                    )
                }
            }

            // Draw Waterfall Line
            if (rssiHistory.isNotEmpty()) {
                val path = Path()
                val stepX = width / (historySize - 1)
                
                rssiHistory.forEachIndexed { index, rssi ->
                    // RSSI usually ranges from -100 (weak) to -30 (strong)
                    val normalizedRssi = (rssi + 100f) / 70f
                    val clamped = normalizedRssi.coerceIn(0.1f, 1.0f)
                    
                    val x = index * stepX
                    val y = height - (clamped * height)
                    
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        // Smooth bezier curve approximations
                        val prevX = (index - 1) * stepX
                        val prevRssi = (rssiHistory[index - 1] + 100f) / 70f
                        val prevY = height - (prevRssi.coerceIn(0.1f, 1.0f) * height)
                        
                        path.cubicTo(
                            prevX + stepX / 2f, prevY,
                            x - stepX / 2f, y,
                            x, y
                        )
                    }
                }
                
                // Draw glow behind the line
                drawPath(
                    path = path,
                    color = baseColor.copy(alpha = 0.3f),
                    style = Stroke(width = 8f)
                )
                
                // Draw sharp center line
                drawPath(
                    path = path,
                    color = baseColor,
                    style = Stroke(width = 3f)
                )
            }
        }
        
        Text(
            text = "SPECTRUM-FX",
            color = baseColor.copy(alpha = 0.5f),
            fontSize = 6.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomStart).padding(2.dp)
        )
    }
}
