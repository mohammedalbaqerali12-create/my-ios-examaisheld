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
    
    @Volatile
    var currentPitch: Float = 0f

    /**
     * Continuous stream of compass heading (degrees) and pitch (degrees).
     */
    fun observeOrientation(): Flow<Pair<Float, Float>> = callbackFlow {
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
                    val r = FloatArray(9)
                    val outR = FloatArray(9)
                    if (SensorManager.getRotationMatrix(r, null, lastAccelerometer, lastMagnetometer)) {
                        // Remap coordinate system for Portrait/Vertical AR usage
                        // World X -> Device X, World Y -> Device Z (Forward)
                        SensorManager.remapCoordinateSystem(
                            r,
                            SensorManager.AXIS_X,
                            SensorManager.AXIS_Z,
                            outR
                        )
                        SensorManager.getOrientation(outR, orientationAngles)
                        
                        // Azimuth is orientationAngles[0]
                        var azimuthDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                        if (azimuthDegrees < 0) azimuthDegrees += 360f
                        
                        // Pitch is orientationAngles[1]
                        val pitchDegrees = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                        
                        currentAzimuth = azimuthDegrees
                        currentPitch = pitchDegrees
                        trySend(Pair(azimuthDegrees, pitchDegrees))
                    }
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
