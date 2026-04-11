package com.examshield.ai.domain.ai

import com.examshield.ai.domain.model.ClassificationResult
import com.examshield.ai.domain.model.DetectedObject

interface DeviceClassifier {
    /**
     * Runs TFLite inference on the signal features of the DetectedObject
     * and returns a ClassificationResult with a Confidence Score.
     */
    suspend fun classify(detectedObject: DetectedObject): ClassificationResult
}
