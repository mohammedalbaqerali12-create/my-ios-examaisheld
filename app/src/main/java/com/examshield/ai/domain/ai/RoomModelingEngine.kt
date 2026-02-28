package com.examshield.ai.domain.ai

import com.examshield.ai.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*
import androidx.compose.ui.geometry.Offset

@Singleton
class RoomModelingEngine @Inject constructor() {
    private val _currentRoom = MutableStateFlow(RoomProfile())
    val currentRoom = _currentRoom.asStateFlow()

    private val _seatGrid = MutableStateFlow<List<SeatPosition>>(emptyList())
    val seatGrid = _seatGrid.asStateFlow()

    fun updateRoomDimensions(length: Float, width: Float) {
        _currentRoom.value = _currentRoom.value.copy(lengthMeters = length, widthMeters = width)
        generateSeatGrid()
    }

    fun generateSeatGrid(config: SeatGridConfig = SeatGridConfig()) {
        val seats = mutableListOf<SeatPosition>()
        for (r in 0 until config.rows) {
            for (s in 0 until config.seatsPerRow) {
                seats.add(
                    SeatPosition(
                        id = "S_${r}_${s}",
                        row = r,
                        seatNumber = s,
                        x = s * config.seatSpacingMeters + (if (s > config.seatsPerRow / 2) config.aisleSpacingMeters else 0f),
                        y = r * config.seatSpacingMeters
                    )
                )
            }
        }
        _seatGrid.value = seats
    }

    /**
     * Adjusted Log-Distance Path Loss Model
     * d = 10 ^ ((Measured Power - RSSI) / (10 * n))
     */
    fun adjustDistanceByEnvironment(rawDistance: Double, measuredRssi: Int): Double {
        val n = _currentRoom.value.calibrationCoefficient
        val txPowerAt1m = -59 // Reference power at 1m
        
        val adjustedDistance = 10.0.pow((txPowerAt1m - measuredRssi) / (10.0 * n))
        
        // Constraint logic: distance cannot exceed room diagonal + margin
        val maxDiagonal = kotlin.math.sqrt(_currentRoom.value.lengthMeters.pow(2) + _currentRoom.value.widthMeters.pow(2))
        return adjustedDistance.coerceAtMost(maxDiagonal.toDouble() * 1.2)
    }

    fun getProbableSeats(x: Float, y: Float, distance: Double): List<Pair<SeatPosition, Double>> {
        if (_seatGrid.value.isEmpty()) return emptyList()
        
        // P(seat) = f(distance error)
        // We use a Gaussian distribution: exp(-error^2 / 2*sigma^2)
        val sigma = 1.5 // meters
        
        return _seatGrid.value.map { seat ->
            val seatDist = kotlin.math.sqrt((seat.x - x).pow(2) + (seat.y - y).pow(2)).toDouble()
            val error = Math.abs(seatDist - distance)
            val probability = Math.exp(-(error.pow(2)) / (2 * sigma.pow(2)))
            seat to probability
        }.filter { it.second > 0.1 }
         .sortedByDescending { it.second }
         .take(3)
    }

    /**
     * Human-Body Absorption Compensation
     * If the operator (supervisor) orientation shows the phone is between 
     * the source and the receiver, we add a correction dBm.
     */
    fun compensateAbsorption(rssi: Int, isOperatorBlocking: Boolean): Int {
        return if (isOperatorBlocking) rssi + 3 else rssi // ~3dBm for human body loss
    }

    /**
     * RSSI Error Surface Minimization (Micro-Trilateration Placeholder)
     * Finds the X,Y coordinate in the room that minimizes the squared error
     * between predicted RSSI and observed RSSI from multiple viewpoints.
     */
    fun minimizeErrorPosition(observations: List<RssiObservation>): Offset {
        // Grid search on the room surface (sampled every 0.5m)
        var bestX = _currentRoom.value.widthMeters / 2
        var bestY = _currentRoom.value.lengthMeters / 2
        var minError = Double.MAX_VALUE

        val step = 0.5f
        var currentX = 0f
        while (currentX <= _currentRoom.value.widthMeters) {
            var currentY = 0f
            while (currentY <= _currentRoom.value.lengthMeters) {
                var totalError = 0.0
                observations.forEach { obs ->
                    val dist = sqrt((currentX - obs.x).pow(2) + (currentY - obs.y).pow(2))
                    val predictedRssi = -59 - (10 * _currentRoom.value.calibrationCoefficient * Math.log10(dist.toDouble().coerceAtLeast(0.1)))
                    totalError += (predictedRssi - obs.rssi).pow(2)
                }
                
                if (totalError < minError) {
                    minError = totalError
                    bestX = currentX
                    bestY = currentY
                }
                currentY += step
            }
            currentX += step
        }
        return Offset(bestX, bestY)
    }

    fun getProbableSeat(x: Float, y: Float): SeatPosition? {
        return _seatGrid.value.minByOrNull { seat ->
            (seat.x - x).pow(2) + (seat.y - y).pow(2)
        }
    }

    /**
     * Calibration Phase: Capture baseline and adjust 'n' factor
     */
    fun performCalibration(baselineRssi: Int) {
        // Ideal at 1m is -59. If measured at 1m is different, adjust.
        // n = (Measured - RSSI) / (10 * log10(d))
        // Here we simplify: if signal is weaker than expected, increase attenuation factor
        val expectedAt1m = -59
        val diff = Math.abs(expectedAt1m - baselineRssi)
        
        val newN = 2.0f + (diff / 20.0f).coerceIn(0f, 2.5f)
        _currentRoom.value = _currentRoom.value.copy(calibrationCoefficient = newN)
        
        android.util.Log.d("ROOM_MODEL", "Calibration complete. New Path Loss Exponent: $newN")
    }
    // Riverside: Calibration system for hall-specific attenuation.
}

data class RssiObservation(val x: Float, val y: Float, val rssi: Double)
