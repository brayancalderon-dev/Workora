package com.workora.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.workora.app.data.local.entity.VacancyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VacancyDao {
    @Query("SELECT * FROM vacancies ORDER BY timestamp DESC")
    fun getAllVacancies(): Flow<List<VacancyEntity>>

    @Insert
    suspend fun insertVacancy(vacancy: VacancyEntity): Long

    @Query("UPDATE vacancies SET jobTitle = :title, companyName = :company, matchScore = :score, pros = :pros, contras = :contras, analysisSummary = :summary, analysisStatus = :status WHERE id = :id")
    suspend fun updateAnalysis(id: Int, title: String, company: String?, score: Int, pros: String, contras: String, summary: String, status: String)

    @Query("DELETE FROM vacancies WHERE id = :id")
    suspend fun deleteVacancy(id: Int)
}
