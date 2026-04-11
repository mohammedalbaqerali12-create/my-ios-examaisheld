package com.examshield.ai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.examshield.ai.MainActivity
import com.examshield.ai.domain.model.ClassificationResult
import com.examshield.ai.domain.repository.DetectionService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AstraNexusService : Service() {

    @Inject
    lateinit var detectionService: DetectionService

    @Inject
    lateinit var performanceAdvisor: com.examshield.ai.domain.ai.AIPerformanceAdvisor

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private val lastCallbackTime = System.currentTimeMillis()
    private var callbackCount = 0

    companion object {
        private const val CHANNEL_ID = "ASTRA_NEXUS_CHANNEL"
        private const val NOTIFICATION_ID = 1001
        
        private val _staticDetectionStream = MutableSharedFlow<ClassificationResult>(
            replay = 5, 
            extraBufferCapacity = 100,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
        )
        val detectionStream = _staticDetectionStream.asSharedFlow()
        
        fun start(context: Context) {
            try {
                // Set permanent background flag
                val prefs = context.getSharedPreferences("shield_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("permanent_background", true).apply()

                val intent = Intent(context, AstraNexusService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                android.util.Log.e("ASTRA_NEXUS", "Failed to start service: ${e.message}")
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, AstraNexusService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID, 
                    createNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or 
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Exception) {
            android.util.Log.e("ASTRA_NEXUS", "Error starting foreground: ${e.message}")
            // Fallback: try starting without types if types fail
            try { startForeground(NOTIFICATION_ID, createNotification()) } catch(e2: Exception) {}
        }
        startScanning()
    }

    private var lastCheckTime = System.currentTimeMillis()
    private var callbacksInLastSecond = 0
    private var totalCallbackCount = 0

    private fun startScanning() {
        android.util.Log.d("ASTRA_NEXUS", "Service Pipeline INITIALIZING...")
        serviceScope.launch {
            try {
                detectionService.observeThreats().collect { result ->
                    callbacksInLastSecond++
                    totalCallbackCount++
                    _staticDetectionStream.tryEmit(result)
                    
                    val now = System.currentTimeMillis()
                    if (now - lastCheckTime >= 1000) {
                        performanceAdvisor.updateMetrics(
                            refreshRate = 60,
                            callbackFrequency = callbacksInLastSecond,
                            bluetoothEnabled = true,
                            rssiVariance = 5.0
                        )
                        
                        android.util.Log.d("ASTRA_NEXUS", "Pipeline Frequency: $callbacksInLastSecond callbacks/sec")
                        
                        callbacksInLastSecond = 0
                        lastCheckTime = now
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ASTRA_NEXUS", "Stream error: ${e.message}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // If we are in permanent mode, we might want to restart ourselves
        val prefs = getSharedPreferences("shield_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("permanent_background", false)) {
            val intent = Intent(this, AstraNexusService::class.java)
            sendBroadcast(Intent("RESTART_ASTRA_NEXUS")) // Potential trigger for a receiver
        }
        serviceJob.cancel()
        detectionService.stop()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Ensure service stays alive even if app is swiped away
        val prefs = getSharedPreferences("shield_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("permanent_background", false)) {
            val restartServiceIntent = Intent(applicationContext, this.javaClass)
            restartServiceIntent.setPackage(packageName)
            startService(restartServiceIntent)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Astra Nexus Active Tracking",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ASTRA NEXUS ACTIVE")
            .setContentText("Continuous Scanning & AI Tracking Pipeline Active")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .build()
    }
}
