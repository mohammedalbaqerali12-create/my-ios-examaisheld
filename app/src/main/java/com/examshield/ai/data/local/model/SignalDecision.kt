package com.examshield.ai.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_decision_history")
data class SignalDecision(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val signalHash: String,
    val decision: String, // "FRIENDLY" or "CHEATING"
    val timestamp: Long = System.currentTimeMillis(),
    val environmentId: String,
    val initialConfidence: Int,
    val finalRiskLevel: Int
)
