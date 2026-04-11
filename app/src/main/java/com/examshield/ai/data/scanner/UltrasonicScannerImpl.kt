package com.examshield.ai.data.scanner

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.examshield.ai.domain.model.DetectedObject
import com.examshield.ai.domain.repository.ScanIntensity
import com.examshield.ai.domain.repository.Scanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sqrt

class UltrasonicScannerImpl(
    private val context: Context
) : Scanner {

    private val sampleRate = 44100
    private var scanJob: Job? = null
    private var isScanning = false
    private var intensity = ScanIntensity.BALANCED

    // The target frequencies we want to look for (covert earpieces / signaling)
    private val targetFrequencies = listOf(18000.0, 19000.0, 20000.0, 21000.0, 22000.0)

    @SuppressLint("MissingPermission")
    override fun startScanning(): Flow<DetectedObject> = flow {
        isScanning = true
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e("UltrasonicScanner", "Audio record buffer size error")
            return@flow
        }

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("UltrasonicScanner", "AudioRecord not initialized")
            return@flow
        }

        audioRecord.startRecording()
        val buffer = ShortArray(bufferSize)

        try {
            while (currentCoroutineContext().isActive && isScanning) {
                val readResult = audioRecord.read(buffer, 0, buffer.size)

                if (readResult > 0) {
                    var maxMagnitude = 0.0
                    var detectedFreq = 0.0

                    // Run Goertzel algorithm for each target frequency
                    for (freq in targetFrequencies) {
                        val magnitude = calculateGoertzel(buffer, readResult, sampleRate, freq)
                        if (magnitude > maxMagnitude) {
                            maxMagnitude = magnitude
                            detectedFreq = freq
                        }
                    }

                    // Threshold representing a strong high-frequency signal
                    val threshold = 50000.0

                    if (maxMagnitude > threshold) {
                        // Normalize magnitude to a mock RSSI value (0 to -100)
                        val mockRssi = -100 + (maxMagnitude / 10000).toInt().coerceAtMost(100)

                        val detectedObj = DetectedObject(
                            macAddress = "ULTRASONIC_SOURCE_${detectedFreq.toLong()}",
                            name = "Covert Ultrasonic Audio Signal (%.1fkHz)".format(detectedFreq / 1000),
                            signalStrengthRssi = mockRssi,
                            isWifi = false,
                            isBle = false,
                            isClassicBluetooth = false,
                            extraMetadata = mapOf(
                                "frequency" to detectedFreq,
                                "magnitude" to maxMagnitude,
                                "type" to "ULTRASONIC"
                            )
                        )
                        emit(detectedObj)
                    }
                }

                // Adjust delay based on intensity
                val delayTime = when (intensity) {
                    ScanIntensity.LOW_POWER -> 2000L
                    ScanIntensity.BALANCED -> 1000L
                    ScanIntensity.HIGH_PRECISION -> 500L
                    ScanIntensity.ULTRA_FAST -> 200L
                }
                delay(delayTime)
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }
    }.flowOn(Dispatchers.IO)

    override fun stopScanning() {
        isScanning = false
        scanJob?.cancel()
    }

    override fun updateScanIntensity(intensity: ScanIntensity) {
        this.intensity = intensity
    }

    /**
     * A lightweight Goertzel algorithm to detect a specific frequency's magnitude.
     */
    private fun calculateGoertzel(samples: ShortArray, numSamples: Int, sampleRate: Int, targetFrequency: Double): Double {
        val k = (0.5 + (numSamples * targetFrequency) / sampleRate).toInt()
        val w = (2.0 * PI * k) / numSamples
        val cosine = cos(w)
        val coeff = 2.0 * cosine

        var q0 = 0.0
        var q1 = 0.0
        var q2 = 0.0

        for (i in 0 until numSamples) {
            q0 = coeff * q1 - q2 + samples[i]
            q2 = q1
            q1 = q0
        }

        val magnitudeSquared = q1 * q1 + q2 * q2 - q1 * q2 * coeff
        return sqrt(magnitudeSquared)
    }
}
