package com.examshield.ai.data.repository

import com.examshield.ai.data.local.dao.*
import com.examshield.ai.data.local.model.*
import com.examshield.ai.domain.repository.LearningRepository
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
}
