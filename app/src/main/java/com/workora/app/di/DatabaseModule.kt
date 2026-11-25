package com.workora.app.di

import android.app.Application
import androidx.room.Room
import com.workora.app.data.local.WorkoraDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideWorkoraDatabase(app: Application): WorkoraDatabase {
        return Room.databaseBuilder(
            app,
            WorkoraDatabase::class.java,
            "workora_db"
        ).addMigrations(WorkoraDatabase.MIGRATION_5_6).build()
    }

    @Provides
    @Singleton
    fun provideProfileDao(db: WorkoraDatabase) = db.profileDao

    @Provides
    @Singleton
    fun provideVacancyDao(db: WorkoraDatabase) = db.vacancyDao

    @Provides
    @Singleton
    fun provideHiringRoomDao(db: WorkoraDatabase) = db.hiringRoomDao
}
