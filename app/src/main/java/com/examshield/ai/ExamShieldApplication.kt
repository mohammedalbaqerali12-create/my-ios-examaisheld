package com.examshield.ai

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ExamShieldApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.examshield.ai.util.ContextUtils.init(this)
    }
}
