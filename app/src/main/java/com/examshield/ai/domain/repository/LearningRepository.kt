package com.examshield.ai.domain.repository

import com.examshield.ai.data.local.model.Baseline
import com.examshield.ai.data.local.model.Scan

interface LearningRepository {
    suspend fun addScan(scan: Scan)
    suspend fun getBaseline(macAddress: String): Baseline?
    suspend fun updateBaseline(baseline: Baseline)
    suspend fun getRecentScansForDevice(macAddress: String, since: Long): List<Scan>
}
