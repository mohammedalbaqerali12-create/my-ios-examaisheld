package com.examshield.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examshield.ai.domain.ai.AIPerformanceAdvisor

data class ChatMessage(val content: String, val isAssistant: Boolean)

@Composable
fun AIPerformanceAdvisorScreen(advisor: AIPerformanceAdvisor, onBack: () -> Unit) {
    val healthState by advisor.healthState.collectAsState()
    var chatQuery by remember { mutableStateOf("") }
    val chatHistory = remember { mutableStateListOf<ChatMessage>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .systemBarsPadding()
    ) {
        // --- HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "AI PERFORMANCE ADVISOR",
                color = Color.Cyan,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                Text("BACK")
            }
        }

        // --- HEALTH DASHBOARD ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Cyan.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("SYSTEM HEALTH STATUS", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                HealthRow("REFRESH RATE", "${healthState.refreshRate}Hz", if (healthState.refreshRate > 2) Color.Green else Color.Red)
                HealthRow("CALLBACK FREQUENCY", "${healthState.callbackFrequency}/s", if (healthState.callbackFrequency > 2) Color.Green else Color.Red)
                HealthRow("BLUETOOTH", if (healthState.isBluetoothEnabled) "ENABLED" else "DISABLED", if (healthState.isBluetoothEnabled) Color.Green else Color.Red)
                HealthRow("RSSI VARIANCE", "%.2f".format(healthState.rssiStability), if (healthState.rssiStability < 10.0) Color.Green else Color.Yellow)
            }
        }

        // --- CHAT INTERFACE ---
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(chatHistory) { msg ->
                ChatBubble(msg)
            }
        }

        // --- INPUT AREA ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = chatQuery,
                onValueChange = { chatQuery = it },
                placeholder = { Text("Ask about system performance...", color = Color.Gray) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color.Cyan,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                if (chatQuery.isNotEmpty()) {
                    chatHistory.add(ChatMessage(chatQuery, false))
                    val response = advisor.getDiagnosticChatResponse(chatQuery)
                    chatHistory.add(ChatMessage(response, true))
                    chatQuery = ""
                }
            }) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Cyan)
            }
        }
    }
}

@Composable
fun HealthRow(label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White, fontSize = 14.sp)
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ChatBubble(msg: ChatMessage) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (msg.isAssistant) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = if (msg.isAssistant) Color.Cyan.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.2f)),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                msg.content, 
                color = Color.White, 
                fontSize = 14.sp, 
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
