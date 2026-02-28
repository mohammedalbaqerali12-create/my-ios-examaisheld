package com.examshield.ai.localization

data class HallModel(
    val id: String,
    val name: String,
    val width: Float, // in meters
    val height: Float, // in meters
    val gridResolution: Float, // size of cell in meters
    val seats: List<SeatPosition> = emptyList()
)

data class SeatPosition(
    val x: Float,
    val y: Float,
    val id: String
)

object HallDefinitions {
    val HallA = HallModel(
        id = "hall_a",
        name = "القاعة أ",
        width = 10f,
        height = 10f,
        gridResolution = 0.5f
    )

    val HallB = HallModel(
        id = "hall_b",
        name = "القاعة ب",
        width = 20f,
        height = 20f,
        gridResolution = 0.5f
    )
}
