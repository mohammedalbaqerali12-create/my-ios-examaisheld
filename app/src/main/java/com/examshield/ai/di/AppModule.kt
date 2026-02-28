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
        ).fallbackToDestructiveMigration()
         .build()
    }

    @Provides
    @Singleton
    fun provideScanDao(database: AppDatabase): ScanDao = database.scanDao()

    @Provides
    @Singleton
    fun provideBaselineDao(database: AppDatabase): BaselineDao = database.baselineDao()

    @Provides
    @Singleton
    fun provideLearnedRuleDao(database: AppDatabase): com.examshield.ai.data.local.dao.LearnedRuleDao = database.learnedRuleDao()

    @Provides
    @Singleton
    fun provideFriendlySignalDao(database: AppDatabase): com.examshield.ai.data.local.dao.FriendlySignalDao = database.friendlySignalDao()

    @Provides
    @Singleton
    fun provideConfirmedCheatingSignalDao(database: AppDatabase): com.examshield.ai.data.local.dao.ConfirmedCheatingSignalDao = database.confirmedCheatingSignalDao()

    @Provides
    @Singleton
    fun provideSignalDecisionDao(database: AppDatabase): com.examshield.ai.data.local.dao.SignalDecisionDao = database.signalDecisionDao()

    @Provides
    @Singleton
    fun provideRoomModelingDao(database: AppDatabase): com.examshield.ai.data.local.dao.RoomModelingDao = database.roomModelingDao()
// Riverside: Registering RoomModelingDao for persistence.
    @Provides
    @Singleton
    fun provideLocalizationSessionController(): com.examshield.ai.session.LocalizationSessionController {
        return com.examshield.ai.session.LocalizationSessionController()
    }

    @Provides
    @Singleton
    fun provideEstimateDistanceUseCase(): EstimateDistanceUseCase {
        return EstimateDistanceUseCase()
    }

    @Provides
    @Singleton
    fun provideLearningRepository(
        scanDao: com.examshield.ai.data.local.dao.ScanDao,
        baselineDao: com.examshield.ai.data.local.dao.BaselineDao,
        learnedRuleDao: com.examshield.ai.data.local.dao.LearnedRuleDao,
        friendlySignalDao: com.examshield.ai.data.local.dao.FriendlySignalDao,
        confirmedCheatingSignalDao: com.examshield.ai.data.local.dao.ConfirmedCheatingSignalDao,
        signalDecisionDao: com.examshield.ai.data.local.dao.SignalDecisionDao
    ): com.examshield.ai.domain.repository.LearningRepository {
        return com.examshield.ai.data.repository.LearningRepositoryImpl(
            scanDao, 
            baselineDao, 
            learnedRuleDao, 
            friendlySignalDao, 
            confirmedCheatingSignalDao, 
            signalDecisionDao
        )
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
        adaptiveLearningEngine: com.examshield.ai.domain.ai.AdaptiveLearningEngine,
        aiIntelligenceService: com.examshield.ai.domain.ai.AiIntelligenceService
    ): DeviceClassifier {
        return TFLiteDeviceClassifierImpl(useCase, adaptiveLearningEngine, aiIntelligenceService)
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
    fun provideOrientationScanner(@ApplicationContext context: Context): com.examshield.ai.data.scanner.OrientationScannerImpl {
        return com.examshield.ai.data.scanner.OrientationScannerImpl(context)
    }

    @Provides
    @Singleton
    fun provideAiIntelligenceService(): com.examshield.ai.domain.ai.AiIntelligenceService {
        return com.examshield.ai.data.remote.OpenAiIntelligenceServiceImpl()
    }

    @Provides
    @Singleton
    fun provideDetectionService(
        @BluetoothLeScanner bleScanner: Scanner,
        @ClassicBluetoothScanner classicBluetoothScanner: Scanner,
        @WifiScanner wifiScanner: Scanner,
        @WifiDirectScanner wifiDirectScanner: Scanner,
        @MagneticFieldScanner magneticFieldScanner: Scanner,
        orientationScanner: com.examshield.ai.data.scanner.OrientationScannerImpl,
        classifier: DeviceClassifier
    ): DetectionService {
        return DetectionServiceImpl(
            bleScanner = bleScanner,
            classicBluetoothScanner = classicBluetoothScanner,
            wifiScanner = wifiScanner,
            wifiDirectScanner = wifiDirectScanner,
            magneticFieldScanner = magneticFieldScanner,
            orientationScanner = orientationScanner,
            classifier = classifier
        )
    }
}
