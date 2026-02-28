package com.examshield.ai.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.examshield.ai.data.local.dao.BaselineDao
import com.examshield.ai.data.local.dao.ScanDao
import com.examshield.ai.data.local.dao.LearnedRuleDao
import com.examshield.ai.data.local.dao.FriendlySignalDao
import com.examshield.ai.data.local.dao.ConfirmedCheatingSignalDao
import com.examshield.ai.data.local.dao.SignalDecisionDao
import com.examshield.ai.data.local.model.Baseline
import com.examshield.ai.data.local.model.Scan
import com.examshield.ai.data.local.model.LearnedRule
import com.examshield.ai.data.local.model.FriendlySignal
import com.examshield.ai.data.local.model.ConfirmedCheatingSignal
import com.examshield.ai.data.local.model.SignalDecision
import com.examshield.ai.data.local.entity.*

@Database(
    entities = [
        Scan::class, Baseline::class, LearnedRule::class,
        FriendlySignal::class, ConfirmedCheatingSignal::class,
        SignalDecision::class, RoomProfileEntity::class,
        SeatGridConfigEntity::class, CalibrationProfileEntity::class,
        ActiveTaskEntity::class
    ],
    version = 4,
    exportSchema = false
)
public abstract class AppDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
    abstract fun baselineDao(): BaselineDao
    abstract fun learnedRuleDao(): LearnedRuleDao
    abstract fun friendlySignalDao(): FriendlySignalDao
    abstract fun confirmedCheatingSignalDao(): ConfirmedCheatingSignalDao
    abstract fun signalDecisionDao(): SignalDecisionDao
    abstract fun roomModelingDao(): com.examshield.ai.data.local.dao.RoomModelingDao
}
// Riverside: Integrated Room Modeling into Core DB.
