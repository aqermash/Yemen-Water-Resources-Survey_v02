package com.yemen.watersurvey.domain.model

import java.io.File

/**
 * Manifest for .ywsync offline Survey Data Exchange Package.
 */
data class SurveySyncPackageManifest(
    val packageId: String,
    val formatVersion: String = "1.0",
    val packageType: String = "YEMEN_WATER_SURVEY_SYNC",
    val sourceDeviceId: String,
    val senderRole: String,
    val senderUsername: String,
    val district: String,
    val governorate: String,
    val creationTimestamp: String,
    val surveyCount: Int,
    val attachmentCount: Int,
    val totalAttachmentSizeBytes: Long,
    val checksum: String,
    val fileList: List<SyncPackageFileEntry> = emptyList()
)

/**
 * File descriptor inside the sync package archive.
 */
data class SyncPackageFileEntry(
    val relativePath: String,
    val sizeBytes: Long,
    val sha256: String
)

/**
 * Package metadata containing contextual and filtering parameters.
 */
data class SyncPackageMetadata(
    val packageId: String,
    val titleAr: String,
    val descriptionAr: String,
    val sourceDeviceId: String,
    val senderRole: String,
    val senderUsername: String,
    val governorate: String,
    val district: String,
    val exportScope: String,
    val surveyTypeFilter: String? = null,
    val statusFilter: String? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
    val createdAt: String,
    val appVersion: String = "2.9.0",
    val targetPlatform: String = "Yemen-Water-Survey-Android"
)

/**
 * Audit and Revision log entry associated with survey records.
 */
data class SurveyRevisionRecord(
    val revisionId: String,
    val recordId: String,
    val surveyUUID: String = "",
    val revisionNumber: Int,
    val modifiedBy: String,
    val modifiedAt: String,
    val reasonForChange: String,
    val previousStatus: String,
    val newStatus: String,
    val changedFields: List<String> = emptyList()
)

/**
 * Filter configuration for selective survey synchronization.
 */
data class SyncExportFilter(
    val surveyType: SurveyType? = null,
    val workflowStatus: String? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
    val districtCode: String? = null,
    val governorateCode: String? = null
) {
    val isAll: Boolean
        get() = surveyType == null && workflowStatus == null && dateFrom == null && dateTo == null && districtCode == null && governorateCode == null

    val scopeDescriptionAr: String
        get() = if (isAll) {
            "تصدير شامل لجميع الاستمارات الميدانية"
        } else {
            val filters = mutableListOf<String>()
            surveyType?.let { filters.add("النوع: ${it.displayNameAr}") }
            workflowStatus?.let { filters.add("الحالة: $it") }
            if (dateFrom != null || dateTo != null) {
                filters.add("الفترة: ${dateFrom ?: "البداية"} إلى ${dateTo ?: "الآن"}")
            }
            if (districtCode != null) filters.add("المديرية: $districtCode")
            if (governorateCode != null) filters.add("المحافظة: $governorateCode")
            "تصدير مخصص (${filters.joinToString(" | ")})"
        }
}

/**
 * Result of creating an offline sync package.
 */
sealed class SyncExportResult {
    data class Success(
        val packageFile: File,
        val manifest: SurveySyncPackageManifest,
        val packageSizeBytes: Long,
        val checksumSha256: String,
        val surveyCount: Int,
        val attachmentCount: Int
    ) : SyncExportResult()

    data class Failure(
        val errorMessage: String,
        val errors: List<String> = emptyList(),
        val exception: Throwable? = null
    ) : SyncExportResult()
}

/**
 * Result of validating an exported .ywsync package.
 */
data class SyncPackageValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val manifest: SurveySyncPackageManifest? = null,
    val metadata: SyncPackageMetadata? = null,
    val surveys: List<SurveyRecord> = emptyList(),
    val revisions: List<SurveyRevisionRecord> = emptyList(),
    val duplicateSurveyUuids: List<String> = emptyList(),
    val isDuplicatePackage: Boolean = false,
    val calculatedChecksum: String = "",
    val checksumVerified: Boolean = false
)

/**
 * Inspection and Preview model for supervisor review before accepting/rejecting import.
 */
data class SyncImportPreview(
    val packageFile: File,
    val packageId: String,
    val sourceDeviceId: String,
    val senderRole: String,
    val senderUsername: String,
    val district: String,
    val governorate: String,
    val creationTimestamp: String,
    val surveyCount: Int,
    val attachmentCount: Int,
    val totalAttachmentSizeBytes: Long,
    val manifestChecksum: String,
    val calculatedChecksum: String,
    val checksumVerified: Boolean,
    val isValidStructure: Boolean,
    val isDuplicatePackage: Boolean,
    val duplicateSurveysCount: Int,
    val newSurveysCount: Int,
    val duplicateSurveyUuids: List<String> = emptyList(),
    val validationErrors: List<String> = emptyList(),
    val validationWarnings: List<String> = emptyList(),
    val surveyItems: List<SurveyRecord> = emptyList(),
    val revisionItems: List<SurveyRevisionRecord> = emptyList()
)

/**
 * Status representation of an imported or inspected package on the supervisor device.
 */
enum class SyncPackageInspectionStatus {
    PENDING_VALIDATION,
    VALID_READY_FOR_REVIEW,
    CHECKSUM_MISMATCH,
    STRUCTURE_INVALID,
    DUPLICATE_PACKAGE_WARNING,
    REJECTED_BY_SUPERVISOR,
    ACCEPTED_STAGED
}

