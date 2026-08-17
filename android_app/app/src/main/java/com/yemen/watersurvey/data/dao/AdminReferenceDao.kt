package com.yemen.watersurvey.data.dao

import androidx.room.*
import com.yemen.watersurvey.data.entity.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Admin1 (Governorates), Admin2 (Districts),
 * Admin3 (Uzlahs), and Villages.
 */
@Dao
interface AdminReferenceDao {

    // ==================== ADMIN1 (GOVERNORATES) ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdmin1List(list: List<Admin1Entity>)

    @Query("SELECT * FROM admin1_governorates WHERE isActive = 1 ORDER BY nameAr ASC")
    suspend fun getAllActiveGovernorates(): List<Admin1Entity>

    @Query("SELECT * FROM admin1_governorates WHERE isActive = 1 ORDER BY nameAr ASC")
    fun observeAllActiveGovernorates(): Flow<List<Admin1Entity>>

    @Query("SELECT * FROM admin1_governorates WHERE admin1Pcode = :pcode LIMIT 1")
    suspend fun getGovernorateByPcode(pcode: String): Admin1Entity?

    @Query("SELECT COUNT(*) FROM admin1_governorates")
    suspend fun getGovernoratesCount(): Int

    // ==================== ADMIN2 (DISTRICTS) ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdmin2List(list: List<Admin2Entity>)

    @Query("SELECT * FROM admin2_districts WHERE admin1Pcode = :admin1Pcode AND isActive = 1 ORDER BY nameAr ASC")
    suspend fun getDistrictsByGovernorate(admin1Pcode: String): List<Admin2Entity>

    @Query("SELECT * FROM admin2_districts WHERE admin1Pcode = :admin1Pcode AND isActive = 1 ORDER BY nameAr ASC")
    fun observeDistrictsByGovernorate(admin1Pcode: String): Flow<List<Admin2Entity>>

    @Query("SELECT * FROM admin2_districts WHERE admin2Pcode = :pcode LIMIT 1")
    suspend fun getDistrictByPcode(pcode: String): Admin2Entity?

    @Query("SELECT * FROM admin2_districts WHERE isActive = 1 ORDER BY nameAr ASC")
    suspend fun getAllActiveDistricts(): List<Admin2Entity>

    @Query("SELECT COUNT(*) FROM admin2_districts")
    suspend fun getDistrictsCount(): Int

    // ==================== ADMIN3 (UZLAHS) ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdmin3List(list: List<Admin3Entity>)

    @Query("SELECT * FROM admin3_uzlahs WHERE admin2Pcode = :admin2Pcode AND isActive = 1 ORDER BY nameAr ASC")
    suspend fun getUzlahsByDistrict(admin2Pcode: String): List<Admin3Entity>

    @Query("SELECT * FROM admin3_uzlahs WHERE admin2Pcode = :admin2Pcode AND isActive = 1 ORDER BY nameAr ASC")
    fun observeUzlahsByDistrict(admin2Pcode: String): Flow<List<Admin3Entity>>

    @Query("SELECT * FROM admin3_uzlahs WHERE admin3Pcode = :pcode LIMIT 1")
    suspend fun getUzlahByPcode(pcode: String): Admin3Entity?

    @Query("SELECT * FROM admin3_uzlahs WHERE isActive = 1 ORDER BY nameAr ASC")
    suspend fun getAllActiveUzlahs(): List<Admin3Entity>

    @Query("SELECT COUNT(*) FROM admin3_uzlahs")
    suspend fun getUzlahsCount(): Int

    // ==================== VILLAGES ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVillagesList(list: List<VillageEntity>)

    @Query("SELECT * FROM admin_villages WHERE admin3Pcode = :admin3Pcode ORDER BY nameAr ASC")
    suspend fun getVillagesByUzlah(admin3Pcode: String): List<VillageEntity>

    @Query("SELECT * FROM admin_villages WHERE admin3Pcode = :admin3Pcode ORDER BY nameAr ASC")
    fun observeVillagesByUzlah(admin3Pcode: String): Flow<List<VillageEntity>>

    @Query("SELECT * FROM admin_villages WHERE villageId = :villageId LIMIT 1")
    suspend fun getVillageById(villageId: String): VillageEntity?

    @Query("SELECT * FROM admin_villages WHERE nameAr LIKE '%' || :query || '%' OR nameEn LIKE '%' || :query || '%' LIMIT 100")
    suspend fun searchVillages(query: String): List<VillageEntity>

    @Query("SELECT * FROM admin_villages WHERE mappingStatus IN ('UNMAPPED', 'NEEDS_REVIEW') LIMIT :limit")
    suspend fun getUnmappedOrReviewVillages(limit: Int = 100): List<VillageEntity>

    @Query("SELECT COUNT(*) FROM admin_villages")
    suspend fun getVillagesCount(): Int

    @Query("SELECT COUNT(*) FROM admin_villages WHERE mappingStatus = 'MAPPED_CONFIRMED'")
    suspend fun getMappedVillagesCount(): Int

    @Query("SELECT COUNT(*) FROM admin_villages WHERE mappingStatus IN ('UNMAPPED', 'NEEDS_REVIEW')")
    suspend fun getUnmappedVillagesCount(): Int

    // ==================== ADMINISTRATIVE HIERARCHY VALIDATION ====================

    @Query("SELECT COUNT(*) FROM admin2_districts WHERE admin2Pcode = :admin2Pcode AND admin1Pcode = :admin1Pcode")
    suspend fun validateDistrictInGovernorate(admin2Pcode: String, admin1Pcode: String): Int

    @Query("SELECT COUNT(*) FROM admin3_uzlahs WHERE admin3Pcode = :admin3Pcode AND admin2Pcode = :admin2Pcode")
    suspend fun validateUzlahInDistrict(admin3Pcode: String, admin2Pcode: String): Int

    // ==================== PURGE / REFRESH ====================

    @Query("DELETE FROM admin1_governorates WHERE versionTag = :versionTag")
    suspend fun deleteAdmin1ByVersion(versionTag: String)

    @Query("DELETE FROM admin2_districts WHERE versionTag = :versionTag")
    suspend fun deleteAdmin2ByVersion(versionTag: String)

    @Query("DELETE FROM admin3_uzlahs WHERE versionTag = :versionTag")
    suspend fun deleteAdmin3ByVersion(versionTag: String)

    @Query("DELETE FROM admin_villages WHERE versionTag = :versionTag")
    suspend fun deleteVillagesByVersion(versionTag: String)
}
