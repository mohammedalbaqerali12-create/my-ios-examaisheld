package com.examshield.ai.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "confirmed_cheating_signals")
data class ConfirmedCheatingSignal(
    @PrimaryKey
    val signalHash: String, // SHA-256 of anonymized features
    val deviceType: String,
    val lastSeenRssi: Int,
    val environmentId: String,
    val confirmedAt: Long = System.currentTimeMillis(),
    val hitCount: Int = 1
)
