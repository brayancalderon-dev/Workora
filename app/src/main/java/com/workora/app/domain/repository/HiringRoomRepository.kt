package com.workora.app.domain.repository

import com.workora.app.data.local.entity.HiringRoomJobEntity
import com.workora.app.data.remote.hiringroom.HiringRoomCompany
import kotlinx.coroutines.flow.Flow

interface HiringRoomRepository {
    fun getActiveJobs(): Flow<List<HiringRoomJobEntity>>
    fun getFavoriteJobs(): Flow<List<HiringRoomJobEntity>>
    suspend fun performSearch(userCvSummary: String?, companies: List<HiringRoomCompany>): List<HiringRoomJobEntity>
    suspend fun toggleFavorite(jobId: String, isFavorite: Boolean)
    suspend fun toggleDismissed(jobId: String, isDismissed: Boolean)
}
