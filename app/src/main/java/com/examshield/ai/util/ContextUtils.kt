package com.examshield.ai.util

import android.content.Context

object ContextUtils {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun getAppContext(): Context {
        return appContext ?: throw IllegalStateException("ContextUtils not initialized")
    }
}
