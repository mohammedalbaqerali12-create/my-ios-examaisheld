package com.examshield.ai.di

import android.content.Context
import androidx.room.Room
import com.examshield.ai.data.local.AppDatabase
import com.examshield.ai.data.local.dao.BaselineDao
import com.examshield.ai.data.local.dao.ScanDao
import com.examshield.ai.data.repository.DetectionServiceImpl
import com.examshield.ai.data.scanner.BleScannerImpl
import com.examshield.ai.data.scanner.ClassicBluetoothScannerImpl
import com.examshield.ai.data.scanner.MagneticFieldScannerImpl
import com.examshield.ai.data.scanner.WifiDirectScannerImpl
import com.examshield.ai.data.scanner.WifiScannerImpl
import com.examshield.ai.domain.ai.DeviceClassifier
import com.examshield.ai.domain.ai.TFLiteDeviceClassifierImpl
import com.examshield.ai.domain.repository.DetectionService
import com.examshield.ai.domain.repository.Scanner
import com.examshield.ai.domain.usecase.EstimateDistanceUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BluetoothLeScanner

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ClassicBluetoothScanner

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WifiScanner

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WifiDirectScanner

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MagneticFieldScanner

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "examshield-ai-db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideScanDao(database: AppDatabase): ScanDao = database.scanDao()

    @Provides
    @Singleton
    fun provideBaselineDao(database: AppDatabase): BaselineDao = database.baselineDao()

    @Provides
    @Singleton
    fun provideEstimateDistanceUseCase(): EstimateDistanceUseCase {
        return EstimateDistanceUseCase()
    }

    @Provides
    @Singleton
    fun provideLearningRepository(
        scanDao: com.examshield.ai.data.local.dao.ScanDao,
        baselineDao: com.examshield.ai.data.local.dao.BaselineDao
    ): com.examshield.ai.domain.repository.LearningRepository {
        return com.examshield.ai.data.repository.LearningRepositoryImpl(scanDao, baselineDao)
    }

    @Provides
    @Singleton
    fun provideAdaptiveLearningEngine(
        learningRepository: com.examshield.ai.domain.repository.LearningRepository
    ): com.examshield.ai.domain.ai.AdaptiveLearningEngine {
        return com.examshield.ai.domain.ai.AdaptiveLearningEngine(learningRepository)
    }

    @Provides
    @Singleton
    fun provideDeviceClassifier(
        useCase: EstimateDistanceUseCase,
        adaptiveLearningEngine: com.examshield.ai.domain.ai.AdaptiveLearningEngine
    ): DeviceClassifier {
        return TFLiteDeviceClassifierImpl(useCase, adaptiveLearningEngine)
    }

    @Provides
    @Singleton
    @BluetoothLeScanner
    fun provideBleScanner(@ApplicationContext context: Context): Scanner {
        return BleScannerImpl(context)
    }

    @Provides
    @Singleton
    @ClassicBluetoothScanner
    fun provideClassicBluetoothScanner(@ApplicationContext context: Context): Scanner {
        return ClassicBluetoothScannerImpl(context)
    }

    @Provides
    @Singleton
    @WifiScanner
    fun provideWifiScanner(@ApplicationContext context: Context): Scanner {
        return WifiScannerImpl(context)
    }

    @Provides
    @Singleton
    @WifiDirectScanner
    fun provideWifiDirectScanner(@ApplicationContext context: Context): Scanner {
        return WifiDirectScannerImpl(context)
    }

    @Provides
    @Singleton
    @MagneticFieldScanner
    fun provideMagneticFieldScanner(@ApplicationContext context: Context): Scanner {
        return MagneticFieldScannerImpl(context)
    }

    @Provides
    @Singleton
    fun provideDetectionService(
        @BluetoothLeScanner bleScanner: Scanner,
        @ClassicBluetoothScanner classicBluetoothScanner: Scanner,
        @WifiScanner wifiScanner: Scanner,
        @WifiDirectScanner wifiDirectScanner: Scanner,
        @MagneticFieldScanner magneticFieldScanner: Scanner,
        classifier: DeviceClassifier
    ): DetectionService {
        return DetectionServiceImpl(
            bleScanner = bleScanner,
            classicBluetoothScanner = classicBluetoothScanner,
            wifiScanner = wifiScanner,
            wifiDirectScanner = wifiDirectScanner,
            magneticFieldScanner = magneticFieldScanner,
            classifier = classifier
        )
    }
}
