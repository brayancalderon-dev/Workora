package com.workora.app.di

import com.workora.app.data.repository.ProfileRepositoryImpl
import com.workora.app.domain.repository.HiringRoomRepository
import com.workora.app.domain.repository.ProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindProfileRepository(
        profileRepositoryImpl: ProfileRepositoryImpl
    ): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindHiringRoomRepository(
        hiringRoomRepositoryImpl: com.workora.app.data.repository.HiringRoomRepositoryImpl
    ): HiringRoomRepository
}