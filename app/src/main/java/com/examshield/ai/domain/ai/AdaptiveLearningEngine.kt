package com.examshield.ai.domain.ai

import com.examshield.ai.domain.model.DetectedObject
import com.examshield.ai.domain.repository.LearningRepository
import javax.inject.Inject
import kotlin.math.min

class AdaptiveLearningEngine @Inject constructor(
    private val learningRepository: LearningRepository
) {

    /**
     * Re-weights the AI confidence score dynamically based on the environment's baseline 
     * and historical supervisor reinforcements.
     */
    suspend fun adjustConfidence(
        detectedObject: DetectedObject,
        baseConfidence: Int
    ): Int {
        var adjustedConfidence = baseConfidence

        // 1. Environmental Baseline Analysis (Calibration phase check)
        // If the device was in the hall before the exam started, it is highly likely it's a safe device 
        // (like a classroom projector, ceiling AP, or a legitimate campus Wi-Fi network).
        val isInBaseline = learningRepository.isDeviceInCalibrationBaseline(detectedObject.macAddress)
        if (isInBaseline) {
            // Cut confidence massively, it is just environmental noise
            adjustedConfidence = (adjustedConfidence * 0.2).toInt() // Reduce by 80%
        }

        // 2. Supervisor Reinforcement Learning (Human-in-the-loop)
        // Check if a supervisor previously confirmed this specific MAC address as a cheating device.
        val confirmationCount = learningRepository.getSupervisorConfirmationCount(detectedObject.macAddress)
        if (confirmationCount > 0) {
            // If it was confirmed as a cheat before, confidence sky-rockets.
            // Add 30% for the first confirmation, 50% for multiple.
            val reinforcementBoost = if (confirmationCount == 1) 30 else 50
            adjustedConfidence += reinforcementBoost
        }

        // Note: Real TF-Lite models would also look at temporal behavior here
        // (e.g., burst intervals, unexpected MAC changing). We simulate this adaptive thresholding.

        // Cap confidence between 0 and 100
        return min(100, kotlin.math.max(0, adjustedConfidence))
    }
}
