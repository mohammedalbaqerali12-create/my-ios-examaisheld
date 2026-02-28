package com.examshield.ai.localization

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LocalizationState {
    INITIALIZING,
    TRACKING_MOTION,
    WALK_SAMPLING_MODE, // NEW: Rapid triangulation by walking
    COLLECTING_SAMPLES,
    FUSING_FIX,
    LOCALIZED,
    LOST
}

class LocalizationStateMachine {
    private val _state = MutableStateFlow(LocalizationState.INITIALIZING)
    val state: StateFlow<LocalizationState> = _state.asStateFlow()

    fun transition(next: LocalizationState) {
        // Simple state machine logic
        _state.value = next
    }
}
