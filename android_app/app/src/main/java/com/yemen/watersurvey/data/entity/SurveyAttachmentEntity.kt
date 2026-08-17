package com.yemen.watersurvey.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room database entity storing attachment references associated with survey records.
 * Attachments are indexed by `surveyUUID` and deduplicated by `fileSha256`.
 */
@Entity(
    tableName = "survey_attachments",
    indices = [
        Index(value = ["surveyUUID"]),
        Index(value = ["recordId"]),
        Index(value = ["fileSha256"]),
        Index(value = ["fileName"])
    ]
)
data class SurveyAttachmentEntity(
    @PrimaryKey
    val attachmentId: String,
    val surveyUUID: String,
    val recordId: String,
    val attachmentType: String,
    val localFilePath: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val fileSha256: String,
    val capturedAt: String,
    val sourcePackageId: String = ""
)
