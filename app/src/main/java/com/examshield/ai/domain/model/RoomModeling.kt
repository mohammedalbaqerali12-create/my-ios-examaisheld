package com.examshield.ai.domain.model

data class RoomProfile(
    val id: String = "DEFAULT_ROOM",
    val name: String = "Exam Hall",
    val lengthMeters: Float = 10f,
    val widthMeters: Float = 10f,
    val heightMeters: Float = 3f,
    val shape: RoomShape = RoomShape.RECTANGLE,
    val calibrationCoefficient: Float = 2.0f // n path loss exponent
)

enum class RoomShape {
    RECTANGLE, SQUARE, POLYGON
}

data class SeatPosition(
    val id: String,
    val row: Int,
    val seatNumber: Int,
    val x: Float,
    val y: Float
)

data class SeatGridConfig(
    val rows: Int = 5,
    val seatsPerRow: Int = 6,
    val seatSpacingMeters: Float = 1.2f,
    val aisleSpacingMeters: Float = 2.0f
)
