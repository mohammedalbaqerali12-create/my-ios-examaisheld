package com.examshield.ai.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object VibrationHelper {

    fun vibrateShort() {
        vibrate(80L)
    }

    fun vibrateThreatPulse() {
        val effect = longArrayOf(0, 150, 100, 150)
        vibrate(effect, -1)
    }

    fun vibrateSuccess() {
        val effect = longArrayOf(0, 100, 50, 100)
        vibrate(effect, -1)
    }

    fun vibrateWarning() {
        vibrate(300L)
    }

    private fun vibrate(milliseconds: Long) {
        val context = ContextUtils.getAppContext()
        val vibrator = getVibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(milliseconds)
        }
    }

    private fun vibrate(pattern: LongArray, repeat: Int) {
        val context = ContextUtils.getAppContext()
        val vibrator = getVibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat))
        } else {
            vibrator.vibrate(pattern, repeat)
        }
    }

    private fun getVibrator(context: Context): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
}
