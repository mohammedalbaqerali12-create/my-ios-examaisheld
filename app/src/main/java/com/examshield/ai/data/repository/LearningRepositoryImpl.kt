package com.examshield.ai.data.repository

import com.examshield.ai.data.local.dao.*
import com.examshield.ai.data.local.model.*
import com.examshield.ai.domain.repository.LearningRepository
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class LearningRepositoryImpl @Inject constructor(
    private val scanDao: ScanDao,
    private val baselineDao: BaselineDao,
    private val learnedRuleDao: LearnedRuleDao,
    private val friendlySignalDao: FriendlySignalDao,
    private val confirmedCheatingSignalDao: ConfirmedCheatingSignalDao,
    private val signalDecisionDao: SignalDecisionDao
) : LearningRepository {

    override suspend fun addScan(scan: Scan) {
        scanDao.insertScan(scan)
    }

    override suspend fun getBaseline(macAddress: String): Baseline? {
        return baselineDao.getBaseline(macAddress)
    }

    override suspend fun updateBaseline(baseline: Baseline) {
        baselineDao.upsertBaseline(baseline)
    }

    override suspend fun getRecentScansForDevice(macAddress: String, since: Long): List<Scan> {
        return scanDao.getRecentScansForDevice(macAddress, since)
    }

    override suspend fun addLearnedRule(rule: LearnedRule) {
        learnedRuleDao.insertRule(rule)
    }

    override suspend fun getAllLearnedRules(): List<LearnedRule> {
        return learnedRuleDao.getAllRules()
    }

    override suspend fun addFriendlySignal(signal: FriendlySignal) {
        friendlySignalDao.insertFriendly(signal)
    }

    override suspend fun getFriendlySignal(hash: String): FriendlySignal? {
        return friendlySignalDao.getFriendly(hash)
    }

    override suspend fun addCheatingSignal(signal: ConfirmedCheatingSignal) {
        confirmedCheatingSignalDao.insertCheating(signal)
    }

    override suspend fun getCheatingSignal(hash: String): ConfirmedCheatingSignal? {
        return confirmedCheatingSignalDao.getCheating(hash)
    }

    override suspend fun addDecisionHistory(decision: SignalDecision) {
        signalDecisionDao.insertDecision(decision)
    }

    override suspend fun getAllConfirmedCheatingSignals(): List<ConfirmedCheatingSignal> {
        return confirmedCheatingSignalDao.getAllCheating()
    }

    override suspend fun prepareFederatedPayload(): String {
        val signals = getAllConfirmedCheatingSignals()
        val root = JSONObject().apply {
            put("version", "ASTRA_V12_FEDERATED")
            put("timestamp", System.currentTimeMillis())
            put("nodeId", android.os.Build.MODEL)
            
            val signalArray = JSONArray()
            signals.forEach { signal ->
                val obj = JSONObject().apply {
                    put("type", signal.deviceType)
                    put("hash", signal.signalHash) // Anonymized via hashing already
                    put("rssi", signal.lastSeenRssi)
                    put("hits", signal.hitCount)
                }
                signalArray.put(obj)
            }
            put("aggregatedData", signalArray)
        }
        return root.toString()
    }
}
