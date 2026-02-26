package com.examshield.ai.domain.model

data class DetectedObject(
    val macAddress: String,
    val name: String?,
    val signalStrengthRssi: Int,
    val isWifi: Boolean = false,
    val isBle: Boolean = false,
    val isClassicBluetooth: Boolean = false,
    val rawData: ByteArray? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
