package com.examshield.ai.domain.ai

import com.examshield.ai.data.local.model.Baseline
import com.examshield.ai.data.local.model.Scan
import com.examshield.ai.domain.model.DetectedObject
import com.examshield.ai.domain.repository.LearningRepository
import javax.inject.Inject
import java.util.concurrent.TimeUnit

class AdaptiveLearningEngine @Inject constructor(
    private val learningRepository: LearningRepository
) {

    // How far back to look for recent activity
    private val RECENCY_THRESHOLD_MS = TimeUnit.MINUTES.toMillis(5)

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

        return AnalysisContext(
            isNew = isNewDevice,
            observationCount = newObservationCount,
            isFrequentlySeen = newObservationCount > 10, // Example threshold
            isRepeatedlySeenRecently = recentScans.size > 3 // Example threshold
        )
    }
}

data class AnalysisContext(
    val isNew: Boolean,
    val observationCount: Int,
    val isFrequentlySeen: Boolean,
    val isRepeatedlySeenRecently: Boolean
)
