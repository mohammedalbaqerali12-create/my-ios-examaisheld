package com.examshield.ai.domain.model

data class FocusTask(
    val type: TaskType,
    val isActive: Boolean = false,
    val intensity: Int = 1, // 1-3
    val parameter: Float = 0f // radius, row index, sector index
)

enum class TaskType {
    NONE,
    FOCUS_RADIUS,
    ROW_FOCUS,
    SECTOR_FOCUS,
    PRECISION_LOCK
}

data class TaskStatus(
    val activeTaskName: String,
    val improvementEstimatePercentage: Int,
    val intensityLabel: String
)
