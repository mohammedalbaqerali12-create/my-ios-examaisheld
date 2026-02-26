package com.examshield.ai.di

import android.content.Context
import androidx.room.Room
import com.examshield.ai.data.local.AppDatabase
import com.examshield.ai.data.local.dao.BaselineDao
import com.examshield.ai.data.local.dao.ScanDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "examshield_ai_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideScanDao(database: AppDatabase): ScanDao = database.scanDao()

    @Provides
    fun provideBaselineDao(database: AppDatabase): BaselineDao = database.baselineDao()
}
