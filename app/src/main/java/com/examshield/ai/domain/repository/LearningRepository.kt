package com.examshield.ai.domain.repository

import com.examshield.ai.data.local.model.Baseline
import com.examshield.ai.data.local.model.Scan
import com.examshield.ai.data.local.model.LearnedRule
import com.examshield.ai.data.local.model.FriendlySignal
import com.examshield.ai.data.local.model.ConfirmedCheatingSignal
import com.examshield.ai.data.local.model.SignalDecision

interface LearningRepository {
    suspend fun addScan(scan: Scan)
    suspend fun getBaseline(macAddress: String): Baseline?
    suspend fun updateBaseline(baseline: Baseline)
    suspend fun getRecentScansForDevice(macAddress: String, since: Long): List<Scan>
    
    // ASTRA V11: NEXUS EVOLUTION
    suspend fun addLearnedRule(rule: LearnedRule)
    suspend fun getAllLearnedRules(): List<LearnedRule>

    // ASTRA NEXUS: SUPERVISOR FEEDBACK LOOP
    suspend fun addFriendlySignal(signal: FriendlySignal)
    suspend fun getFriendlySignal(hash: String): FriendlySignal?
    suspend fun addCheatingSignal(signal: ConfirmedCheatingSignal)
    suspend fun getCheatingSignal(hash: String): ConfirmedCheatingSignal?
    suspend fun addDecisionHistory(decision: SignalDecision)
}
