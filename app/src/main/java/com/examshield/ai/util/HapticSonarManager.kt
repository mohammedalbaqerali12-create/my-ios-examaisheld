package com.examshield.ai.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.examshield.ai.domain.model.DistanceZone
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import com.examshield.ai.domain.ai.CentralNeuralLink

@Singleton
class HapticSonarManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val neuralLink: CentralNeuralLink
) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private var toneGenerator: ToneGenerator? = null
    private var isSonarActive = false
    private var sonarJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        } catch (e: Exception) {
            android.util.Log.e("HapticSonar", "Failed to init ToneGenerator: ${e.message}")
        }
    }

    fun updateSonarTarget(distanceZone: DistanceZone, confidence: Int, isNexusVerified: Boolean) {
        if (confidence < 50 && !isNexusVerified) {
            stopSonar()
            return
        }

        val delayMs = when (distanceZone) {
            DistanceZone.IMMEDIATE -> 200L
            DistanceZone.NEAR -> 600L
            DistanceZone.MEDIUM -> 1200L
            DistanceZone.FAR -> 2500L
        }

        if (delayMs > 0) {
            startPulsing(delayMs, distanceZone == DistanceZone.IMMEDIATE)
        } else {
            stopSonar()
        }
    }

    private fun startPulsing(intervalMs: Long, isCritical: Boolean) {
        if (isSonarActive && sonarJob?.isActive == true) {
            sonarJob?.cancel()
        }

        isSonarActive = true
        sonarJob = scope.launch {
            while (isSonarActive) {
                ping(isCritical)
                delay(intervalMs)
            }
        }
    }

    private fun ping(isCritical: Boolean) {
        try {
            if (vibrator.hasVibrator()) {
                val aiMultiplier = neuralLink.directives.value.hapticIntensityMultiplier
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amplitude = if (isCritical) 255 else (100 * aiMultiplier).toInt().coerceIn(1, 255)
                    val effect = VibrationEffect.createOneShot(if (isCritical) 50 else 20, amplitude)
                    vibrator. v vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(if (isCritical) 50L else 20L)
                }
            }
            toneGenerator?.startTone(if (isCritical) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 50)
        } catch (e: Exception) {
             android.util.Log.e("HapticSonar", "Ping failed: ${e.message}")
        }
    }

    fun stopSonar() {
        isSonarActive = false
        sonarJob?.cancel()
    }

    fun release() {
        stopSonar()
        toneGenerator?.release()
        toneGenerator = null
    }
}
