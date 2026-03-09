package com.examshield.ai.vision

import android.annotation.SuppressLint
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class VisionTarget(
    val boundingBox: Rect,
    val trackingId: Int?,
    val labels: List<String>,
    val brandHeuristic: String? = null
)

class TargetLockVisionAnalyzer : ImageAnalysis.Analyzer {

    // Use ML Kit's default object detector (fast, tracks multiple objects)
    private val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableMultipleObjects()
        .enableClassification() // Gets broad categories like "Fashion good"
        .build()

    private val objectDetector = ObjectDetection.getClient(options)

    private val _detectedTargets = MutableStateFlow<List<VisionTarget>>(emptyList())
    val detectedTargets: StateFlow<List<VisionTarget>> = _detectedTargets

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            
            objectDetector.process(image)
                .addOnSuccessListener { detectedObjects ->
                    val targets = detectedObjects.map { obj ->
                        val objLabels = obj.labels.map { it.text }
                        val brand = when {
                            objLabels.any { it.contains("Mobile phone", true) || it.contains("Phone", true) } -> "MOBILE_COMM_DEVICE"
                            objLabels.any { it.contains("Watch", true) || it.contains("Wearable", true) } -> "WEARABLE_STATION"
                            objLabels.any { it.contains("Headphones", true) || it.contains("Earbuds", true) } || 
                            objLabels.any { it.contains("Audio", true) } -> "AUDIO_TRANSCEIVER"
                            objLabels.any { it.contains("Tablet", true) || it.contains("Computer", true) } -> "COMPUTING_TERMINAL"
                            objLabels.any { it.contains("Hardware", true) || it.contains("Component", true) } -> "ELECTRONIC_CORE"
                            else -> null
                        }
                        VisionTarget(
                            boundingBox = obj.boundingBox,
                            trackingId = obj.trackingId,
                            labels = objLabels,
                            brandHeuristic = brand
                        )
                    }
                    _detectedTargets.value = targets
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("VisionAI", "Object detection failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
