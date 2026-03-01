package com.examshield.ai.data.repository

import com.examshield.ai.domain.ai.DeviceClassifier
import com.examshield.ai.domain.model.ClassificationResult
import com.examshield.ai.domain.repository.DetectionService
import com.examshield.ai.domain.repository.Scanner
import com.examshield.ai.domain.repository.OrbitalData
import com.examshield.ai.domain.repository.OrbitalUplink
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import com.examshield.ai.util.HapticSonarManager
import com.examshield.ai.data.swarm.SwarmMeshService
import com.examshield.ai.data.swarm.SwarmMessage
import com.examshield.ai.domain.ai.CentralNeuralLink
import com.examshield.ai.domain.ai.SensorFusionEngine
import com.examshield.ai.domain.ai.AdaptiveLearningEngine

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DetectionServiceImpl(
    private val bleScanner: Scanner,
    private val classicBluetoothScanner: Scanner,
    private val wifiScanner: Scanner,
    private val wifiDirectScanner: Scanner,
    private val magneticFieldScanner: Scanner,
    private val orientationScanner: com.examshield.ai.data.scanner.OrientationScannerImpl,
    private val classifier: DeviceClassifier,
    private val adaptiveLearningEngine: AdaptiveLearningEngine,
    private val orbitalUplink: OrbitalUplink,
    private val hapticSonarManager: HapticSonarManager,
    private val swarmMeshService: SwarmMeshService,
    private val neuralLink: CentralNeuralLink
) : DetectionService {

    private val sensorFusionEngine = SensorFusionEngine(neuralLink)
    private val callbackCounts = ConcurrentHashMap<String, Int>()
    private val swarmIdentities = ConcurrentHashMap<String, SwarmMessage.ThreatIntel>()
    private val lastLogTime = ConcurrentHashMap<String, Long>()
    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Default + kotlinx.coroutines.SupervisorJob())

    override val currentOrbitalData: StateFlow<OrbitalData>
        get() = _currentOrbitalData.asStateFlow()
    private val _currentOrbitalData = MutableStateFlow(OrbitalData())

    override val maxDetectionRange: StateFlow<Float>
        get() = _maxDetectionRange.asStateFlow()
    private val _maxDetectionRange = MutableStateFlow(5.0f)

    override fun setMaxDetectionRange(range: Float) {
        _maxDetectionRange.value = range
    }

    override fun observeThreats(): Flow<ClassificationResult> {
        var lastMagneticAnomalyTime = 0L
        
        // Start Swarm & Orbital Background Tasks
        swarmMeshService.startSwarm()
        scope.launch {
            swarmMeshService.swarmIntelStream.collect { message ->
                if (message is SwarmMessage.ThreatIntel) {
                    swarmIdentities[message.macAddress] = message
                }
            }
        }

        scope.launch {
            orbitalUplink.streamOrbitalData().collect { data ->
                _currentOrbitalData.value = data
            }
        }

        // Parallel Scanner Merge (High Speed)
        val mergedScanners = merge(
            bleScanner.startScanning(),
            classicBluetoothScanner.startScanning(),
            wifiScanner.startScanning(),
            wifiDirectScanner.startScanning(),
            magneticFieldScanner.startScanning()
        )

        return mergedScanners
            .buffer(capacity = 1024, onBufferOverflow = BufferOverflow.DROP_OLDEST) // Increased capacity for Overdrive
            .mapNotNull { detectedObj ->
                val directives = neuralLink.directives.value
                
                // Adaptive Purge (AI Directed)
                if (Math.random() < 0.0005) sensorFusionEngine.purgeAll()

                if (detectedObj.macAddress == "MAGNETIC_FIELD_ANOMALY") {
                    lastMagneticAnomalyTime = System.currentTimeMillis()
                    return@mapNotNull null
                }

                // High-Speed Signal Fusion
                val smoothedRssi = sensorFusionEngine.process(
                    detectedObj.macAddress, 
                    detectedObj.signalStrengthRssi,
                    motionIntensity = (abs(orientationScanner.currentAzimuth - lastAzimuth) / 60.0).coerceIn(0.0, 1.0)
                )
                lastAzimuth = orientationScanner.currentAzimuth
                
                val optimizedObj = detectedObj.copy(signalStrengthRssi = smoothedRssi)
                val baseClassification = classifier.classify(optimizedObj)
                
                val now = System.currentTimeMillis()
                val timeSinceMagnet = now - lastMagneticAnomalyTime
                
                var finalResult = baseClassification
                
                // Swarm & Magnet Fusion
                val allyIntel = swarmIdentities[detectedObj.macAddress]
                val stability = sensorFusionEngine.getSignalIntegrity(detectedObj.macAddress)
                
                var confidenceMod = 0
                if (timeSinceMagnet < 3000 && baseClassification.estimatedDistanceMeters < 2.0) confidenceMod += 40
                if (stability > 0.85) confidenceMod += 15
                if (allyIntel != null) confidenceMod += 25
                
                val finalConfidence = (baseClassification.confidenceScore + confidenceMod).coerceIn(0, 100)

                finalResult = finalResult.copy(
                    confidenceScore = finalConfidence,
                    discoveryReason = "${finalResult.discoveryReason} [NEXUS_OVERDRIVE]${if (allyIntel != null) " [SWARM:${allyIntel.sourceNode}]" else ""}".trim()
                )

                // Update Sonar & Swarm
                if (finalConfidence >= 40) {
                    hapticSonarManager.updateSonarTarget(finalResult.distanceZone, finalConfidence, finalResult.isNexusVerified)
                    if (finalConfidence >= 80) swarmMeshService.broadcastThreat(finalResult)
                }

                // Range Peeking (AI Controlled)
                val rangeLimit = if (directives.stealthPeekEnabled) _maxDetectionRange.value + 2.5f else _maxDetectionRange.value
                if (finalResult.estimatedDistanceMeters > rangeLimit) return@mapNotNull null

                finalResult
            }
            .filter { result ->
                // Fast path filter for tactical devices
                val type = result.deviceType
                type == com.examshield.ai.domain.model.DeviceType.SMARTPHONE ||
                type == com.examshield.ai.domain.model.DeviceType.SMARTWATCH ||
                type == com.examshield.ai.domain.model.DeviceType.WIRELESS_EARBUD ||
                type == com.examshield.ai.domain.model.DeviceType.NANO_EARPIECE
            }
            .flowOn(Dispatchers.Default)
    }

    private var lastAzimuth = 0f

    override fun observeOrientation(): Flow<Pair<Float, Float>> {
        return orientationScanner.observeOrientation()
    }

    fun observeSteps(): Flow<Float> {
        return orientationScanner.observeSteps()
    }

    override fun stop() {
        bleScanner.stopScanning()
        classicBluetoothScanner.stopScanning()
        wifiScanner.stopScanning()
        wifiDirectScanner.stopScanning()
        magneticFieldScanner.stopScanning()
        hapticSonarManager.stopSonar()
        swarmMeshService.stopSwarm()
    }
}
