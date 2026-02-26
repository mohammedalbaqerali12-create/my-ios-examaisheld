package com.examshield.ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.examshield.ai.data.local.entity.BaselineEntity

@Dao
interface BaselineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBaselineDevice(device: BaselineEntity)

    @Query("SELECT COUNT(*) FROM calibration_baseline WHERE macAddress = :mac")
    suspend fun isDeviceInBaseline(mac: String): Int
}
