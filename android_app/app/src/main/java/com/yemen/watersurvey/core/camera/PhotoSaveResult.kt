package com.yemen.watersurvey.core.camera

/**
 * Value object returned by [PhotoCaptureManager.processAndSavePhoto] or
 * [PhotoCaptureManager.processInMemory].
 *
 * All path fields are empty strings when produced by [processInMemory] (in-memory test mode).
 */
data class PhotoSaveResult(
    /** Unique attachment UUID, used as the primary key in [SurveyAttachmentEntity]. */
    val attachmentId: String,
    /** File name: `ATT_<attachmentId>.jpg` */
    val fileName: String,
    /**
     * Relative path stored in [SurveyAttachmentEntity.localFilePath]:
     * `attachments/ATT_<attachmentId>.jpg`.
     *
     * Resolved by [SurveySyncExporter] as `context.filesDir/<relativePath>`.
     */
    val relativePath: String,
    /** Absolute path on the device filesystem. Empty when using in-memory overload. */
    val absolutePath: String,
    /** File size in bytes of the compressed JPEG. */
    val fileSizeBytes: Long,
    /** SHA-256 hex digest of the compressed JPEG bytes. */
    val fileSha256: String,
    /** ISO-8601 timestamp (`yyyy-MM-dd HH:mm:ss`) of when the photo was captured. */
    val capturedAt: String,
    /** UUID of the survey this attachment belongs to. */
    val surveyUUID: String,
    /** Final JPEG quality used after any compression retries (80, 65, or 50). */
    val finalJpegQuality: Int,
    /** Width of the output image in pixels (after downsampling). */
    val outputWidth: Int,
    /** Height of the output image in pixels (after downsampling). */
    val outputHeight: Int
)
