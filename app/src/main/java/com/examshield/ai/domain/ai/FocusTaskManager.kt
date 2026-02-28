package com.examshield.ai.domain.ai

import com.examshield.ai.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class FocusTaskManager @Inject constructor() {
    private val _activeTask = MutableStateFlow(FocusTask(TaskType.NONE))
    val activeTask = _activeTask.asStateFlow()

    fun setTask(task: FocusTask) {
        _activeTask.value = task
    }

    fun clearTask() {
        _activeTask.value = FocusTask(TaskType.NONE)
    }

    fun isSignalInFocus(distanceMeters: Double, x: Float, y: Float): Boolean {
        val task = _activeTask.value
        if (!task.isActive) return true

        return when (task.type) {
            TaskType.FOCUS_RADIUS -> distanceMeters <= task.parameter
            TaskType.ROW_FOCUS -> {
                // Approximate row by Y coordinate
                val rowIndex = (y / 1.2f).toInt() // assuming 1.2m per row
                rowIndex == task.parameter.toInt()
            }
            TaskType.SECTOR_FOCUS -> {
                // Define 8 sectors based on angle/position
                // (Simplified for now)
                true 
            }
            TaskType.PRECISION_LOCK -> true // Precision lock focuses on quality, not excluding others
            else -> true
        }
    }

    fun getImprovementEstimate(): TaskStatus {
        val task = _activeTask.value
        return when (task.type) {
            TaskType.FOCUS_RADIUS -> TaskStatus("Radius Shield", 35, "High Efficiency")
            TaskType.ROW_FOCUS -> TaskStatus("Row Scanning", 45, "Surgical")
            TaskType.SECTOR_FOCUS -> TaskStatus("Sector Sweep", 50, "Maximum Rejection")
            TaskType.PRECISION_LOCK -> TaskStatus("Precision Lock", 80, "Ultra-Intensity")
            else -> TaskStatus("Standard Flow", 0, "Nominal")
        }
    }
}
