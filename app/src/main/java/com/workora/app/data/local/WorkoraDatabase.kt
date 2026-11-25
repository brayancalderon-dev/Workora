package com.workora.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.workora.app.data.local.dao.HiringRoomDao
import com.workora.app.data.local.dao.ProfileDao
import com.workora.app.data.local.dao.VacancyDao
import com.workora.app.data.local.entity.HiringRoomJobEntity
import com.workora.app.data.local.entity.ProfileEntity
import com.workora.app.data.local.entity.VacancyEntity

@Database(
    entities = [ProfileEntity::class, VacancyEntity::class, HiringRoomJobEntity::class],
    version = 6,
    exportSchema = false
)
abstract class WorkoraDatabase : RoomDatabase() {
    abstract val profileDao: ProfileDao
    abstract val vacancyDao: VacancyDao
    abstract val hiringRoomDao: HiringRoomDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vacancies ADD COLUMN companyName TEXT")
                db.execSQL("ALTER TABLE vacancies ADD COLUMN analysisSummary TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE vacancies ADD COLUMN analysisStatus TEXT NOT NULL DEFAULT 'Analizado'")
            }
        }
    }
}
