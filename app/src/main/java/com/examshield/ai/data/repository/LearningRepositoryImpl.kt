package com.examshield.ai.data.repository

import com.examshield.ai.data.local.dao.BaselineDao
import com.examshield.ai.data.local.dao.ScanDao
import com.examshield.ai.data.local.model.Baseline
import com.examshield.ai.data.local.model.Scan
import com.examshield.ai.domain.repository.LearningRepository
import javax.inject.Inject

class LearningRepositoryImpl @Inject constructor(
    private val scanDao: ScanDao,
    private val baselineDao: BaselineDao
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
}
