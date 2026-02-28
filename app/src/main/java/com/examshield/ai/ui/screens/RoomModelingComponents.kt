package com.examshield.ai.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examshield.ai.domain.model.*
import com.examshield.ai.ui.theme.*

@Composable
fun RoomLayoutView(
    roomProfile: RoomProfile,
    seats: List<SeatPosition>,
    activeThreats: List<ClassificationResult>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(roomProfile.widthMeters / roomProfile.lengthMeters)
            .background(DarkMatterSurface, RoundedCornerShape(8.dp))
            .border(1.dp, Color.Cyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val scaleX = size.width / roomProfile.widthMeters
            val scaleY = size.height / roomProfile.lengthMeters

            // Draw Room Border
            drawRect(
                color = Color.Cyan.copy(alpha = 0.2f),
                style = Stroke(width = 2.dp.toPx())
            )

            // Draw Seats
            seats.forEach { seat ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.1f),
                    radius = 4.dp.toPx(),
                    center = Offset(seat.x * scaleX, seat.y * scaleY)
                )
            }

            // Draw Heatmap / Signals
            activeThreats.forEach { threat ->
                val dist = threat.estimatedDistanceMeters
                val alpha = (1.0f - (dist / roomProfile.lengthMeters)).coerceIn(0.1f, 1.0f)
                
                // Signal Source Center Approximation (Center of room as placeholder)
                val centerX = size.width / 2
                val centerY = size.height / 2

                drawCircle(
                    color = if (threat.riskLevel == RiskLevel.LEVEL_4_CONFIRMED_THREAT) ThreatRed.copy(alpha = alpha * 0.3f) else Color.Blue.copy(alpha = alpha * 0.2f),
                    radius = (40.dp.toPx() * (1.1f / (dist + 1))).coerceIn(20f, 150f),
                    center = Offset(centerX, centerY)
                )

                // High Precision: Seat Probability Snapping
                // (Visualize the most likely seat zones)
                if (threat.confidenceScore > 70) {
                     seats.forEach { seat ->
                         val dx = (seat.x - (roomProfile.widthMeters/2)).toDouble()
                         val dy = (seat.y - (roomProfile.lengthMeters/2)).toDouble()
                         val seatDist = kotlin.math.sqrt(dx * dx + dy * dy)
                         val error = kotlin.math.abs(seatDist - dist.toDouble())
                         if (error < 1.0) { // Within 1m of predicted distance
                             drawCircle(
                                 color = Color.Yellow.copy(alpha = 0.4f),
                                 radius = 6.dp.toPx(),
                                 center = Offset(seat.x * scaleX, seat.y * scaleY)
                             )
                         }
                     }
                }
            }
        }
        // Riverside: Probabilistic Seat Snapping Visualization enabled.
        Text(
            text = "${roomProfile.widthMeters}m x ${roomProfile.lengthMeters}m",
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun TaskSelectionPanel(
    onTaskSelected: (FocusTask) -> Unit,
    activeTask: FocusTask
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkMatterSurface)
            .padding(16.dp)
    ) {
        Text(
            "تفعيل وضع التركيز (Focus Mode)",
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TaskButton(
                name = "قطر محدد",
                icon = Icons.Default.Info,
                isSelected = activeTask.type == TaskType.FOCUS_RADIUS,
                onClick = { onTaskSelected(FocusTask(TaskType.FOCUS_RADIUS, true, parameter = 3f)) }
            )
            TaskButton(
                name = "ركن معين",
                icon = Icons.Default.Info,
                isSelected = activeTask.type == TaskType.SECTOR_FOCUS,
                onClick = { onTaskSelected(FocusTask(TaskType.SECTOR_FOCUS, true, parameter = 1f)) }
            )
            TaskButton(
                name = "مسح صفوف",
                icon = Icons.Default.Lock,
                isSelected = activeTask.type == TaskType.ROW_FOCUS,
                onClick = { onTaskSelected(FocusTask(TaskType.ROW_FOCUS, true, parameter = 2f)) }
            )
            TaskButton(
                name = "قفل دقيق",
                icon = Icons.Default.Lock,
                isSelected = activeTask.type == TaskType.PRECISION_LOCK,
                onClick = { onTaskSelected(FocusTask(TaskType.PRECISION_LOCK, true)) }
            )
        }
    }
}

@Composable
fun RowScope.TaskButton(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color.Cyan.copy(alpha = 0.2f) else Color.Transparent
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color.Cyan else Color.Gray.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (isSelected) Color.Cyan else Color.Gray)
            Text(name, fontSize = 10.sp, color = if (isSelected) Color.White else Color.Gray)
        }
    }
}
