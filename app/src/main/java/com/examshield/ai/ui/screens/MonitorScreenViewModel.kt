package com.examshield.ai.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examshield.ai.domain.model.ClassificationResult
import com.examshield.ai.domain.model.DeviceType
import com.examshield.ai.domain.repository.DetectionService
import com.examshield.ai.domain.ai.AdaptiveLearningEngine
import com.examshield.ai.domain.ai.CentralNeuralLink
import com.examshield.ai.domain.model.SupervisorFeedback
import com.examshield.ai.data.swarm.SwarmMeshService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject
import java.util.concurrent.ConcurrentHashMap
import com.examshield.ai.domain.repository.OrbitalData
import com.examshield.ai.report.MissionReportGenerator
import java.io.File

@HiltViewModel
class MonitorScreenViewModel @Inject constructor(
    private val detectionService: DetectionService,
    private val adaptiveLearningEngine: AdaptiveLearningEngine,
    val performanceAdvisor: com.examshield.ai.domain.ai.AIPerformanceAdvisor,
    val localizationController: com.examshield.ai.session.LocalizationSessionController,
    private val neuralLink: CentralNeuralLink,
    private val swarmMeshService: SwarmMeshService
) : ViewModel() {

    init {
        localizationController.selectHall(com.examshield.ai.localization.HallDefinitions.HallA)
    }

    val aiNeuralState = neuralLink.directives.map { it.aiNeuralState }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CentralNeuralLink.NeuralState.STABLE)

    val swarmNodeCount = swarmMeshService.activeNodes.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val currentHall = localizationController.currentHall
    val estimatedDevicePos = localizationController.estimatedDevicePos
    val localizationConfidence = localizationController.confidence
    val errorRadius = localizationController.errorRadius
    val supervisorPos = localizationController.motionEngine.currentPosition
    val localizationState = localizationController.stateMachine.state
    
    fun getTrilaterationSamples() = localizationController.trilatEngine.getSamples()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _azimuth = MutableStateFlow(0f)
    val azimuth: StateFlow<Float> = _azimuth.asStateFlow()

    private val _pitch = MutableStateFlow(0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    val currentOrbitalData: StateFlow<OrbitalData> = detectionService.currentOrbitalData
    val maxDetectionRange: StateFlow<Float> = detectionService.maxDetectionRange

    fun setMaxDetectionRange(range: Float) {
        detectionService.setMaxDetectionRange(range)
        com.examshield.ai.util.VibrationHelper.vibrateShort()
    }

    private val _ignoredMacs = MutableStateFlow<Set<String>>(emptySet())
    val ignoredMacs: StateFlow<Set<String>> = _ignoredMacs.asStateFlow()

    private val _threatListMap = MutableStateFlow<Map<String, ClassificationResult>>(emptyMap())
    val threatList: StateFlow<List<ClassificationResult>> = _threatListMap.combine(_ignoredMacs) { map, ignored ->
        map.values.filter { it.rawObject.macAddress !in ignored }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _rawDetectionStream = MutableSharedFlow<ClassificationResult>(
        replay = 0, 
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val rawDetectionStream = _rawDetectionStream.asSharedFlow()

    private var scanningJob: kotlinx.coroutines.Job? = null

    fun toggleScan() {
        if (_isScanning.value) {
            stopScanning()
        } else {
            try {
                com.examshield.ai.service.AstraNexusService.start(com.examshield.ai.util.ContextUtils.getAppContext())
                startScanning()
            } catch (e: Exception) {
                android.util.Log.e("MonitorScreenViewModel", "Failed to start service: ${e.message}", e)
                _isScanning.value = false
            }
        }
    }

    private fun startScanning() {
        if (_isScanning.value) return
        
        _isScanning.value = true
        _threatListMap.value = emptyMap()
        scanningJob?.cancel()

        scanningJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            launch {
                try {
                    detectionService.observeOrientation().collect { (ang, pitchVal) ->
                        _azimuth.value = ang
                        _pitch.value = pitchVal
                        if (_isScanning.value) {
                            localizationController.onHeadingUpdate(ang, pitchVal)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MonitorScreenViewModel", "Orientation error: ${e.message}")
                }
            }

            launch {
                try {
                    (detectionService as? com.examshield.ai.data.repository.DetectionServiceImpl)
                        ?.observeSteps()?.collect { angAtStep ->
                            if (_isScanning.value) {
                                 localizationController.onStepDetected(angAtStep)
                            }
                        }
                } catch (e: Exception) {
                    android.util.Log.e("MonitorScreenViewModel", "Step error: ${e.message}")
                }
            }

            launch {
                try {
                    com.examshield.ai.service.AstraNexusService.detectionStream.collect { result ->
                        if (!_isScanning.value) return@collect
                        _rawDetectionStream.tryEmit(result)
                        
                        try {
                            val target = localizationController.targetMacAddress
                            if (target == null || result.rawObject.macAddress == target) {
                                localizationController.onScanSignal(result.rawObject.signalStrengthRssi)
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("MonitorScreenViewModel", "Localization error: ${e.message}")
                        }

                        val currentMap = _threatListMap.value.toMutableMap()
                        if (result.isNexusVerified || result.confidenceScore > 40) {
                            currentMap[result.rawObject.macAddress] = result
                            _threatListMap.value = currentMap
                            
                            // Trigger Haptic Feedback for critical nearby threats
                            if (result.confidenceScore > 85 && result.estimatedDistanceMeters < 1.5f) {
                                com.examshield.ai.util.VibrationHelper.vibrateThreatPulse()
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MonitorScreenViewModel", "Detection stream error: ${e.message}")
                    _isScanning.value = false
                }
            }
        }
    }

    fun markAsFriendly(result: ClassificationResult) {
        viewModelScope.launch {
            adaptiveLearningEngine.applySupervisorLogic(result, isCheating = false, environmentId = "Astra_Nexus_Hall_01")
            ignoreDevice(result.rawObject.macAddress)
        }
    }

    fun markAsCheating(result: ClassificationResult) {
        viewModelScope.launch {
            adaptiveLearningEngine.applySupervisorLogic(result, isCheating = true, environmentId = "Astra_Nexus_Hall_01")
            
            val confirmedResult = result.copy(
                riskLevel = com.examshield.ai.domain.model.RiskLevel.LEVEL_4_CONFIRMED_THREAT,
                confidenceScore = 100,
                feedback = SupervisorFeedback.CHEATING,
                isNexusVerified = true
            )
            val currentMap = _threatListMap.value.toMutableMap()
            currentMap[result.rawObject.macAddress] = confirmedResult
            _threatListMap.value = currentMap
        }
    }

    fun ignoreDevice(mac: String) {
        viewModelScope.launch {
            _ignoredMacs.value = _ignoredMacs.value + mac
            val currentMap = _threatListMap.value.toMutableMap()
            currentMap.remove(mac)
            _threatListMap.value = currentMap
        }
    }

    fun generateMissionReport(context: android.content.Context): File? {
        val generator = MissionReportGenerator(context)
        return generator.generateReport(_threatListMap.value.values.toList(), currentOrbitalData.value)
    }

    private fun stopScanning() {
        _isScanning.value = false
        com.examshield.ai.service.AstraNexusService.stop(com.examshield.ai.util.ContextUtils.getAppContext())
        scanningJob?.cancel()
        scanningJob = null
        detectionService.stop()
        _threatListMap.value = emptyMap()
    }
}
