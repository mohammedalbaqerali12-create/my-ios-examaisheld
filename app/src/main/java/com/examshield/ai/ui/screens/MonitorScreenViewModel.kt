package com.examshield.ai.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examshield.ai.domain.model.ClassificationResult
import com.examshield.ai.domain.model.DeviceType
import com.examshield.ai.domain.repository.DetectionService
import com.examshield.ai.domain.ai.AdaptiveLearningEngine
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
    val localizationController: com.examshield.ai.session.LocalizationSessionController
) : ViewModel() {

    init {
        // Initialize localization session with a default hall
        localizationController.selectHall(com.examshield.ai.localization.HallDefinitions.HallA)
    }

    // Localization State exposure
    val currentHall = localizationController.currentHall
    val estimatedDevicePos = localizationController.estimatedDevicePos
    val localizationConfidence = localizationController.confidence
    val errorRadius = localizationController.errorRadius
    val supervisorPos = localizationController.motionEngine.currentPosition
    val localizationState = localizationController.stateMachine.state
    
    fun getTrilaterationSamples() = localizationController.trilatEngine.getSamples()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Real-time azimuth and pitch for radar stabilization
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

    // MAC addresses that the user explicitly wants to hide
    private val _ignoredMacs = MutableStateFlow<Set<String>>(emptySet())
    val ignoredMacs: StateFlow<Set<String>> = _ignoredMacs.asStateFlow()

    private val _threatListMap = MutableStateFlow<Map<String, ClassificationResult>>(emptyMap())
    val threatList: StateFlow<List<ClassificationResult>> = _threatListMap.combine(_ignoredMacs) { map, ignored ->
        map.values.filter { it.rawObject.macAddress !in ignored }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // High-frequency stream for the Radar screen to track orientation in real-time
    private val _rawDetectionStream = MutableSharedFlow<ClassificationResult>(replay = 0, extraBufferCapacity = 64)
    val rawDetectionStream = _rawDetectionStream.asSharedFlow()

    private var scanningJob: kotlinx.coroutines.Job? = null

    fun toggleScan() {
        if (_isScanning.value) {
            stopScanning()
        } else {
            // Start Foreground Service
            com.examshield.ai.service.AstraNexusService.start(com.examshield.ai.util.ContextUtils.getAppContext())
            startScanning()
        }
    }

    private fun startScanning() {
        if (_isScanning.value) return // Already scanning
        
        _isScanning.value = true
        _threatListMap.value = emptyMap() // clear old visual results
        
        // Cancel any lingering job just in case
        scanningJob?.cancel()

        // 0. Orientation Tracker
        viewModelScope.launch {
            detectionService.observeOrientation().collect { (ang, pitchVal) ->
                _azimuth.value = ang
                _pitch.value = pitchVal
                if (_isScanning.value) {
                    localizationController.onHeadingUpdate(ang, pitchVal)
                }
            }
        }

        // --- KINETIC SAMPLING ENGINE ---
        // Exclusively rely on phone movements & step detection instead of GPS.
        viewModelScope.launch {
            (detectionService as com.examshield.ai.data.repository.DetectionServiceImpl)
                .observeSteps().collect { angAtStep ->
                    if (_isScanning.value) {
                         localizationController.onStepDetected(angAtStep)
                    }
                }
        }

        // 1. Reactive Collector from Service (Zero-Latency)
        scanningJob = viewModelScope.launch {
            com.examshield.ai.service.AstraNexusService.detectionStream.collect { result ->
                // Emit to high-frequency stream for the SignalFinderScreen
                _rawDetectionStream.emit(result)
                
                // --- HYBRID ENGINE FEED ---
                localizationController.onScanSignal(result.rawObject.signalStrengthRssi)

                // Update the main threat list map reactively
                val currentMap = _threatListMap.value.toMutableMap()
                
                // Astra Nexus: Only show verified or newly discovered targets
                if (result.isNexusVerified || result.confidenceScore > 40) {
                    currentMap[result.rawObject.macAddress] = result
                    _threatListMap.value = currentMap
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
            // CONFIRMED CHEATING: Force Level 4 and Lock
            adaptiveLearningEngine.applySupervisorLogic(result, isCheating = true, environmentId = "Astra_Nexus_Hall_01")
            
            // Immediately update the local map with the "Confirmed" state
            val confirmedResult = result.copy(
                riskLevel = com.examshield.ai.domain.model.RiskLevel.LEVEL_4_CONFIRMED_THREAT,
                confidenceScore = 100,
                feedback = com.examshield.ai.domain.model.SupervisorFeedback.CHEATING,
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
        // Stop Foreground Service
        com.examshield.ai.service.AstraNexusService.stop(com.examshield.ai.util.ContextUtils.getAppContext())
        scanningJob?.cancel()
        scanningJob = null
        detectionService.stop()
        _threatListMap.value = emptyMap()
    }
}
