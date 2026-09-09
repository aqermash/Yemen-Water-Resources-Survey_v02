package com.yemen.watersurvey.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.yemen.watersurvey.data.dao.UserDao
import com.yemen.watersurvey.data.database.SurveyAppDatabase
import com.yemen.watersurvey.data.entity.UserEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@Database(entities = [UserEntity::class], version = 8, exportSchema = false)
abstract class TestSurveyAppDatabaseV8 : RoomDatabase() {
    abstract fun userDao(): UserDao
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class MigrationTest {

    private val dbName = "migration-test-db"
    private lateinit var context: Context
    private var openHelper: SupportSQLiteOpenHelper? = null

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        openHelper?.close()
        context.deleteDatabase(dbName)
    }

    private fun createV7Database(): SupportSQLiteDatabase {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create existing version 7 tables
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `survey_records` (
                            `id` TEXT PRIMARY KEY NOT NULL,
                            `registryCode` TEXT NOT NULL DEFAULT '',
                            `isRegistryCodePending` INTEGER NOT NULL DEFAULT 0,
                            `enumeratorCode` TEXT NOT NULL DEFAULT ''
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `device_sequence_pools` (
                            `adminBucketKey` TEXT NOT NULL,
                            `facilityType` TEXT NOT NULL,
                            `rangeStart` INTEGER NOT NULL,
                            `rangeEnd` INTEGER NOT NULL,
                            `currentNext` INTEGER NOT NULL,
                            `lastAllocatedTimestamp` INTEGER NOT NULL,
                            PRIMARY KEY(`adminBucketKey`, `facilityType`)
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        openHelper = factory.create(config)
        return openHelper!!.writableDatabase
    }

    /**
     * Test 3.1: migrate7To8_createsUsersTable
     * 1. Create DB at version 7
     * 2. Insert sample rows into existing tables to simulate real data
     * 3. Run MIGRATION_7_8
     * 4. Assert: query for 'users' table returns 1 row
     * 5. Assert: previously inserted data still exists intact (SELECT COUNT(*) = 1)
     */
    @Test
    fun migrate7To8_createsUsersTable() {
        val db = createV7Database()
        assertEquals("Database should start at version 7", 7, db.version)

        // Insert sample row into survey_records
        db.execSQL(
            "INSERT INTO survey_records (id, registryCode, isRegistryCodePending, enumeratorCode) " +
            "VALUES ('survey-101', 'YE110101-WL-0001', 0, 'ENUM-99')"
        )

        // Insert sample row into device_sequence_pools
        db.execSQL(
            "INSERT INTO device_sequence_pools (adminBucketKey, facilityType, rangeStart, rangeEnd, currentNext, lastAllocatedTimestamp) " +
            "VALUES ('YE110101', 'WL', 1, 100, 15, 1725900000)"
        )

        // Execute MIGRATION_7_8
        SurveyAppDatabase.MIGRATION_7_8.migrate(db)
        db.version = 8

        // Assert table 'users' exists
        val tableCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='users'")
        tableCursor.use { cursor ->
            assertTrue("Table 'users' must exist after migration", cursor.moveToFirst())
            assertEquals("users", cursor.getString(0))
        }

        // Assert pre-existing data in survey_records is preserved
        val surveyCursor = db.query("SELECT COUNT(*), registryCode, enumeratorCode FROM survey_records WHERE id='survey-101'")
        surveyCursor.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals("YE110101-WL-0001", cursor.getString(1))
            assertEquals("ENUM-99", cursor.getString(2))
        }

        // Assert pre-existing data in device_sequence_pools is preserved
        val poolCursor = db.query("SELECT COUNT(*), currentNext FROM device_sequence_pools WHERE adminBucketKey='YE110101' AND facilityType='WL'")
        poolCursor.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals(15, cursor.getInt(1))
        }
    }

    /**
     * Test 3.2: migrate7To8_createsAllIndices
     * 1. Create v7 DB and run MIGRATION_7_8
     * 2. Query sqlite_master for indices on 'users'
     * 3. Assert at least 6 indices exist matching MIGRATION_7_8 SQL
     * 4. Verify each expected index name is present
     * 5. Verify username uniqueness enforcement
     */
    @Test
    fun migrate7To8_createsAllIndices() {
        val db = createV7Database()
        SurveyAppDatabase.MIGRATION_7_8.migrate(db)
        db.version = 8

        val expectedIndices = setOf(
            "index_users_username",
            "index_users_role",
            "index_users_governorateCode",
            "index_users_districtCode",
            "index_users_isActive",
            "index_users_assignedDeviceId"
        )

        val actualIndices = mutableSetOf<String>()
        val indexCursor = db.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='users'")
        indexCursor.use { cursor ->
            while (cursor.moveToNext()) {
                actualIndices.add(cursor.getString(0))
            }
        }

        assertTrue(
            "At least 6 indices must exist on 'users', found ${actualIndices.size}: $actualIndices",
            actualIndices.size >= 6
        )

        for (expected in expectedIndices) {
            assertTrue("Expected index '$expected' must be present in $actualIndices", actualIndices.contains(expected))
        }

        // Verify username uniqueness constraint
        db.execSQL("""
            INSERT INTO users (userId, username, fullNameAr, fullNameEn, role, pinSalt, pinSaltedHash, isActive, provisionedBy, provisionedAt, metadataExtraJson)
            VALUES ('u1', 'unique_user', 'مستخدم 1', 'User 1', 'ENUMERATOR', 'salt1', 'hash1', 1, 'admin', '2026-09-09', '{}')
        """.trimIndent())

        assertThrows(SQLiteConstraintException::class.java) {
            db.execSQL("""
                INSERT INTO users (userId, username, fullNameAr, fullNameEn, role, pinSalt, pinSaltedHash, isActive, provisionedBy, provisionedAt, metadataExtraJson)
                VALUES ('u2', 'unique_user', 'مستخدم 2', 'User 2', 'SUPERVISOR', 'salt2', 'hash2', 1, 'admin', '2026-09-09', '{}')
            """.trimIndent())
        }
    }

