package com.examshield.ai.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examshield.ai.domain.model.ClassificationResult
import com.examshield.ai.domain.model.DeviceType
import com.examshield.ai.domain.repository.DetectionService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject
import java.util.concurrent.ConcurrentHashMap

@HiltViewModel
class MonitorScreenViewModel @Inject constructor(
    private val detectionService: DetectionService
) : ViewModel() {

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _threatListMap = MutableStateFlow<Map<String, ClassificationResult>>(emptyMap())
    val threatList: StateFlow<List<ClassificationResult>> = _threatListMap.map {
        it.values.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // A fully thread-safe invisible basket to collect hardware pings at maximum speed
    private val backgroundDataCache = ConcurrentHashMap<String, ClassificationResult>()

    fun toggleScan() {
        if (_isScanning.value) {
            stopScanning()
        } else {
            startScanning()
        }
    }

    private fun startScanning() {
        _isScanning.value = true
        _threatListMap.value = emptyMap() // clear old visual results
        backgroundDataCache.clear() // clear old background cache

        // 1. Silent Hardware Collector (Runs as fast as possible without disturbing UI)
        viewModelScope.launch {
            detectionService.observeThreats().collect { result ->
                // Only collect highly confident or suspicious data
                if (result.deviceType != DeviceType.SUSPICIOUS_UNKNOWN || result.confidenceScore > 60) {
                    backgroundDataCache[result.rawObject.macAddress] = result
                }
            }
        }

        // 2. Strict 1-Second UI Ticker Clock (The "Radar Sweep")
        viewModelScope.launch {
            while (_isScanning.value) {
                // Snap a photograph of the invisible basket and paint it on the screen
                _threatListMap.value = backgroundDataCache.toMap()
                
                // Wait exactly 1 second (1000 milliseconds) before updating again
                delay(1000)
            }
        }
    }

    private fun stopScanning() {
        _isScanning.value = false
        detectionService.stop()
        backgroundDataCache.clear()
    }
}
