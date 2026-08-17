package com.yemen.watersurvey.data.dao

import androidx.room.*
import com.yemen.watersurvey.data.entity.AdminReferencePackageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for AdminReferencePackageEntity.
 */
@Dao
interface AdminReferencePackageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackage(pkg: AdminReferencePackageEntity)

    @Query("SELECT * FROM admin_reference_packages WHERE isActive = 1 LIMIT 1")
    suspend fun getActivePackage(): AdminReferencePackageEntity?

    @Query("SELECT * FROM admin_reference_packages WHERE isActive = 1 LIMIT 1")
    fun observeActivePackage(): Flow<AdminReferencePackageEntity?>

    @Query("SELECT * FROM admin_reference_packages WHERE packageId = :packageId LIMIT 1")
    suspend fun getPackageById(packageId: String): AdminReferencePackageEntity?

    @Query("SELECT * FROM admin_reference_packages WHERE packageSha256 = :sha256 LIMIT 1")
    suspend fun getPackageBySha256(sha256: String): AdminReferencePackageEntity?

    @Query("SELECT * FROM admin_reference_packages ORDER BY installedAt DESC")
    suspend fun getAllPackages(): List<AdminReferencePackageEntity>

    @Query("UPDATE admin_reference_packages SET isActive = CASE WHEN packageId = :activePackageId THEN 1 ELSE 0 END")
    suspend fun setActivePackage(activePackageId: String)
}
