package com.examshield.ai.domain.repository

import kotlinx.coroutines.flow.Flow

data class OrbitalData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val speed: Float = 0f,
    val satelliteCount: Int = 0,
    val isSecure: Boolean = false // True if we have a valid 3D fix
)

interface OrbitalUplink {
    /**
     * Starts binding to the GNSS satellites and returns a continuous flow
     * of OrbitalData (coordinates and satellite lock count).
     */
    fun streamOrbitalData(): Flow<OrbitalData>

    /**
     * Instantly grabs the last known reliable orbital stamp.
     */
    suspend fun requestOrbitalStamp(): OrbitalData
}
