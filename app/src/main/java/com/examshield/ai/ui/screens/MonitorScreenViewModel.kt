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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MonitorScreenViewModel @Inject constructor(
    private val detectionService: DetectionService
) : ViewModel() {

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _threatListMap = MutableStateFlow<Map<String, ClassificationResult>>(emptyMap())
    val threatList: StateFlow<List<ClassificationResult>> = _threatListMap.map {
        it.values.toList()
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleScan() {
        if (_isScanning.value) {
            stopScanning()
        } else {
            startScanning()
        }
    }

    private fun startScanning() {
        _isScanning.value = true
        _threatListMap.value = emptyMap() // clear old results

        viewModelScope.launch {
            detectionService.observeThreats()
                .collect { result ->
                    // Only collect suspicious or classified items to avoid clutter
                    if (result.deviceType != DeviceType.SUSPICIOUS_UNKNOWN || result.confidenceScore > 60) {
                        _threatListMap.update { currentMap ->
                            val newMap = currentMap.toMutableMap()
                            newMap[result.rawObject.macAddress] = result
                            newMap
                        }
                    }
                }
        }
    }

    private fun stopScanning() {
        _isScanning.value = false
        detectionService.stop()
    }
}
