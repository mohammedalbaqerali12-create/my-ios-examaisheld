package com.examshield.ai.data.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.examshield.ai.domain.repository.OrbitalData
import com.examshield.ai.domain.repository.OrbitalUplink
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

class OrbitalUplinkServiceImpl @Inject constructor(
    private val context: Context
) : OrbitalUplink {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val _currentOrbitalState = MutableStateFlow(OrbitalData())

    override fun streamOrbitalData(): Flow<OrbitalData> = callbackFlow {
        if (locationManager == null || !hasLocationPermissions()) {
            trySend(OrbitalData(isSecure = false))
            close()
            return@callbackFlow
        }

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                _currentOrbitalState.update { current ->
                    current.copy(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitude = location.altitude,
                        speed = location.speed,
                        isSecure = true
                    )
                }
                trySend(_currentOrbitalState.value)
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                _currentOrbitalState.update { it.copy(isSecure = false) }
                trySend(_currentOrbitalState.value)
            }
        }

        val gnssCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var usedInFixCount = 0
                for (i in 0 until status.satelliteCount) {
                    if (status.usedInFix(i)) {
                        usedInFixCount++
                    }
                }
                _currentOrbitalState.update { it.copy(satelliteCount = usedInFixCount) }
                trySend(_currentOrbitalState.value)
            }
        }

        try {
            // Request location updates (High frequency for "Sci-Fi" feel)
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L, // 1 second
                0f,    // 0 meters (any movement)
                locationListener,
                Looper.getMainLooper()
            )

            // Request GNSS active satellites payload
            locationManager.registerGnssStatusCallback(
                context.mainExecutor,
                gnssCallback
            )
        } catch (e: SecurityException) {
            // Permissions lost
            trySend(OrbitalData(isSecure = false))
        }

        awaitClose {
            locationManager.removeUpdates(locationListener)
            locationManager.unregisterGnssStatusCallback(gnssCallback)
        }
    }

    override suspend fun requestOrbitalStamp(): OrbitalData {
        return _currentOrbitalState.value
    }

    private fun hasLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
