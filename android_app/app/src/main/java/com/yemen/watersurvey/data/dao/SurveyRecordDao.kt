package com.yemen.watersurvey.data.dao

import androidx.room.*
import com.yemen.watersurvey.data.entity.SurveyRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local Survey Records.
 */
@Dao
interface SurveyRecordDao {

    @Query("SELECT * FROM survey_records ORDER BY createdAt DESC")
    fun getAllSurveys(): Flow<List<SurveyRecordEntity>>

    @Query("SELECT * FROM survey_records ORDER BY createdAt DESC")
    suspend fun getAllSurveysSync(): List<SurveyRecordEntity>

    @Query("SELECT * FROM survey_records WHERE workflowStatus = :status ORDER BY createdAt DESC")
    fun getSurveysByStatus(status: String): Flow<List<SurveyRecordEntity>>

    @Query("SELECT * FROM survey_records WHERE surveyUUID = :uuid LIMIT 1")
    suspend fun getSurveyByUUID(uuid: String): SurveyRecordEntity?

    @Query("SELECT * FROM survey_records WHERE recordId = :recordId LIMIT 1")
    suspend fun getSurveyByRecordId(recordId: String): SurveyRecordEntity?

    @Query("SELECT * FROM survey_records WHERE surveyUUID IN (:uuids)")
    suspend fun getSurveysByUUIDs(uuids: List<String>): List<SurveyRecordEntity>

    @Query("SELECT COUNT(*) FROM survey_records")
    suspend fun countSurveys(): Int

    @Query("UPDATE survey_records SET workflowStatus = :status, updatedAt = :updatedAt WHERE surveyUUID = :uuid")
    suspend fun updateWorkflowStatus(uuid: String, status: String, updatedAt: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSurvey(survey: SurveyRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSurvey(survey: SurveyRecordEntity)

    @Update
    suspend fun updateSurvey(survey: SurveyRecordEntity)

    @Query("SELECT COUNT(*) FROM survey_records WHERE formId = :formId AND formVersion = :formVersion")
    suspend fun countSurveysWithFormPackage(formId: String, formVersion: String): Int

    @Query("SELECT * FROM survey_records WHERE formId = :formId AND formVersion = :formVersion")
    suspend fun getSurveysByFormPackage(formId: String, formVersion: String): List<SurveyRecordEntity>

    @Query("DELETE FROM survey_records WHERE surveyUUID = :uuid")
    suspend fun deleteByUUID(uuid: String)
}
