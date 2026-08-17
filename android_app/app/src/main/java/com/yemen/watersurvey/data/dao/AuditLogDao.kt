package com.yemen.watersurvey.data.dao

import androidx.room.*
import com.yemen.watersurvey.data.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for immutable Audit Logs.
 */
@Dao
interface AuditLogDao {

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE surveyUUID = :uuid ORDER BY timestamp DESC")
    suspend fun getAuditLogsForSurvey(uuid: String): List<AuditLogEntity>

    @Query("SELECT * FROM audit_logs WHERE packageId = :packageId ORDER BY timestamp DESC")
    suspend fun getAuditLogsForPackage(packageId: String): List<AuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLogs(logs: List<AuditLogEntity>)
}
