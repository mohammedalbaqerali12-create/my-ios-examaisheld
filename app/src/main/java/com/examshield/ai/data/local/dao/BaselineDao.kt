package com.examshield.ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.examshield.ai.data.local.model.Baseline

@Dao
interface BaselineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBaseline(baseline: Baseline)

    @Query("SELECT * FROM environment_baseline WHERE macAddress = :macAddress")
    suspend fun getBaseline(macAddress: String): Baseline?

    @Query("SELECT * FROM environment_baseline ORDER BY lastSeen DESC")
    suspend fun getAllBaselines(): List<Baseline>
}
