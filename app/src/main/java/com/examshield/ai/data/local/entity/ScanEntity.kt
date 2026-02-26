package com.examshield.ai.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val macAddress: String,
    val deviceName: String?,
    val rssi: Int,
    val distanceZone: String,
    val classificationConfidence: Int,
    val isConfirmedBySupervisor: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
