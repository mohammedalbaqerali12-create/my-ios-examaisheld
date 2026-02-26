package com.examshield.ai.data.repository

import com.examshield.ai.data.local.dao.BaselineDao
import com.examshield.ai.data.local.dao.ScanDao
import com.examshield.ai.data.local.entity.BaselineEntity
import com.examshield.ai.domain.repository.LearningRepository
import javax.inject.Inject

class LearningRepositoryImpl @Inject constructor(
    private val scanDao: ScanDao,
    private val baselineDao: BaselineDao
) : LearningRepository {

    override suspend fun getSupervisorConfirmationCount(macAddress: String): Int {
        return scanDao.getConfirmationCount(macAddress)
    }

    override suspend fun isDeviceInCalibrationBaseline(macAddress: String): Boolean {
        return baselineDao.isDeviceInBaseline(macAddress) > 0
    }

    override suspend fun addDeviceToBaseline(macAddress: String) {
        baselineDao.insertBaselineDevice(
            BaselineEntity(macAddress = macAddress, initialRssi = -100) // basic implementation
        )
    }
}
