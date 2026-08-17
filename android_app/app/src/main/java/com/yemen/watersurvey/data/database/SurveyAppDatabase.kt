package com.yemen.watersurvey.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yemen.watersurvey.data.dao.*
import com.yemen.watersurvey.data.entity.*

/**
 * Main Room Database for Yemen Water Survey Application.
 * Houses FormPackageEntity, SurveyRecordEntity, SurveyRevisionEntity,
 * AuditLogEntity, and SurveyAttachmentEntity with strict schema isolation.
 */
@Database(
    entities = [
        FormPackageEntity::class,
        SurveyRecordEntity::class,
        SurveyRevisionEntity::class,
        AuditLogEntity::class,
        SurveyAttachmentEntity::class,
        SyncPackageEntity::class,
        SyncPackageHistoryEntity::class,
        Admin1Entity::class,
        Admin2Entity::class,
        Admin3Entity::class,
        VillageEntity::class,
        AdminGeometryEntity::class,
        AdministrativeOverrideEntity::class,
        AdminReferencePackageEntity::class,
        DeviceSequencePoolEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class SurveyAppDatabase : RoomDatabase() {

    abstract fun formPackageDao(): FormPackageDao
    abstract fun surveyRecordDao(): SurveyRecordDao
    abstract fun surveyRevisionDao(): SurveyRevisionDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun surveyAttachmentDao(): SurveyAttachmentDao
    abstract fun syncPackageDao(): SyncPackageDao
    abstract fun adminReferenceDao(): AdminReferenceDao
    abstract fun adminGeometryDao(): AdminGeometryDao
    abstract fun administrativeOverrideDao(): AdministrativeOverrideDao
    abstract fun adminReferencePackageDao(): AdminReferencePackageDao
    abstract fun deviceSequenceDao(): DeviceSequenceDao

    companion object {
        @Volatile
        private var INSTANCE: SurveyAppDatabase? = null

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE survey_records ADD COLUMN registryCode TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE survey_records ADD COLUMN isRegistryCodePending INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE survey_records ADD COLUMN enumeratorCode TEXT NOT NULL DEFAULT ''")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS device_sequence_pools (
                        adminBucketKey TEXT NOT NULL,
                        facilityType TEXT NOT NULL,
                        rangeStart INTEGER NOT NULL,
                        rangeEnd INTEGER NOT NULL,
                        currentNext INTEGER NOT NULL,
                        lastAllocatedTimestamp INTEGER NOT NULL,
                        PRIMARY KEY(adminBucketKey, facilityType)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_device_sequence_pools_adminBucketKey ON device_sequence_pools (adminBucketKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_device_sequence_pools_facilityType ON device_sequence_pools (facilityType)")
            }
        }

        fun getInstance(context: Context): SurveyAppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SurveyAppDatabase::class.java,
                    "yemen_water_survey_db"
                )
                    .addMigrations(MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun createInMemory(context: Context): SurveyAppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                SurveyAppDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}
