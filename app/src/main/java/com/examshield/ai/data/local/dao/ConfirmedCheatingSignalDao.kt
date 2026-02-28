package com.examshield.ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.examshield.ai.data.local.model.ConfirmedCheatingSignal

@Dao
interface ConfirmedCheatingSignalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheating(signal: ConfirmedCheatingSignal)

    @Query("SELECT * FROM confirmed_cheating_signals WHERE signalHash = :hash")
    suspend fun getCheating(hash: String): ConfirmedCheatingSignal?

    @Query("SELECT * FROM confirmed_cheating_signals")
    suspend fun getAllCheating(): List<ConfirmedCheatingSignal>
}
