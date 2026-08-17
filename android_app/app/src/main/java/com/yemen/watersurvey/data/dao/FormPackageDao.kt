package com.yemen.watersurvey.data.dao

import androidx.room.*
import com.yemen.watersurvey.data.entity.FormPackageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for versioned Survey Form Packages.
 */
@Dao
interface FormPackageDao {

    @Query("SELECT * FROM form_packages ORDER BY form_id ASC, version DESC")
    fun getAllPackagesFlow(): Flow<List<FormPackageEntity>>

    @Query("SELECT * FROM form_packages ORDER BY form_id ASC, version DESC")
    suspend fun getAllPackages(): List<FormPackageEntity>

    @Query("SELECT * FROM form_packages WHERE is_active = 1")
    suspend fun getActivePackages(): List<FormPackageEntity>

    @Query("SELECT * FROM form_packages WHERE form_id = :formId AND is_active = 1 LIMIT 1")
    suspend fun getActivePackageForForm(formId: String): FormPackageEntity?

    @Query("SELECT * FROM form_packages WHERE form_id = :formId AND version = :version LIMIT 1")
    suspend fun getPackageByVersion(formId: String, version: String): FormPackageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackage(entity: FormPackageEntity)

    @Update
    suspend fun updatePackage(entity: FormPackageEntity)

    @Query("UPDATE form_packages SET is_active = 0 WHERE form_id = :formId")
    suspend fun deactivateAllVersionsForForm(formId: String)

    @Query("UPDATE form_packages SET is_active = 1, updated_at = :updatedAt WHERE form_id = :formId AND version = :version")
    suspend fun activatePackageVersion(formId: String, version: String, updatedAt: String)

    @Transaction
    suspend fun setActiveVersion(formId: String, version: String, updatedAt: String) {
        deactivateAllVersionsForForm(formId)
        activatePackageVersion(formId, version, updatedAt)
    }

    @Query("DELETE FROM form_packages WHERE form_id = :formId AND version = :version")
    suspend fun deletePackage(formId: String, version: String)

    @Query("SELECT COUNT(*) FROM form_packages WHERE form_id = :formId")
    suspend fun getVersionCount(formId: String): Int
}
