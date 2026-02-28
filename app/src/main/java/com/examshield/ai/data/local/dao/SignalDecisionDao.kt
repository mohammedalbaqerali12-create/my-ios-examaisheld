package com.examshield.ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.examshield.ai.data.local.model.SignalDecision

@Dao
interface SignalDecisionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDecision(decision: SignalDecision)

    @Query("SELECT * FROM signal_decision_history ORDER BY timestamp DESC")
    suspend fun getDecisionHistory(): List<SignalDecision>

    @Query("SELECT * FROM signal_decision_history WHERE signalHash = :hash")
    suspend fun getHistoryForSignal(hash: String): List<SignalDecision>
}
