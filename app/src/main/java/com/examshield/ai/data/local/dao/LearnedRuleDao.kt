package com.examshield.ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.examshield.ai.data.local.model.LearnedRule

@Dao
interface LearnedRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: LearnedRule)

    @Query("SELECT * FROM learned_rules")
    suspend fun getAllRules(): List<LearnedRule>
}
