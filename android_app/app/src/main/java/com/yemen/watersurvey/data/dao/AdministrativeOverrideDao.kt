package com.yemen.watersurvey.data.dao

import androidx.room.*
import com.yemen.watersurvey.data.entity.AdministrativeOverrideEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Administrative Local Name Overrides.
 */
@Dao
interface AdministrativeOverrideDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverride(override: AdministrativeOverrideEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverrides(list: List<AdministrativeOverrideEntity>)

    @Update
    suspend fun updateOverride(override: AdministrativeOverrideEntity)

    @Query("SELECT * FROM admin_overrides WHERE officialPcodeOrId = :pcodeOrId AND isActive = 1 LIMIT 1")
    suspend fun getActiveOverrideForPcodeOrId(pcodeOrId: String): AdministrativeOverrideEntity?

    @Query("SELECT * FROM admin_overrides WHERE administrativeLevel = :level AND isActive = 1")
    suspend fun getActiveOverridesByLevel(level: String): List<AdministrativeOverrideEntity>

    @Query("SELECT * FROM admin_overrides ORDER BY createdAt DESC")
    suspend fun getAllOverrides(): List<AdministrativeOverrideEntity>

    @Query("SELECT * FROM admin_overrides ORDER BY createdAt DESC")
    fun observeAllOverrides(): Flow<List<AdministrativeOverrideEntity>>

    @Query("SELECT * FROM admin_overrides WHERE isApproved = 0 ORDER BY createdAt DESC")
    suspend fun getPendingApprovalOverrides(): List<AdministrativeOverrideEntity>

    @Query("SELECT COUNT(*) FROM admin_overrides WHERE isActive = 1")
    suspend fun getActiveOverridesCount(): Int

    @Query("UPDATE admin_overrides SET isActive = 0 WHERE overrideId = :overrideId")
    suspend fun deactivateOverride(overrideId: String)
}
