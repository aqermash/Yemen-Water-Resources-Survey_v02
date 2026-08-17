package com.yemen.watersurvey.data.dao

import androidx.room.*
import com.yemen.watersurvey.data.entity.SurveyAttachmentEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local Survey Attachments.
 */
@Dao
interface SurveyAttachmentDao {

    @Query("SELECT * FROM survey_attachments WHERE surveyUUID = :uuid")
    suspend fun getAttachmentsForSurvey(uuid: String): List<SurveyAttachmentEntity>

    @Query("SELECT * FROM survey_attachments WHERE fileSha256 = :sha256 LIMIT 1")
    suspend fun findAttachmentBySha256(sha256: String): SurveyAttachmentEntity?

    @Query("SELECT * FROM survey_attachments WHERE surveyUUID = :uuid AND fileName = :fileName LIMIT 1")
    suspend fun findAttachmentByName(uuid: String, fileName: String): SurveyAttachmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: SurveyAttachmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<SurveyAttachmentEntity>)
}
