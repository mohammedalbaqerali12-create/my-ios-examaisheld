package com.examshield.ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.examshield.ai.data.local.model.FriendlySignal

@Dao
interface FriendlySignalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriendly(signal: FriendlySignal)

    @Query("SELECT * FROM friendly_signals WHERE signalHash = :hash")
    suspend fun getFriendly(hash: String): FriendlySignal?

    @Query("SELECT * FROM friendly_signals")
    suspend fun getAllFriendly(): List<FriendlySignal>
}
