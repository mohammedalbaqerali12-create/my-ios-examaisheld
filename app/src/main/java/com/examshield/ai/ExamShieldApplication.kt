package com.examshield.ai

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ExamShieldApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialization logic for crash reporting, logging base line environment can start here.
    }
}
