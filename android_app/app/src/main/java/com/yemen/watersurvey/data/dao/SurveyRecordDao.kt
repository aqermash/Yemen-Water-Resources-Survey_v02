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

    @Query("SELECT * FROM survey_records WHERE surveyUUID = :uuid LIMIT 1")
    suspend fun getSurveyByUUID(uuid: String): SurveyRecordEntity?

    @Query("SELECT * FROM survey_records WHERE recordId = :recordId LIMIT 1")
    suspend fun getSurveyByRecordId(recordId: String): SurveyRecordEntity?

    @Query("SELECT * FROM survey_records WHERE surveyUUID IN (:uuids)")
    suspend fun getSurveysByUUIDs(uuids: List<String>): List<SurveyRecordEntity>

    @Query("SELECT COUNT(*) FROM survey_records")
    suspend fun countSurveys(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSurvey(survey: SurveyRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSurvey(survey: SurveyRecordEntity)

    @Update
    suspend fun updateSurvey(survey: SurveyRecordEntity)

    @Query("DELETE FROM survey_records WHERE surveyUUID = :uuid")
    suspend fun deleteByUUID(uuid: String)
}
