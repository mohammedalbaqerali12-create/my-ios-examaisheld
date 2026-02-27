package com.examshield.ai.data.scanner

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.examshield.ai.domain.model.DetectedObject
import com.examshield.ai.domain.repository.Scanner
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import kotlin.math.sqrt

class MagneticFieldScannerImpl @Inject constructor(
    private val context: Context
) : Scanner {

    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private val magnetometer: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    private var baselineMagneticField: Float? = null
    private val THRESHOLD = 10.0f // Micro-Tesla (µT) - Tweak this value based on testing

    override fun startScanning(): Flow<DetectedObject> = callbackFlow {
        if (magnetometer == null) {
            close()
            return@callbackFlow
        }

        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]

                    val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

                    if (baselineMagneticField == null) {
                        baselineMagneticField = magnitude
                    }

                    val delta = kotlin.math.abs(magnitude - baselineMagneticField!!)

                    if (delta > THRESHOLD) {
                        val detectedObj = DetectedObject(
                            macAddress = "MAGNETIC_FIELD_ANOMALY",
                            name = "Magnetic Anomaly Detected",
                            signalStrengthRssi = delta.toInt(),
                            isWifi = false,
                            isBle = false,
                            isClassicBluetooth = false,
                            timestampMs = System.currentTimeMillis()
                        )
                        trySend(detectedObj)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Not used
            }
        }

        sensorManager.registerListener(sensorListener, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)

        awaitClose {
            sensorManager.unregisterListener(sensorListener)
        }
    }

    override fun stopScanning() {
        // Handled by awaitClose
    }
}
