package com.examshield.ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.examshield.ai.data.local.model.Scan
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: Scan)

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<Scan>>

    @Query("SELECT * FROM scan_history WHERE macAddress = :macAddress AND timestamp >= :since")
    suspend fun getRecentScansForDevice(macAddress: String, since: Long): List<Scan>
    
    @Query("DELETE FROM scan_history WHERE timestamp < :time")
    suspend fun pruneOldScans(time: Long)
}
