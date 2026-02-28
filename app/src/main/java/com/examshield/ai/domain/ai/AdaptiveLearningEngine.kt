package com.examshield.ai.domain.ai

import com.examshield.ai.data.local.model.Baseline
import com.examshield.ai.data.local.model.Scan
import com.examshield.ai.domain.model.DetectedObject
import com.examshield.ai.domain.repository.LearningRepository
import javax.inject.Inject
import java.util.concurrent.TimeUnit

import com.examshield.ai.data.local.model.LearnedRule
import com.examshield.ai.domain.model.ClassificationResult
import com.examshield.ai.domain.model.DeviceType
import com.examshield.ai.data.local.model.FriendlySignal
import com.examshield.ai.data.local.model.ConfirmedCheatingSignal
import com.examshield.ai.data.local.model.SignalDecision
import com.examshield.ai.domain.model.SupervisorFeedback

class AdaptiveLearningEngine @Inject constructor(
    private val learningRepository: LearningRepository
) {

    // How far back to look for recent activity
    private val RECENCY_THRESHOLD_MS = TimeUnit.MINUTES.toMillis(5)

    // Cache learned rules in memory for high-speed matching
    private var learnedRulesCache: List<LearnedRule>? = null

    /**
     * Analyzes a detected object, updates the baseline, and returns context about it.
     */
    suspend fun analyze(detectedObject: DetectedObject): AnalysisContext {
        // Record the raw scan
        learningRepository.addScan(
            Scan(
                macAddress = detectedObject.macAddress,
                deviceName = detectedObject.name,
                timestamp = detectedObject.timestampMs,
                signalStrength = detectedObject.signalStrengthRssi
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

        // Self-Learning: Calculate consistency of signal
        val signalVariance = if (recentScans.size > 2) {
            val rssis = recentScans.map { it.signalStrength }
            val avg = rssis.map { it.toDouble() }.sum() / rssis.size
            var sumSq = 0.0
            for (item in rssis) sumSq += Math.pow(item.toDouble() - avg, 2.0)
            sumSq / rssis.size
        } else 100.0

        // Practice/Learning: Devices that appear in short predictable bursts are more likely to be cheating gear
        val isConsistentStream = recentScans.size > 5 && signalVariance < 15.0

        // ASTRA V11: Match against learned patterns (Offline Intel)
        val learnedMatch = matchLearnedRules(detectedObject)

        // ASTRA NEXUS: SUPERVISOR FEEDBACK LOOP
        val signalHash = SignalFeatureHasher.hashSignal(detectedObject)
        val friendlyRecord = learningRepository.getFriendlySignal(signalHash)
        val cheatingRecord = learningRepository.getCheatingSignal(signalHash)

        var feedbackRiskElevation = 0
        if (cheatingRecord != null) {
            feedbackRiskElevation = 40 + (cheatingRecord.hitCount * 5)
        }

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
            feedbackRiskElevation = feedbackRiskElevation
        )
    }

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
            // Friendly logic
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
        // Extract pattern: If it's a verified threat, learn its OUI or Name
        val name = result.rawObject.name
        val mac = result.rawObject.macAddress
        
        // 1. Learn OUI (Top 3 bytes)
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
        
        // 2. Learn Name if specific enough
        if (!name.isNullOrBlank() && name.length > 5 && !name.contains("PHONE", true)) {
            learningRepository.addLearnedRule(
                LearnedRule(
                    pattern = "NAME:$name",
                    detectedType = result.deviceType.name,
                    confidenceBoost = 30
                )
            )
        }
        
        // Invalidate cache
        learnedRulesCache = null
    }

    private fun checkForGlobalThreatUpdates() {
        // This simulates the AI 'practicing' and 'learning' from cloud-updated patterns
        // In a real implementation, this would sync with a remote intelligence server periodically
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
    val feedbackRiskElevation: Int = 0
)
