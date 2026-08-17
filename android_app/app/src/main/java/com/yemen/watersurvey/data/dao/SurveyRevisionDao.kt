package com.yemen.watersurvey.data.dao

import androidx.room.*
import com.yemen.watersurvey.data.entity.SurveyRevisionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for immutable Survey Revision snapshots.
 */
@Dao
interface SurveyRevisionDao {

    @Query("SELECT * FROM survey_revisions WHERE surveyUUID = :uuid ORDER BY revisionNumber DESC")
    fun getRevisionsForSurvey(uuid: String): Flow<List<SurveyRevisionEntity>>

    @Query("SELECT * FROM survey_revisions WHERE surveyUUID = :uuid ORDER BY revisionNumber DESC")
    suspend fun getRevisionsForSurveySync(uuid: String): List<SurveyRevisionEntity>

    @Query("SELECT * FROM survey_revisions WHERE revisionId = :revisionId LIMIT 1")
    suspend fun getRevisionById(revisionId: String): SurveyRevisionEntity?

    @Query("SELECT * FROM survey_revisions ORDER BY modifiedAt DESC")
    suspend fun getAllRevisionsSync(): List<SurveyRevisionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRevision(revision: SurveyRevisionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRevisions(revisions: List<SurveyRevisionEntity>)
}
