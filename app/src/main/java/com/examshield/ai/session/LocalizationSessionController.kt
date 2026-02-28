package com.examshield.ai.session

import com.examshield.ai.localization.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalizationSessionController @Inject constructor() {
    val motionEngine = SupervisorMotionEngine()
    val distanceEngine = DistanceEstimationEngine()
    val gradientEngine = GradientDirectionEngine()
    val trilatEngine = TrilaterationEngine()
    val fusionEngine = FusionEngine()
    val envModel = EnvironmentAdaptiveModel()
    val stateMachine = LocalizationStateMachine()

    private val _currentHall = MutableStateFlow(HallDefinitions.HallA)
    val currentHall = _currentHall.asStateFlow()

    private val _estimatedDevicePos = MutableStateFlow<Vector2D?>(null)
    val estimatedDevicePos = _estimatedDevicePos.asStateFlow()
    
    private val _confidence = MutableStateFlow(0)
    val confidence = _confidence.asStateFlow()

    private val _errorRadius = MutableStateFlow(0f)
    val errorRadius = _errorRadius.asStateFlow()

    fun selectHall(hall: HallModel) {
        _currentHall.value = hall
        motionEngine.resetPosition(0f, 0f)
        trilatEngine.clear()
        _estimatedDevicePos.value = null
        _confidence.value = 0
    }

    /**
     * Called when a step is detected and heading is known.
     */
    fun onMotionUpdate(heading: Float) {
        motionEngine.onStepDetected(heading, _currentHall.value)
        stateMachine.transition(LocalizationState.TRACKING_MOTION)
    }

    /**
     * Called when an RSSI measurement is perceived for the target device.
     */
    fun onScanSignal(rssi: Int) {
        val pos = motionEngine.currentPosition.value
        val dist = distanceEngine.estimateDistance(rssi, n = envModel.getCurrentN())
        
        gradientEngine.addSample(pos.x, pos.y, rssi)
        
        // Auto-fuse or update state
        updateGlobalPosition(rssi)
    }

    /**
     * Record a manual position sample for trilateration.
     */
    fun recordSample(rssi: Int) {
        val pos = motionEngine.currentPosition.value
        val dist = distanceEngine.estimateDistance(rssi, n = envModel.getCurrentN())
        
        trilatEngine.addSample(pos.x, pos.y, dist)
        stateMachine.transition(LocalizationState.COLLECTING_SAMPLES)
        
        updateGlobalPosition(rssi)
    }

    private fun updateGlobalPosition(rssi: Int) {
        val supervisorPos = motionEngine.currentPosition.value
        val bearing = gradientEngine.estimateBearing()
        val dist = distanceEngine.estimateDistance(rssi, n = envModel.getCurrentN())
        
        val trilatFix = trilatEngine.computeDevicePosition()
        val residual = trilatFix?.let { trilatEngine.computeRefinementResidual(it) } ?: 10f

        val finalPos = fusionEngine.fuseEstimates(
            supervisorPos,
            bearing,
            dist,
            trilatFix,
            residual,
            5.0f // Placeholder variance
        )

        _estimatedDevicePos.value = finalPos
        _confidence.value = fusionEngine.computeConfidenceScore(
            if (trilatFix != null) 3 else 1, 
            5.0f,
            residual
        )
        
        _errorRadius.value = envModel.estimateErrorRadius(5.0f, residual)
        
        if (_confidence.value > 70) {
            stateMachine.transition(LocalizationState.LOCALIZED)
        }
    }
}
