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
        evolveSystem(signalVariance, stability)

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

    suspend fun applySupervisorLogic(
        result: ClassificationResult,
        isCheating: Boolean,
        environmentId: String
    ) {
        val signalHash = SignalFeatureHasher.hashSignal(result.rawObject)
        
        if (isCheating) {
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
            
            learningRepository.addDecisionHistory(
                SignalDecision(
                    signalHash = signalHash,
                    decision = "CHEATING",
                    environmentId = environmentId,
                    initialConfidence = result.confidenceScore,
                    finalRiskLevel = 4
                )
            )
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

            learningRepository.addDecisionHistory(
                SignalDecision(
                    signalHash = signalHash,
                    decision = "FRIENDLY",
                    environmentId = environmentId,
                    initialConfidence = result.confidenceScore,
                    finalRiskLevel = 1
                )
            )
            
            selfCorrectionPenalties[result.rawObject.macAddress] = 50
        }
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

    suspend fun checkForGlobalThreatUpdates() {
        android.util.Log.d("ASTRA_NEXUS", "Initiating Global Threat Intel Synchronization...")
        kotlinx.coroutines.delay(1500)
        
        val globalThreatSignatures = listOf(
            LearnedRule(pattern = "NAME:vip pro", detectedType = DeviceType.NANO_EARPIECE.name, confidenceBoost = 100),
            LearnedRule(pattern = "NAME:mic spy", detectedType = DeviceType.NANO_EARPIECE.name, confidenceBoost = 100),
            LearnedRule(pattern = "NAME:esp32", detectedType = DeviceType.SUSPICIOUS_UNKNOWN.name, confidenceBoost = 85),
            LearnedRule(pattern = "NAME:hc-05", detectedType = DeviceType.NANO_EARPIECE.name, confidenceBoost = 90),
            LearnedRule(pattern = "NAME:hc-06", detectedType = DeviceType.NANO_EARPIECE.name, confidenceBoost = 90),
            LearnedRule(pattern = "NAME:bt-serial", detectedType = DeviceType.NANO_EARPIECE.name, confidenceBoost = 90),
            LearnedRule(pattern = "NAME:invisible ear", detectedType = DeviceType.NANO_EARPIECE.name, confidenceBoost = 100),
            LearnedRule(pattern = "NAME:k-ear", detectedType = DeviceType.NANO_EARPIECE.name, confidenceBoost = 100),
            LearnedRule(pattern = "NAME:mag-spy", detectedType = DeviceType.NANO_EARPIECE.name, confidenceBoost = 100),
            LearnedRule(pattern = "MAC_OUI:CC50E3", detectedType = DeviceType.NANO_EARPIECE.name, confidenceBoost = 90), 
            LearnedRule(pattern = "MAC_OUI:001B10", detectedType = DeviceType.NANO_EARPIECE.name, confidenceBoost = 90),
            LearnedRule(pattern = "MAC_OUI:12:34:56", detectedType = DeviceType.NANO_EARPIECE.name, confidenceBoost = 90),
            LearnedRule(pattern = "MAC_OUI:20:13:08", detectedType = DeviceType.NANO_EARPIECE.name, confidenceBoost = 95)
        )

        var newLearned = 0
        val existingRules = learningRepository.getAllLearnedRules().map { it.pattern }

        for (signature in globalThreatSignatures) {
            if (!existingRules.contains(signature.pattern)) {
                learningRepository.addLearnedRule(signature)
                newLearned++
            }
        }

        if (newLearned > 0) {
            learnedRulesCache = null
            android.util.Log.e("ASTRA_NEXUS", "Evolution Complete. $newLearned Global Threat Signatures Injected. AI is armed.")
        }
    }

    /**
     * Autonomous Evolution Loop: Adjusts system sensitivity based on signal environment.
     */
    private fun evolveSystem(variance: Double, stability: Float) {
        neuralLink.mutate { current ->
            val newState = when {
                variance > 50.0 -> CentralNeuralLink.NeuralState.EVOLVING
                stability < 0.4f -> CentralNeuralLink.NeuralState.STEALTH
                else -> CentralNeuralLink.NeuralState.STABLE
            }

            val targetProcessNoise = if (variance > 30.0) 0.025 else 0.012
            val targetHapticMult = if (stability > 0.8f) 1.2f else 1.0f

            current.copy(
                aiNeuralState = newState,
                kalmanProcessNoise = targetProcessNoise,
                hapticIntensityMultiplier = targetHapticMult
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
