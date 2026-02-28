package com.examshield.ai.data.local.dao

import androidx.room.*
import com.examshield.ai.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomModelingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoomProfile(profile: RoomProfileEntity)

    @Query("SELECT * FROM room_profiles ORDER BY timestamp DESC")
    fun getAllRoomProfiles(): Flow<List<RoomProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeatGridConfig(config: SeatGridConfigEntity)

    @Query("SELECT * FROM seat_grid_configs WHERE roomId = :roomId")
    suspend fun getSeatGridConfig(roomId: String): SeatGridConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalibrationProfile(profile: CalibrationProfileEntity)

    @Query("SELECT * FROM calibration_profiles WHERE roomId = :roomId ORDER BY timestamp DESC LIMIT 1")
    fun getLatestCalibration(roomId: String): Flow<CalibrationProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun logActiveTask(task: ActiveTaskEntity)

    @Query("SELECT * FROM active_tasks_history ORDER BY timestamp DESC LIMIT 10")
    fun getRecentTaskHistory(): Flow<List<ActiveTaskEntity>>
}
