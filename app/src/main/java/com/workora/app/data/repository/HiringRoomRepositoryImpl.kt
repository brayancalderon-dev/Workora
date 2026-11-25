package com.workora.app.data.repository

import com.workora.app.data.local.dao.HiringRoomDao
import com.workora.app.data.local.entity.HiringRoomJobEntity
import com.workora.app.data.remote.hiringroom.HiringRoomCompany
import com.workora.app.data.remote.hiringroom.HiringRoomVacancySource
import com.workora.app.domain.repository.HiringRoomRepository
import com.workora.app.domain.usecase.AnalyzeJobCompatibilityUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class HiringRoomRepositoryImpl @Inject constructor(
    private val dao: HiringRoomDao,
    private val analyzeUseCase: AnalyzeJobCompatibilityUseCase
) : HiringRoomRepository {

    override fun getActiveJobs(): Flow<List<HiringRoomJobEntity>> {
        return dao.getActiveJobs()
    }

    override fun getFavoriteJobs(): Flow<List<HiringRoomJobEntity>> {
        return dao.getFavoriteJobs()
    }

    override suspend fun performSearch(userCvSummary: String?, companies: List<HiringRoomCompany>): List<HiringRoomJobEntity> {
        return withContext(Dispatchers.IO) {
            val rawVacancies = HiringRoomVacancySource.fetch(companies)
            dao.clearBundledSampleJobs()
            val analyzedJobs = rawVacancies.map { rawJob ->
                analyzeUseCase.execute(rawJob, userCvSummary)
            }
            dao.insertJobs(analyzedJobs)
            analyzedJobs
        }
    }

    override suspend fun toggleFavorite(jobId: String, isFavorite: Boolean) {
        dao.updateFavoriteStatus(jobId, isFavorite)
    }

    override suspend fun toggleDismissed(jobId: String, isDismissed: Boolean) {
        dao.updateDismissedStatus(jobId, isDismissed)
    }
}
