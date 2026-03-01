package com.examshield.ai.domain.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ASTRA NEXUS: CENTRAL NEURAL LINK (ALIEN DNA CORE)
 * 
 * A reactive bridge allowing the AI to autonomously mutate system parameters.
 */
@Singleton
class CentralNeuralLink @Inject constructor() {

    // SYSTEM DIRECTIVES (DNA)
    data class SystemDirectives(
        val kalmanProcessNoise: Double = 0.012,
        val kalmanMeasurementNoise: Double = 0.45,
        val hapticIntensityMultiplier: Float = 1.0f,
        val scanningSpeedMultiplier: Float = 1.0f,
        val stealthPeekEnabled: Boolean = false,
        val aiNeuralState: NeuralState = NeuralState.STABLE
    )

    enum class NeuralState {
        STABLE,     // Default operation
        EVOLVING,   // AI is recalculating weights
        CRITICAL,   // AI has locked onto a persistent threat
        STEALTH     // AI is minimizing signal footprint
    }

    private val _directives = MutableStateFlow(SystemDirectives())
    val directives = _directives.asStateFlow()

    /**
     * Mutate system parameters based on AI intelligence.
     */
    fun mutate(mutation: (SystemDirectives) -> SystemDirectives) {
        _directives.value = mutation(_directives.value)
    }

    /**
     * Set the neural state for UI feedback.
     */
    fun setNeuralState(state: NeuralState) {
        _directives.value = _directives.value.copy(aiNeuralState = state)
    }
}
