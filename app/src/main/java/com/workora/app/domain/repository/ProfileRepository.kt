package com.workora.app.domain.repository

import com.workora.app.domain.model.Profile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun getProfile(): Flow<Profile?>
    suspend fun saveProfile(profile: Profile)
}