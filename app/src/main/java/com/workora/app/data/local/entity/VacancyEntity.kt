package com.workora.app.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "vacancies")
data class VacancyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val jobTitle: String,
    val matchScore: Int,
    val pros: String,
    val contras: String,
    val imageUri: String? = null,
    val extractedEmail: String? = null,
    val companyName: String? = null,
    @ColumnInfo(defaultValue = "''")
    val analysisSummary: String = "",
    @ColumnInfo(defaultValue = "'Analizado'")
    val analysisStatus: String = "Analizado",
    val timestamp: Long = System.currentTimeMillis()
)
