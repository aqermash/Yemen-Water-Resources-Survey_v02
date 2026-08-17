package com.yemen.watersurvey.data.dao

import androidx.room.*
import com.yemen.watersurvey.data.entity.SyncPackageEntity
import com.yemen.watersurvey.data.entity.SyncPackageHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Supervisor Sync Packages and Historical Transitions.
 */
@Dao
interface SyncPackageDao {

    @Query("SELECT * FROM sync_packages ORDER BY receivedTimestamp DESC")
    fun getAllPackagesFlow(): Flow<List<SyncPackageEntity>>

    @Query("SELECT * FROM sync_packages ORDER BY receivedTimestamp DESC")
    suspend fun getAllPackagesSync(): List<SyncPackageEntity>

    @Query("SELECT * FROM sync_packages WHERE isArchived = 0 ORDER BY receivedTimestamp DESC")
    suspend fun getActivePackagesSync(): List<SyncPackageEntity>

    @Query("SELECT * FROM sync_packages WHERE isArchived = 1 ORDER BY receivedTimestamp DESC")
    suspend fun getArchivedPackagesSync(): List<SyncPackageEntity>

    @Query("SELECT * FROM sync_packages WHERE packageId = :packageId LIMIT 1")
    suspend fun getPackageById(packageId: String): SyncPackageEntity?

    @Query("SELECT * FROM sync_packages WHERE packageSha256 = :sha256 LIMIT 1")
    suspend fun findPackageBySha256(sha256: String): SyncPackageEntity?

    @Query("SELECT * FROM sync_packages WHERE state = :state AND isArchived = 0 ORDER BY receivedTimestamp DESC")
    suspend fun getPackagesByState(state: String): List<SyncPackageEntity>

    @Query("SELECT * FROM sync_packages WHERE governorate = :gov AND district = :dist AND isArchived = 0")
    suspend fun getPackagesByLocation(gov: String, dist: String): List<SyncPackageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePackage(pkg: SyncPackageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackages(packages: List<SyncPackageEntity>)

    @Query("UPDATE sync_packages SET state = :newState, lastReviewedAt = :timestamp, reviewedBy = :actorId, decisionReason = :reason WHERE packageId = :packageId")
    suspend fun updatePackageState(packageId: String, newState: String, timestamp: String, actorId: String, reason: String? = null)

    @Query("UPDATE sync_packages SET isArchived = :isArchived WHERE packageId = :packageId")
    suspend fun setPackageArchived(packageId: String, isArchived: Boolean)

    @Query("UPDATE sync_packages SET newRecordsCount = :newCount, updatesCount = :updatesCount, conflictsCount = :conflictsCount, duplicatesCount = :duplicatesCount WHERE packageId = :packageId")
    suspend fun updateConflictCounts(packageId: String, newCount: Int, updatesCount: Int, conflictsCount: Int, duplicatesCount: Int)

    @Query("UPDATE sync_packages SET mergedRecordsCount = :mergedCount, state = :newState, lastReviewedAt = :timestamp, reviewedBy = :actorId WHERE packageId = :packageId")
    suspend fun markPackageMerged(packageId: String, mergedCount: Int, newState: String, timestamp: String, actorId: String)

    @Query("DELETE FROM sync_packages WHERE packageId = :packageId")
    suspend fun deletePackage(packageId: String)

    // --- History Log Operations ---

    @Query("SELECT * FROM sync_package_history WHERE packageId = :packageId ORDER BY timestamp DESC")
    suspend fun getHistoryForPackage(packageId: String): List<SyncPackageHistoryEntity>

    @Query("SELECT * FROM sync_package_history ORDER BY timestamp DESC")
    suspend fun getAllHistory(): List<SyncPackageHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: SyncPackageHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistories(histories: List<SyncPackageHistoryEntity>)
}
