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
        DeviceSequencePoolEntity::class,
        UserEntity::class
    ],
    version = 8,
    exportSchema = true
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
    abstract fun userDao(): UserDao

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

        /**
         * P2.10: Bootstrap sequence pool for admin bucket YE110101 (YE11/YE1101/YE110101).
         * INSERT OR IGNORE preserves any pools already provisioned via FormPackageManager.
         * Ranges 1-100 match the values embedded in FormPackageManager.createSamplePackageZip.
         */
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                listOf("WL", "SP", "WH").forEach { facilityType ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO device_sequence_pools " +
                        "(adminBucketKey, facilityType, rangeStart, rangeEnd, currentNext, lastAllocatedTimestamp) " +
                        "VALUES ('YE110101', '$facilityType', 1, 100, 1, $now)"
                    )
                }
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `users` (
                        `userId` TEXT NOT NULL,
                        `username` TEXT NOT NULL,
                        `fullNameAr` TEXT NOT NULL,
                        `fullNameEn` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `pinSalt` TEXT NOT NULL,
                        `pinSaltedHash` TEXT NOT NULL,
                        `governorateCode` TEXT,
                        `districtCode` TEXT,
                        `assignedDeviceId` TEXT,
                        `publicKeyBase64` TEXT,
                        `isActive` INTEGER NOT NULL,
                        `provisionedBy` TEXT NOT NULL,
                        `provisionedAt` TEXT NOT NULL,
                        `lastLoginAt` TEXT,
                        `metadataExtraJson` TEXT NOT NULL,
                        PRIMARY KEY(`userId`)
                    )
                """.trimIndent())

                // Create unique index on login username (case-insensitive in SQLite queries)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_username` ON `users` (`username`)")

                // Create query lookup indices
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_users_role` ON `users` (`role`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_users_governorateCode` ON `users` (`governorateCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_users_districtCode` ON `users` (`districtCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_users_isActive` ON `users` (`isActive`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_users_assignedDeviceId` ON `users` (`assignedDeviceId`)")
            }
        }

        fun getInstance(context: Context): SurveyAppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SurveyAppDatabase::class.java,
                    "yemen_water_survey_db"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
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
