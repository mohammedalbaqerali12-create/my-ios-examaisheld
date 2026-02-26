package com.examshield.ai.domain.repository

interface LearningRepository {
    /**
     * Retrieves the number of times a specific MAC address was confirmed as a cheating device by a supervisor.
     */
    suspend fun getSupervisorConfirmationCount(macAddress: String): Int

    /**
     * Checks if the device was recorded during the empty-hall calibration phase.
     * Devices present before students arrive are usually safe (e.g., ceiling APs, projectors).
     */
    suspend fun isDeviceInCalibrationBaseline(macAddress: String): Boolean

    /**
     * Marks a device as part of the baseline environmental noise.
     */
    suspend fun addDeviceToBaseline(macAddress: String)
}
