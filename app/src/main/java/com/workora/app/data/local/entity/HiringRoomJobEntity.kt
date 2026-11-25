package com.workora.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hiring_room_jobs")
data class HiringRoomJobEntity(
    @PrimaryKey
    val id: String, // Unique job ID or hash
    val title: String,
    val companyName: String,
    val companyLogoUrl: String? = null,
    val location: String,
    val workMode: String, // Remote, Hybrid, On-site
    val level: String, // Senior, Semi-Senior, Junior, Lead
    val description: String,
    val requirements: String,
    val applyUrl: String,
    val matchScore: Int,
    val category: String, // Software, Data, DB, DevOps, QA, Other
    val isRecommended: Boolean, // Score >= 75
    val isFavorite: Boolean = false,
    val isDismissed: Boolean = false,
    val postedDate: String? = null,
    val daysAgo: Int = 1,
    val salary: String? = null,
    val aiWhyFit: String,
    val aiMatches: String, // Pipe-separated list
    val aiGaps: String, // Pipe-separated list
    val aiConclusion: String,
    val techScore: Int = 85,
    val expScore: Int = 80,
    val funcScore: Int = 90,
    val timestamp: Long = System.currentTimeMillis()
)