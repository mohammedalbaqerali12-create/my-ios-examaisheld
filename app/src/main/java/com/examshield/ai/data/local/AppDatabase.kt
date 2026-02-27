package com.examshield.ai.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.examshield.ai.data.local.dao.BaselineDao
import com.examshield.ai.data.local.dao.ScanDao
import com.examshield.ai.data.local.model.Baseline
import com.examshield.ai.data.local.model.Scan

@Database(entities = [Scan::class, Baseline::class], version = 1, exportSchema = false)
public abstract class AppDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
    abstract fun baselineDao(): BaselineDao
}
