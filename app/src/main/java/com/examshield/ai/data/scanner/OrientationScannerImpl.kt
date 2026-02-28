package com.examshield.ai.data.scanner

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class OrientationScannerImpl @Inject constructor(
    private val context: Context
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    
    @Volatile
    var currentAzimuth: Float = 0f

    /**
     * Continuous stream of compass heading (degrees).
     */
    fun observeOrientation(): Flow<Float> = callbackFlow {
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        val lastAccelerometer = FloatArray(3)
        val lastMagnetometer = FloatArray(3)
        var lastAccelerometerSet = false
        var lastMagnetometerSet = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    System.arraycopy(event.values, 0, lastAccelerometer, 0, 3)
                    lastAccelerometerSet = true
                } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    System.arraycopy(event.values, 0, lastMagnetometer, 0, 3)
                    lastMagnetometerSet = true
                }

                if (lastAccelerometerSet && lastMagnetometerSet) {
                    SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelerometer, lastMagnetometer)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    val degrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                    currentAzimuth = degrees
                    trySend(degrees)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    /**
     * Discrete stream of "Steps" emitting the azimuth at each step.
     */
    fun observeSteps(): Flow<Float> = callbackFlow {
        var lastStepTime = 0L
        val minDelay = 400L // Approx humans pace
        val stepThreshold = 12.5f // Above gravity (9.8)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val magnitude = Math.sqrt(
                        (event.values[0] * event.values[0] + 
                         event.values[1] * event.values[1] + 
                         event.values[2] * event.values[2]).toDouble()
                    ).toFloat()

                    val now = System.currentTimeMillis()
                    if (magnitude > stepThreshold && (now - lastStepTime) > minDelay) {
                        lastStepTime = now
                        trySend(currentAzimuth)
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
