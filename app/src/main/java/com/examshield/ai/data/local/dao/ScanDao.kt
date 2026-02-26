package com.examshield.ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.examshield.ai.data.local.entity.ScanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Insert
    suspend fun insertScan(scan: ScanEntity)

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scan_history WHERE timestamp > :startTime")
    suspend fun getRecentScans(startTime: Long): List<ScanEntity>

    // Learning module query: get counts for confirmed patterns
    @Query("SELECT COUNT(*) FROM scan_history WHERE macAddress = :mac AND isConfirmedBySupervisor = 1")
    suspend fun getConfirmationCount(mac: String): Int
}