    /**
     * Test 3.3: migrate7To8_schemaMatchesEntity
     * 1. Verify PRAGMA table_info('users') matches UserEntity columns, types, notnull, and pk
     * 2. Verify Room entity compatibility by opening migrated database with Room
     * 3. Insert and query UserEntity through Room DAO to ensure complete schema congruence
     */
    @Test
    fun migrate7To8_schemaMatchesEntity() = runBlocking {
        val db = createV7Database()
        SurveyAppDatabase.MIGRATION_7_8.migrate(db)
        db.version = 8

        // Expected schema matching UserEntity exactly
        data class ColumnSpec(val type: String, val notNull: Boolean, val isPk: Boolean)
        val expectedColumns = mapOf(
            "userId" to ColumnSpec("TEXT", true, true),
            "username" to ColumnSpec("TEXT", true, false),
            "fullNameAr" to ColumnSpec("TEXT", true, false),
            "fullNameEn" to ColumnSpec("TEXT", true, false),
            "role" to ColumnSpec("TEXT", true, false),
            "pinSalt" to ColumnSpec("TEXT", true, false),
            "pinSaltedHash" to ColumnSpec("TEXT", true, false),
            "governorateCode" to ColumnSpec("TEXT", false, false),
            "districtCode" to ColumnSpec("TEXT", false, false),
            "assignedDeviceId" to ColumnSpec("TEXT", false, false),
            "publicKeyBase64" to ColumnSpec("TEXT", false, false),
            "isActive" to ColumnSpec("INTEGER", true, false),
            "provisionedBy" to ColumnSpec("TEXT", true, false),
            "provisionedAt" to ColumnSpec("TEXT", true, false),
            "lastLoginAt" to ColumnSpec("TEXT", false, false),
            "metadataExtraJson" to ColumnSpec("TEXT", true, false)
        )

        val actualColumns = mutableMapOf<String, ColumnSpec>()
        val infoCursor = db.query("PRAGMA table_info('users')")
        infoCursor.use { cursor ->
            val nameCol = cursor.getColumnIndex("name")
            val typeCol = cursor.getColumnIndex("type")
            val notNullCol = cursor.getColumnIndex("notnull")
            val pkCol = cursor.getColumnIndex("pk")

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol)
                val type = cursor.getString(typeCol).uppercase()
                val notNull = cursor.getInt(notNullCol) == 1
                val pk = cursor.getInt(pkCol) > 0
                actualColumns[name] = ColumnSpec(type, notNull, pk)
            }
        }

        assertEquals("Number of columns in users table must match UserEntity (16)", 16, actualColumns.size)

        for ((colName, spec) in expectedColumns) {
            val actual = actualColumns[colName]
            assertNotNull("Column '$colName' must exist in table 'users'", actual)
            assertEquals("Column '$colName' type mismatch", spec.type, actual!!.type)
            assertEquals("Column '$colName' notNull mismatch", spec.notNull, actual.notNull)
            assertEquals("Column '$colName' primary key mismatch", spec.isPk, actual.isPk)
        }

        // Close low-level SQLite handle before opening with Room
        openHelper?.close()

        // Validate that Room opens the migrated database file and DAO operates seamlessly
        val roomDb = Room.databaseBuilder(context, TestSurveyAppDatabaseV8::class.java, dbName)
            .allowMainThreadQueries()
            .build()

        try {
            val userDao = roomDb.userDao()
            val user = UserEntity(
                userId = "test-user-001",
                username = "taha_yemen",
                fullNameAr = "طه محمد",
                fullNameEn = "Taha Mohammed",
                role = "ENUMERATOR",
                pinSalt = "salt123",
                pinSaltedHash = "hash123",
                governorateCode = "YE22",
                districtCode = "YE2215",
                assignedDeviceId = "DEV-99",
                publicKeyBase64 = null,
                isActive = true,
                provisionedBy = "SYSTEM",
                provisionedAt = "2026-09-09T22:00:00Z",
                lastLoginAt = null,
                metadataExtraJson = "{}"
            )

            userDao.insertOrUpdateUser(user)

            val retrieved = userDao.getUserById("test-user-001")
            assertNotNull("User inserted into migrated DB should be retrieved via Room", retrieved)
            assertEquals(user.username, retrieved!!.username)
            assertEquals(user.fullNameAr, retrieved.fullNameAr)
            assertEquals(user.role, retrieved.role)
            assertEquals(user.governorateCode, retrieved.governorateCode)
            assertEquals(user.districtCode, retrieved.districtCode)
            assertEquals(user.assignedDeviceId, retrieved.assignedDeviceId)
            assertEquals(user.isActive, retrieved.isActive)
        } finally {
            roomDb.close()
        }
    }
}
