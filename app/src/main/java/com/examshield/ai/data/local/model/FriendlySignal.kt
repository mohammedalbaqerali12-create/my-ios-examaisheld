package com.examshield.ai.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friendly_signals")
data class FriendlySignal(
    @PrimaryKey
    val signalHash: String, // SHA-256 of anonymized features
    val namePattern: String?,
    val macOui: String?,
    val environmentId: String,
    val firstMarkedAt: Long = System.currentTimeMillis(),
    val observationCount: Int = 1
)
