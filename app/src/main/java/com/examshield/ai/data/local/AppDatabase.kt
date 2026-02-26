package com.examshield.ai.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.examshield.ai.data.local.dao.BaselineDao
import com.examshield.ai.data.local.dao.ScanDao
import com.examshield.ai.data.local.entity.BaselineEntity
import com.examshield.ai.data.local.entity.ScanEntity

// In a real implementation this would use SQLCipher to be encrypted
@Database(
    entities = [ScanEntity::class, BaselineEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
    abstract fun baselineDao(): BaselineDao
}
