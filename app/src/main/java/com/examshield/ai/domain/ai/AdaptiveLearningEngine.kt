package com.examshield.ai.domain.ai

import com.examshield.ai.data.local.model.Baseline
import com.examshield.ai.data.local.model.Scan
import com.examshield.ai.domain.model.DetectedObject
import com.examshield.ai.domain.repository.LearningRepository
import javax.inject.Inject
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

import com.examshield.ai.data.local.model.LearnedRule
import com.examshield.ai.domain.model.ClassificationResult
import com.examshield.ai.domain.model.DeviceType
import com.examshield.ai.data.local.model.FriendlySignal
import com.examshield.ai.data.local.model.ConfirmedCheatingSignal
import com.examshield.ai.data.local.model.SignalDecision
import com.examshield.ai.domain.model.SupervisorFeedback
import com.examshield.ai.domain.model.RiskLevel

class AdaptiveLearningEngine @Inject constructor(
    private val learningRepository: com.examshield.ai.domain.repository.LearningRepository,
    private val neuralLink: CentralNeuralLink
) {
    private val denoisingEngine = SignalDenoisingEngine()

    private val selfCorrectionPenalties = ConcurrentHashMap<String, Int>()
    private val RECENCY_THRESHOLD_MS = TimeUnit.MINUTES.toMillis(5)
    private var learnedRulesCache: List<LearnedRule>? = null

    suspend fun analyze(detectedObject: DetectedObject): AnalysisContext {
        val cleanRssi = denoisingEngine.denoise(detectedObject.macAddress, detectedObject.signalStrengthRssi)
        val stability = denoisingEngine.getStabilityScore(detectedObject.macAddress)

        learningRepository.addScan(
            Scan(
                macAddress = detectedObject.macAddress,
                deviceName = detectedObject.name,
                timestamp = detectedObject.timestampMs,
                signalStrength = cleanRssi
            )
        )

        val existingBaseline = learningRepository.getBaseline(detectedObject.macAddress)
        val isNewDevice = existingBaseline == null
        val newObservationCount = (existingBaseline?.observationCount ?: 0) + 1

        val baseline = existingBaseline?.apply {
            lastSeen = detectedObject.timestampMs
            observationCount = newObservationCount
        } ?: Baseline(
            macAddress = detectedObject.macAddress,
            firstSeen = detectedObject.timestampMs,
            lastSeen = detectedObject.timestampMs,
            observationCount = 1,
            avgRssi = detectedObject.signalStrengthRssi.toDouble()
        )

        learningRepository.updateBaseline(baseline)

        val recentScans = learningRepository.getRecentScansForDevice(
            detectedObject.macAddress, 
            System.currentTimeMillis() - RECENCY_THRESHOLD_MS
        )

        val signalVariance = if (recentScans.size > 2) {
            val rssis = recentScans.map { it.signalStrength }
            val avg = rssis.map { it.toDouble() }.sum() / rssis.size
            var sumSq = 0.0
            for (item in rssis) sumSq += Math.pow(item.toDouble() - avg, 2.0)
            sumSq / rssis.size
        } else 100.0

        val isConsistentStream = recentScans.size > 5 && signalVariance < 15.0
        val learnedMatch = matchLearnedRules(detectedObject)

        val signalHash = SignalFeatureHasher.hashSignal(detectedObject)
        val friendlyRecord = learningRepository.getFriendlySignal(signalHash)
        val cheatingRecord = learningRepository.getCheatingSignal(signalHash)

        var feedbackRiskElevation = 0
        if (cheatingRecord != null) {
            feedbackRiskElevation = 40 + (cheatingRecord.hitCount * 5)
        }

        // ASTRA NEURAL EVOLUTION: Analyze environment stability and mutate system
        evolveSystem(signalVariance, stability, cheatingRecord != null)

        return AnalysisContext(
            isNew = isNewDevice,
            observationCount = newObservationCount,
            isFrequentlySeen = newObservationCount > 20, 
            isRepeatedlySeenRecently = recentScans.size > 3,
            isConsistentStream = isConsistentStream,
            learnedSynergyBoost = learnedMatch?.confidenceBoost ?: 0,
            learnedTypePromotion = learnedMatch?.detectedType,
            isMarkedFriendly = friendlyRecord != null,
            isMarkedCheating = cheatingRecord != null,
            cheatingHitCount = cheatingRecord?.hitCount ?: 0,
            feedbackRiskElevation = feedbackRiskElevation,
            stability = stability
        )
    }

    fun getmacPenalty(mac: String): Int = selfCorrectionPenalties[mac] ?: 0

    suspend fun checkForGlobalThreatUpdates() {
        android.util.Log.d("ASTRA_NEXUS", "Initiating Global Threat Intel Synchronization...")
        kotlinx.coroutines.delay(1000)
    }

    suspend fun applySupervisorLogic(
        result: ClassificationResult,
        isCheating: Boolean,
        environmentId: String
    ) {
        val signalHash = SignalFeatureHasher.hashSignal(result.rawObject)
        
        if (isCheating) {
            // FORCE OVERDRIVE ON CHEATING DETECTION
            neuralLink.setNeuralState(CentralNeuralLink.NeuralState.OVERDRIVE)
            
            val existing = learningRepository.getCheatingSignal(signalHash)
            if (existing != null) {
                learningRepository.addCheatingSignal(
                    existing.copy(
                        hitCount = existing.hitCount + 1,
                        confirmedAt = System.currentTimeMillis()
                    )
                )
            } else {
                learningRepository.addCheatingSignal(
                    ConfirmedCheatingSignal(
                        signalHash = signalHash,
                        deviceType = result.deviceType.name,
                        lastSeenRssi = result.rawObject.signalStrengthRssi,
                        environmentId = environmentId
                    )
                )
            }
        } else {
            val existing = learningRepository.getFriendlySignal(signalHash)
            if (existing != null) {
                learningRepository.addFriendlySignal(
                    existing.copy(
                        observationCount = existing.observationCount + 1
                    )
                )
            } else {
                learningRepository.addFriendlySignal(
                    FriendlySignal(
                        signalHash = signalHash,
                        namePattern = result.rawObject.name,
                        macOui = result.rawObject.macAddress.take(8),
                        environmentId = environmentId
                    )
                )
            }
            selfCorrectionPenalties[result.rawObject.macAddress] = 50
        }
    }

    suspend fun learnFromNexus(result: ClassificationResult) {
        val name = result.rawObject.name
        val mac = result.rawObject.macAddress
        
        if (mac.length >= 8) {
            val oui = mac.take(8)
            learningRepository.addLearnedRule(
                LearnedRule(
                    pattern = "MAC_OUI:$oui",
                    detectedType = result.deviceType.name,
                    confidenceBoost = 25
                )
            )
        }
        
        if (!name.isNullOrBlank() && name.length > 5 && !name.contains("PHONE", true)) {
            learningRepository.addLearnedRule(
                LearnedRule(
                    pattern = "NAME:$name",
                    detectedType = result.deviceType.name,
                    confidenceBoost = 30
                )
            )
        }
        
        learnedRulesCache = null
    }

    private suspend fun matchLearnedRules(obj: DetectedObject): LearnedRule? {
        if (learnedRulesCache == null) {
            learnedRulesCache = learningRepository.getAllLearnedRules()
        }
        
        return learnedRulesCache?.find { rule ->
            when {
                rule.pattern.startsWith("MAC_OUI:") -> {
                    obj.macAddress.startsWith(rule.pattern.removePrefix("MAC_OUI:").trim())
                }
                rule.pattern.startsWith("NAME:") -> {
                    obj.name?.contains(rule.pattern.removePrefix("NAME:").trim(), ignoreCase = true) == true
                }
                else -> false
            }
        }
    }

    /**
     * Autonomous Evolution Loop: Adjusts system sensitivity based on signal environment.
     */
    private fun evolveSystem(variance: Double, stability: Float, hasCheatingSignal: Boolean) {
        neuralLink.mutate { current ->
            val newState = when {
                hasCheatingSignal && stability > 0.8f -> CentralNeuralLink.NeuralState.PRIME_SYNERGY
                hasCheatingSignal -> CentralNeuralLink.NeuralState.OVERDRIVE
                variance > 50.0 -> CentralNeuralLink.NeuralState.EVOLVING
                stability < 0.3f -> CentralNeuralLink.NeuralState.STEALTH
                stability > 0.9f && variance < 10.0 -> CentralNeuralLink.NeuralState.STABLE
                else -> current.aiNeuralState
            }

            val targetProcessNoise = when(newState) {
                CentralNeuralLink.NeuralState.PRIME_SYNERGY -> 0.005
                CentralNeuralLink.NeuralState.OVERDRIVE -> 0.025
                else -> 0.012
            }
            
            val scanningMultiplier = when(newState) {
                CentralNeuralLink.NeuralState.PRIME_SYNERGY -> 5.0f
                CentralNeuralLink.NeuralState.OVERDRIVE -> 2.5f
                else -> 1.0f
            }

            current.copy(
                aiNeuralState = newState,
                kalmanProcessNoise = targetProcessNoise,
                scanningSpeedMultiplier = scanningMultiplier,
                refreshRateHz = when(newState) {
                    CentralNeuralLink.NeuralState.PRIME_SYNERGY -> 120
                    CentralNeuralLink.NeuralState.OVERDRIVE -> 60
                    else -> 30
                },
                spectralSensitivity = if (newState == CentralNeuralLink.NeuralState.PRIME_SYNERGY) 2.5f else 1.0f,
                temporalSyncActive = newState == CentralNeuralLink.NeuralState.PRIME_SYNERGY
            )
        }
    }
}

data class AnalysisContext(
    val isNew: Boolean,
    val observationCount: Int,
    val isFrequentlySeen: Boolean,
    val isRepeatedlySeenRecently: Boolean,
    val isConsistentStream: Boolean,
    val learnedSynergyBoost: Int = 0,
    val learnedTypePromotion: String? = null,
    val isMarkedFriendly: Boolean = false,
    val isMarkedCheating: Boolean = false,
    val cheatingHitCount: Int = 0,
    val feedbackRiskElevation: Int = 0,
    val stability: Float = 0f
)
