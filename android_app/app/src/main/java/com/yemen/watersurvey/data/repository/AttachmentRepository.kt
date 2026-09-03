package com.yemen.watersurvey.data.repository

import android.content.Context
import com.yemen.watersurvey.core.camera.PhotoCaptureManager
import com.yemen.watersurvey.core.camera.PhotoSaveResult
import com.yemen.watersurvey.data.dao.SurveyAttachmentDao
import com.yemen.watersurvey.data.entity.SurveyAttachmentEntity
import java.io.File

/**
 * Phase 10A — CameraX Core Pipeline.
 *
 * Coordinates [PhotoCaptureManager] (image processing + disk write) with
 * [SurveyAttachmentDao] (Room persistence).
 *
 * Architecture boundary:
 * - [SurveyViewModel] must NOT call [PhotoCaptureManager] directly.
 * - All camera-to-database coordination is funnelled through this class.
 * - This class does NOT hold any Compose or Lifecycle references.
 *
 * @param context Application context (used by [PhotoCaptureManager] to resolve filesDir).
 * @param attachmentDao Room DAO for survey attachments.
 */
open class AttachmentRepository(
    private val context: Context,
    private val attachmentDao: SurveyAttachmentDao
) {

    private val photoCaptureManager = PhotoCaptureManager(context)

    /**
     * Processes a raw JPEG file from CameraX capture, compresses it, writes it to internal
     * storage under `<filesDir>/attachments/`, computes SHA-256, and persists the metadata
     * to Room as a [SurveyAttachmentEntity].
     *
     * @param sourceFile Temporary JPEG file produced by CameraX `ImageCapture.takePicture()`.
     *   The caller is responsible for deleting this file after this call completes (success or
     *   failure), as [AttachmentRepository] does NOT delete the source.
     * @param surveyUUID UUID of the active survey (`SurveyRecordEntity.surveyUUID`).
     * @param recordId Record ID of the active survey (`SurveyRecordEntity.recordId`).
     * @return The [SurveyAttachmentEntity] that was persisted to Room.
     * @throws Exception if image processing or Room insertion fails.
     */
    open suspend fun savePhoto(
        sourceFile: File,
        surveyUUID: String,
        recordId: String
    ): SurveyAttachmentEntity {
        val result: PhotoSaveResult = photoCaptureManager.processAndSavePhoto(sourceFile, surveyUUID)
        val entity = result.toEntity(recordId)
        attachmentDao.insertAttachment(entity)
        return entity
    }

    /**
     * Returns all attachment entities associated with a given survey UUID.
     * Thin pass-through to [SurveyAttachmentDao.getAttachmentsForSurvey].
     *
     * @param surveyUUID Survey UUID to look up.
     * @return List of [SurveyAttachmentEntity] (empty list if none).
     */
    suspend fun getAttachmentsForSurvey(surveyUUID: String): List<SurveyAttachmentEntity> {
        return attachmentDao.getAttachmentsForSurvey(surveyUUID)
    }

    /**
     * Deletes a local attachment file from internal storage.
     *
     * If the file does not exist, this is a no-op (returns false).
     *
     * @param localFilePath Relative path stored in [SurveyAttachmentEntity.localFilePath],
     *   e.g. `attachments/ATT_<uuid>.jpg`.
     * @return `true` if file was deleted, `false` if it did not exist or deletion failed.
     */
    fun deleteAttachmentFile(localFilePath: String): Boolean {
        val file = File(context.filesDir, localFilePath)
        return file.exists() && file.delete()
    }

    /**
     * Deletes an attachment by ID: removes record from Room database and deletes local file.
     */
    open suspend fun deleteAttachment(attachment: SurveyAttachmentEntity) {
        attachmentDao.deleteAttachmentById(attachment.attachmentId)
        deleteAttachmentFile(attachment.localFilePath)
    }
}

// ---------------------------------------------------------------------------
// Extension: PhotoSaveResult → SurveyAttachmentEntity
// ---------------------------------------------------------------------------

/**
 * Maps the in-memory [PhotoSaveResult] value object to the Room [SurveyAttachmentEntity]
 * that will be persisted to the `survey_attachments` table.
 *
 * No migration required — all target fields already exist in the schema.
 *
 * @param recordId The `recordId` of the survey record this attachment is linked to.
 */
private fun PhotoSaveResult.toEntity(recordId: String): SurveyAttachmentEntity =
    SurveyAttachmentEntity(
        attachmentId = attachmentId,
        surveyUUID = surveyUUID,
        recordId = recordId,
        attachmentType = "PHOTO",
        localFilePath = relativePath,
        fileName = fileName,
        fileSizeBytes = fileSizeBytes,
        fileSha256 = fileSha256,
        capturedAt = capturedAt,
        sourcePackageId = ""         // Populated during sync import (Phase 10C / sync layer)
    )
