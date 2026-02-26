package com.examshield.ai.di

import android.content.Context
import com.examshield.ai.data.repository.DetectionServiceImpl
import com.examshield.ai.data.scanner.BleScannerImpl
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
annotation class BleScanner

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WifiScanner

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

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
    @BleScanner
    fun provideBleScanner(@ApplicationContext context: Context): Scanner {
        return BleScannerImpl(context)
    }

    @Provides
    @Singleton
    @WifiScanner
    fun provideWifiScanner(@ApplicationContext context: Context): Scanner {
        return WifiScannerImpl(context)
    }

    @Provides
    @Singleton
    fun provideDetectionService(
        @BleScanner bleScanner: Scanner,
        @WifiScanner wifiScanner: Scanner,
        classifier: DeviceClassifier
    ): DetectionService {
        return DetectionServiceImpl(bleScanner, wifiScanner, classifier)
    }
}
