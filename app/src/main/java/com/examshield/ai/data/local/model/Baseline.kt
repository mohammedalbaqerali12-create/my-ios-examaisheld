package com.examshield.ai.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "environment_baseline")
data class Baseline(
    @PrimaryKey
    val macAddress: String,
    val firstSeen: Long,
    var lastSeen: Long,
    var observationCount: Int,
    val avgRssi: Double,
    val isConsideredSafe: Boolean = false
)
