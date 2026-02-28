package com.examshield.ai.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "room_profiles")
data class RoomProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lengthMeters: Float,
    val widthMeters: Float,
    val heightMeters: Float,
    val shape: String,
    val calibrationCoefficient: Float,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "seat_grid_configs")
data class SeatGridConfigEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val rows: Int,
    val seatsPerRow: Int,
    val seatSpacingMeters: Float,
    val aisleSpacingMeters: Float
)

@Entity(tableName = "calibration_profiles")
data class CalibrationProfileEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val rssiBaseline: Int,
    val attenuationCoefficient: Float,
    val environmentFingerprint: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "active_tasks_history")
data class ActiveTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val isActive: Boolean,
    val intensity: Int,
    val parameter: Float,
    val timestamp: Long = System.currentTimeMillis()
)
