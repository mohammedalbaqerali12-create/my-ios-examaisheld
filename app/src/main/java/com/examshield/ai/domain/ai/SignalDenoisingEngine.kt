package com.examshield.ai.domain.ai

import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * SignalDenoisingEngine: Surgically cleans raw RSSI signals to eliminate environmental 
 * multi-path interference and hardware jitter.
 */
class SignalDenoisingEngine {

    private val signalHistory = ConcurrentHashMap<String, MutableList<Int>>()
    private val WINDOW_SIZE = 7
    private val ALPHA = 0.3f // Smoothing factor for EMA (Exponential Moving Average)
    private val smoothedRssi = ConcurrentHashMap<String, Float>()

    /**
     * Processes a raw RSSI value and returns a denoised stable value.
     */
    fun denoise(macAddress: String, rawRssi: Int): Int {
        val history = signalHistory.getOrPut(macAddress) { Collections.synchronizedList(mutableListOf()) }
        
        // 1. Sliding Window Median Filter (Removes Outliers/Spikes)
        history.add(rawRssi)
        if (history.size > WINDOW_SIZE) {
            history.removeAt(0)
        }

        val sorted = history.sorted()
        val median = sorted[sorted.size / 2]

        // 2. Exponential Moving Average (EMA) for Smoothing
        val currentSmooth = smoothedRssi[macAddress] ?: median.toFloat()
        val newSmooth = (ALPHA * median) + (1 - ALPHA) * currentSmooth
        smoothedRssi[macAddress] = newSmooth

        return newSmooth.toInt()
    }

    /**
     * Calculates variance to determine signal stability.
     */
    fun getStabilityScore(macAddress: String): Float {
        val history = signalHistory[macAddress] ?: return 0f
        if (history.size < 3) return 0f
        
        val avg = history.average()
        val variance = history.map { (it - avg) * (it - avg) }.sum() / history.size
        
        // Return a score from 0 (unstable) to 1 (stable)
        return (1.0 - (variance / 50.0).coerceIn(0.0, 1.0)).toFloat()
    }
}
