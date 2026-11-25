package com.workora.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.workora.app.domain.model.Profile

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey
    val id: Int = 1,
    val name: String,
    val summary: String,
    val pdfFileName: String? = null,
    val lastUpdated: Long
)

fun ProfileEntity.toDomain() = Profile(
    id = id,
    name = name,
    summary = summary,
    pdfFileName = pdfFileName,
    lastUpdated = lastUpdated
)

fun Profile.toEntity() = ProfileEntity(
    id = id,
    name = name,
    summary = summary,
    pdfFileName = pdfFileName,
    lastUpdated = lastUpdated
)