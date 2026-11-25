package com.workora.app.data.repository

import com.workora.app.data.local.dao.ProfileDao
import com.workora.app.data.local.entity.toDomain
import com.workora.app.data.local.entity.toEntity
import com.workora.app.domain.model.Profile
import com.workora.app.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val dao: ProfileDao
) : ProfileRepository {

    override fun getProfile(): Flow<Profile?> {
        return dao.getProfile().map { it?.toDomain() }
    }

    override suspend fun saveProfile(profile: Profile) {
        dao.insertProfile(profile.toEntity())
    }
}