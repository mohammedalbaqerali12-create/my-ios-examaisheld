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

    // Persistent Target Points (Identified Devices)
    private val _targetPoints = MutableStateFlow<List<Vector2D>>(emptyList())
    val targetPoints = _targetPoints.asStateFlow()

    private var lastVibrateTime = 0L

    // WALK_SAMPLING_MODE State
    private var lastSamplePos: Vector2D? = null
    private var lastHeading: Float = 0f
    private val rssiBuffer = mutableListOf<Int>()
    private val MAX_RSSI_BUFFER = 10

    fun selectHall(hall: HallModel) {
        _currentHall.value = hall
        motionEngine.resetPosition(0f, 0f)
        trilatEngine.clear()
        _estimatedDevicePos.value = null
        _confidence.value = 0
        lastSamplePos = null
        _targetPoints.value = emptyList()
    }

    /**
     * Called on EVERY orientation update (Heading).
     */
    fun onHeadingUpdate(heading: Float) {
        lastHeading = heading
        if (stateMachine.state.value != LocalizationState.WALK_SAMPLING_MODE) {
            stateMachine.transition(LocalizationState.TRACKING_MOTION)
        }
        checkProximityVibration()
    }

    /**
     * Called ONLY when a physical STEP is detected.
     */
    fun onStepDetected(heading: Float) {
        motionEngine.onStepDetected(heading, _currentHall.value)
        val currentPos = motionEngine.currentPosition.value
        
        if (stateMachine.state.value == LocalizationState.WALK_SAMPLING_MODE) {
            val distFromLast = lastSamplePos?.let { 
                Math.sqrt(Math.pow((currentPos.x - it.x).toDouble(), 2.0) + Math.pow((currentPos.y - it.y).toDouble(), 2.0))
            } ?: Double.MAX_VALUE

            val threshold = if (isCloseRefinement()) 0.5 else 1.0
            if (distFromLast >= threshold) {
                if (validateSample(0f)) { // Rotation speed handled by heading update
                    val currentAvgRssi = if (rssiBuffer.isNotEmpty()) rssiBuffer.average().toInt() else -100
                    recordSample(currentAvgRssi)
                    lastSamplePos = currentPos
                }
            }
        }
        checkProximityVibration()
    }

    private fun checkProximityVibration() {
        val supervisor = motionEngine.currentPosition.value
        val device = _estimatedDevicePos.value ?: return
        
        val dist = Math.sqrt(Math.pow((supervisor.x - device.x).toDouble(), 2.0) + Math.pow((supervisor.y - device.y).toDouble(), 2.0))
        
        val now = System.currentTimeMillis()
        if (dist < 2.0 && (now - lastVibrateTime) > (dist * 400 + 100).toLong()) {
            com.examshield.ai.util.VibrationHelper.vibrateShort()
            lastVibrateTime = now
        }
    }

    private fun validateSample(rotationSpeed: Float): Boolean {
        // Reject if rotating quickly (> 30 deg/step)
        if (rotationSpeed > 30f) return false
        
        // Reject if RSSI variance is high
        if (rssiBuffer.size >= 3) {
            val avg = rssiBuffer.average()
            val variance = rssiBuffer.map { Math.pow(it - avg, 2.0) }.average()
            if (variance > 25.0) return false // Threshold for high noise
        }
        
        return true
    }

    private fun isCloseRefinement(): Boolean {
        val estimatedDist = _estimatedDevicePos.value?.let { est ->
            val curr = motionEngine.currentPosition.value
            Math.sqrt(Math.pow((est.x - curr.x).toDouble(), 2.0) + Math.pow((est.y - curr.y).toDouble(), 2.0))
        } ?: 10.0
        return estimatedDist < 2.0
    }

    /**
     * Called when an RSSI measurement is perceived for the target device.
     */
    fun onScanSignal(rssi: Int) {
        rssiBuffer.add(rssi)
        if (rssiBuffer.size > MAX_RSSI_BUFFER) rssiBuffer.removeAt(0)

        val pos = motionEngine.currentPosition.value
        gradientEngine.addSample(pos.x, pos.y, rssi)
        
        // Auto-fuse or update state
        updateGlobalPosition(rssi)
    }

    fun startWalkSampling() {
        stateMachine.transition(LocalizationState.WALK_SAMPLING_MODE)
        lastSamplePos = null
    }

    /**
     * Record a position sample for trilateration.
     */
    fun recordSample(rssi: Int) {
        val pos = motionEngine.currentPosition.value
        val dist = distanceEngine.estimateDistance(rssi, n = envModel.getCurrentN())
        
        trilatEngine.addSample(pos.x, pos.y, dist)
        
        // PART 4: Live Error Update
        updateGlobalPosition(rssi)
    }

    private fun updateGlobalPosition(rssi: Int) {
        val supervisorPos = motionEngine.currentPosition.value
        val bearing = gradientEngine.estimateBearing()
        val dist = distanceEngine.estimateDistance(rssi, n = envModel.getCurrentN())
        
        val trilatFix = trilatEngine.computeDevicePosition()
        val residual = trilatFix?.let { trilatEngine.computeRefinementResidual(it) } ?: 10f

        // PART 7: Fusion Integration Logic
        val isWalkMode = stateMachine.state.value == LocalizationState.WALK_SAMPLING_MODE
        
        val finalPos = fusionEngine.fuseWithWalkLogic(
            supervisorPos,
            bearing,
            dist,
            trilatFix,
            residual,
            isWalkMode
        )

        _estimatedDevicePos.value = finalPos
        
        // CONFIDENCE EVOLUTION
        val sampleCount = trilatEngine.getSamples().size
        val newConfidence = fusionEngine.computeConfidenceScore(
            if (trilatFix != null) (3 + sampleCount) else 1, 
            5.0f,
            residual
        )
        _confidence.value = newConfidence
        
        // PERSIST TARGET POINT
        if (newConfidence > 90 && finalPos != null) {
            val currentPoints = _targetPoints.value.toMutableList()
            val isDuplicate = currentPoints.any { 
                Math.sqrt(Math.pow((it.x - finalPos.x).toDouble(), 2.0) + Math.pow((it.y - finalPos.y).toDouble(), 2.0)) < 1.0 
            }
            if (!isDuplicate) {
                currentPoints.add(finalPos)
                _targetPoints.value = currentPoints
                com.examshield.ai.util.VibrationHelper.vibrateSuccess()
                stateMachine.transition(LocalizationState.LOCALIZED)
            }
        }
        
        _errorRadius.value = envModel.estimateErrorRadius(5.0f, residual)
    }
}
