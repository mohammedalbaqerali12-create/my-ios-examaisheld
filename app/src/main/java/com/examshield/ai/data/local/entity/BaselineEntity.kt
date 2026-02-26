package com.examshield.ai.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calibration_baseline")
data class BaselineEntity(
    @PrimaryKey val macAddress: String,
    val initialRssi: Int,
    val timestamp: Long = System.currentTimeMillis()
)
