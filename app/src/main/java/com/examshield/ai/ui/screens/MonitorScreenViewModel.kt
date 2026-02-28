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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    // High-frequency stream for the Radar screen to track orientation in real-time
    private val _rawDetectionStream = MutableSharedFlow<ClassificationResult>(replay = 0, extraBufferCapacity = 64)
    val rawDetectionStream = _rawDetectionStream.asSharedFlow()

    // A fully thread-safe invisible basket to collect hardware pings at maximum speed
    private val backgroundDataCache = ConcurrentHashMap<String, ClassificationResult>()
    private var scanningJob: kotlinx.coroutines.Job? = null

    fun toggleScan() {
        if (_isScanning.value) {
            stopScanning()
        } else {
            startScanning()
        }
    }

    private fun startScanning() {
        if (_isScanning.value) return // Already scanning
        
        _isScanning.value = true
        _threatListMap.value = emptyMap() // clear old visual results
        backgroundDataCache.clear() // clear old background cache
        
        // Cancel any lingering job just in case
        scanningJob?.cancel()

        // 1. Silent Hardware Collector (Runs as fast as possible without disturbing UI)
        scanningJob = viewModelScope.launch {
            detectionService.observeThreats().collect { result ->
                // Emit to high-frequency stream for UI components that need real-time data
                _rawDetectionStream.emit(result)
                
                // Only collect highly confident or suspicious data for the main list
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
        scanningJob?.cancel()
        scanningJob = null
        detectionService.stop()
        backgroundDataCache.clear()
    }
}
