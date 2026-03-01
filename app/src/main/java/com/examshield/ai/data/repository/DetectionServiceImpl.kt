package com.examshield.ai.data.repository

import com.examshield.ai.domain.ai.DeviceClassifier
import com.examshield.ai.domain.model.ClassificationResult
import com.examshield.ai.domain.repository.DetectionService
import com.examshield.ai.domain.repository.Scanner
import com.examshield.ai.domain.repository.OrbitalData
import com.examshield.ai.domain.repository.OrbitalUplink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import com.examshield.ai.util.HapticSonarManager
import com.examshield.ai.data.swarm.SwarmMeshService
import com.examshield.ai.data.swarm.SwarmIntel
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
    private val swarmIdentities = ConcurrentHashMap<String, SwarmIntel>()
    private val lastLogTime = ConcurrentHashMap<String, Long>()
    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

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
        
        scope.launch {
            try {
                adaptiveLearningEngine.checkForGlobalThreatUpdates()
            } catch (e: Exception) {
                android.util.Log.e("ASTRA_NEXUS", "Intel Sync Failed: ${e.message}")
            }
        }

        scope.launch {
            orbitalUplink.streamOrbitalData().collect { data ->
                _currentOrbitalData.value = data
            }
        }

        swarmMeshService.startSwarm()
        scope.launch {
            swarmMeshService.swarmIntelStream.collect { intel ->
                swarmIdentities[intel.macAddress] = intel
            }
        }

        val mergedScanners = merge(
            bleScanner.startScanning(),
            classicBluetoothScanner.startScanning(),
            wifiScanner.startScanning(),
            wifiDirectScanner.startScanning(),
            magneticFieldScanner.startScanning()
        )

        return mergedScanners
            .buffer(capacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            .mapNotNull { detectedObj ->
                if (Math.random() < 0.001) {
                    sensorFusionEngine.purgeAll()
                }

                if (detectedObj.macAddress == "MAGNETIC_FIELD_ANOMALY") {
                    lastMagneticAnomalyTime = System.currentTimeMillis()
                    return@mapNotNull null
                }

                val smoothedRssi = sensorFusionEngine.process(
                    detectedObj.macAddress, 
                    detectedObj.signalStrengthRssi,
                    motionIntensity = (abs(orientationScanner.currentAzimuth - lastAzimuth) / 45.0).coerceIn(0.0, 1.0)
                )
                lastAzimuth = orientationScanner.currentAzimuth
                
                val now = System.currentTimeMillis()
                val lastLog = lastLogTime[detectedObj.macAddress] ?: 0L
                if (now - lastLog > 500) {
                    android.util.Log.d("ASTRA_NEXUS", "MAC: ${detectedObj.macAddress} | RSSI: ${detectedObj.signalStrengthRssi} | FUSED: $smoothedRssi")
                    lastLogTime[detectedObj.macAddress] = now
                }

                val optimizedObj = detectedObj.copy(signalStrengthRssi = smoothedRssi)
                val baseClassification = classifier.classify(optimizedObj)
                
                val timeSinceMagnet = now - lastMagneticAnomalyTime
                val isNearRF = baseClassification.distanceZone == com.examshield.ai.domain.model.DistanceZone.IMMEDIATE || 
                             baseClassification.distanceZone == com.examshield.ai.domain.model.DistanceZone.NEAR
                
                var finalResult = baseClassification
                if (timeSinceMagnet < 4000 && isNearRF) {
                    finalResult = baseClassification.copy(
                        riskLevel = com.examshield.ai.domain.model.RiskLevel.LEVEL_4_CONFIRMED_THREAT,
                        confidenceScore = 100,
                        discoveryReason = "${baseClassification.discoveryReason} [MAG_FUSION_VERIFIED]".trim()
                    )
                }

                updateIntensityBasedOnDistance(finalResult.distanceZone)

                val stability = sensorFusionEngine.getSignalIntegrity(detectedObj.macAddress)
                val callbacks = callbackCounts.getOrDefault(detectedObj.macAddress, 0)
                callbackCounts[detectedObj.macAddress] = callbacks + 1
                
                val tempConfidenceBase = if (baseClassification.confidenceScore > 0) baseClassification.confidenceScore else 50
                var confidenceMod = 0
                if (stability > 0.8) confidenceMod += 15
                if (finalResult.estimatedDistanceMeters < 3.0) confidenceMod += 10
                if (callbacks > 5) confidenceMod += 5
                
                val confidence = (tempConfidenceBase + confidenceMod).coerceIn(0, 100)

                finalResult = finalResult.copy(
                    confidenceScore = confidence,
                    discoveryReason = "${finalResult.discoveryReason} [FUSION]${if (_currentOrbitalData.value.isSecure && confidence == 100) " [ORBIT: ${_currentOrbitalData.value.latitude.toString().take(6)}, ${_currentOrbitalData.value.longitude.toString().take(6)}]" else ""}".trim()
                )

                if (confidence >= 50 || finalResult.isNexusVerified) {
                    hapticSonarManager.updateSonarTarget(finalResult.distanceZone, confidence, finalResult.isNexusVerified)
                }

                if (finalResult.riskLevel == com.examshield.ai.domain.model.RiskLevel.LEVEL_4_CONFIRMED_THREAT) {
                    swarmMeshService.broadcastThreat(finalResult)
                }

                val allyIntel = swarmIdentities[detectedObj.macAddress]
                if (allyIntel != null && allyIntel.confidence >= 80) {
                     finalResult = finalResult.copy(
                         confidenceScore = Math.max(finalResult.confidenceScore, allyIntel.confidence),
                         riskLevel = com.examshield.ai.domain.model.RiskLevel.LEVEL_4_CONFIRMED_THREAT,
                         discoveryReason = "${finalResult.discoveryReason} [SWARM_MESH_CONFIRMED]".trim()
                     )
                     swarmIdentities.remove(detectedObj.macAddress)
                }

                val directives = neuralLink.directives.value
                val rangeLimit = if (directives.stealthPeekEnabled) _maxDetectionRange.value + 2.0f else _maxDetectionRange.value

                if (finalResult.estimatedDistanceMeters > rangeLimit) {
                    return@mapNotNull null
                }

                finalResult
            }
            .filter { result -> 
                val type = result.deviceType
                type == com.examshield.ai.domain.model.DeviceType.SMARTPHONE ||
                type == com.examshield.ai.domain.model.DeviceType.SMARTWATCH ||
                type == com.examshield.ai.domain.model.DeviceType.WIRELESS_EARBUD ||
                type == com.examshield.ai.domain.model.DeviceType.NANO_EARPIECE ||
                type == com.examshield.ai.domain.model.DeviceType.MAGNETIC_ANOMALY
            }
            .flowOn(Dispatchers.Default)
    }

    private var lastAzimuth = 0f

    private fun updateIntensityBasedOnDistance(zone: com.examshield.ai.domain.model.DistanceZone) {
        val intensity = when (zone) {
            com.examshield.ai.domain.model.DistanceZone.IMMEDIATE -> com.examshield.ai.domain.repository.ScanIntensity.ULTRA_FAST
            com.examshield.ai.domain.model.DistanceZone.NEAR -> com.examshield.ai.domain.repository.ScanIntensity.HIGH_PRECISION
            else -> com.examshield.ai.domain.repository.ScanIntensity.BALANCED
        }
        updateAllScannersIntensity(intensity)
    }

    private fun updateAllScannersIntensity(intensity: com.examshield.ai.domain.repository.ScanIntensity) {
        bleScanner.updateScanIntensity(intensity)
        classicBluetoothScanner.updateScanIntensity(intensity)
        wifiScanner.updateScanIntensity(intensity)
        wifiDirectScanner.updateScanIntensity(intensity)
        magneticFieldScanner.updateScanIntensity(intensity)
    }

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
 Riverside
