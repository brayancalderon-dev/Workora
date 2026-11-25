package com.workora.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.workora.app.data.local.entity.HiringRoomJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HiringRoomDao {
    @Query("SELECT * FROM hiring_room_jobs WHERE isDismissed = 0 AND id NOT IN ('hr_101', 'hr_102', 'hr_103', 'hr_104') ORDER BY matchScore DESC")
    fun getActiveJobs(): Flow<List<HiringRoomJobEntity>>

    @Query("SELECT * FROM hiring_room_jobs WHERE isFavorite = 1 AND id NOT IN ('hr_101', 'hr_102', 'hr_103', 'hr_104') ORDER BY matchScore DESC")
    fun getFavoriteJobs(): Flow<List<HiringRoomJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobs(jobs: List<HiringRoomJobEntity>)

    @Query("UPDATE hiring_room_jobs SET isFavorite = :isFavorite WHERE id = :jobId")
    suspend fun updateFavoriteStatus(jobId: String, isFavorite: Boolean)

    @Query("UPDATE hiring_room_jobs SET isDismissed = :isDismissed WHERE id = :jobId")
    suspend fun updateDismissedStatus(jobId: String, isDismissed: Boolean)

    @Query("DELETE FROM hiring_room_jobs")
    suspend fun clearAll()

    @Query("DELETE FROM hiring_room_jobs WHERE id IN ('hr_101', 'hr_102', 'hr_103', 'hr_104')")
    suspend fun clearBundledSampleJobs()
}
