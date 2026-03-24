package com.examshield.ai.domain.ai

import kotlinx.coroutines.flow.Flow

/**
 * Service to provide advanced AI-driven threat intelligence.
 */
interface AiIntelligenceService {
    /**
     * Analyzes a potential threat using cloud-based AI.
     */
    fun analyzeThreat(
        mac: String,
        name: String?,
        manufacturer: String?,
        rssi: Int,
        deviceType: String
    ): Flow<String>
}
