package com.yemen.watersurvey.domain.model

/**
 * Lifecycle states of an offline synchronization package in the Supervisor Workspace.
 */
enum class SyncPackageState(
    val code: String,
    val titleAr: String,
    val descriptionAr: String
) {
    RECEIVED(
        code = "RECEIVED",
        titleAr = "مستلمة في صندوق الوارد",
        descriptionAr = "تم استلام حزمة التبادل محلياً وبانتظار بدء فحص السلامة والتطابق."
    ),
    VALIDATED(
        code = "VALIDATED",
        titleAr = "تم التحقق من السلامة",
        descriptionAr = "تم فحص البصمة الرقمية SHA-256 والهيكل الداخلي بنجاح وخلوها من التلف."
    ),
    REVIEW_PENDING(
        code = "REVIEW_PENDING",
        titleAr = "بانتظار مراجعة المشرف",
        descriptionAr = "تم تحليل التعارضات واكتشاف الفروقات وبانتظار قرارات المشرف الميداني."
    ),
    PARTIALLY_MERGED(
        code = "PARTIALLY_MERGED",
        titleAr = "مدمجة جزئياً",
        descriptionAr = "تم اعتماد ودمج جزء من الاستمارات مع تأجيل بعض الاستمارات لمراجعة لاحقة."
    ),
    MERGED(
        code = "MERGED",
        titleAr = "مدمجة بالكامل",
        descriptionAr = "تم اعتماد ودمج جميع استمارات الحزمة بنجاح في قاعدة البيانات المحلية."
    ),
    REJECTED(
        code = "REJECTED",
        titleAr = "مرفوضة من المشرف",
        descriptionAr = "تم رفض الحزمة بالكامل من قبل المشرف لأسباب فنية أو تدقيقية."
    ),
    ARCHIVED(
        code = "ARCHIVED",
        titleAr = "مؤرشفة",
        descriptionAr = "تمت أرشفة الحزمة بعد إكمال المعالجة لحفظ السجل التاريخي دون التأثير على العمليات الحالية."
    );

    companion object {
        fun fromCode(code: String): SyncPackageState {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: RECEIVED
        }
    }
}

/**
 * Summary descriptor of a sync package managed in the Supervisor Workspace.
 */
data class SyncPackageWorkspaceItem(
    val packageId: String,
    val originalFileName: String,
    val packageFilePath: String,
    val extractedDirPath: String? = null,
    val packageSha256: String,
    val sourceDeviceId: String,
    val senderUsername: String,
    val senderRole: String,
    val governorate: String,
    val district: String,
    val exportTimestamp: String,
    val receivedTimestamp: String,
    val surveyCount: Int,
    val attachmentCount: Int,
    val totalAttachmentSizeBytes: Long,
    val packageSizeBytes: Long,
    val state: SyncPackageState,
    val validationStatus: String,
    val validationErrors: List<String> = emptyList(),
    val validationWarnings: List<String> = emptyList(),
    val newRecordsCount: Int = 0,
    val updatesCount: Int = 0,
    val conflictsCount: Int = 0,
    val duplicatesCount: Int = 0,
    val mergedRecordsCount: Int = 0,
    val lastReviewedAt: String? = null,
    val reviewedBy: String? = null,
    val decisionReason: String? = null,
    val isArchived: Boolean = false,
    val notes: String? = null
)

/**
 * Aggregated workspace metrics and dashboard statistics.
 * All calculations execute 100% offline.
 */
data class WorkspaceDashboardStats(
    val totalReceivedPackages: Int = 0,
    val pendingReviewsCount: Int = 0,
    val validatedReadyCount: Int = 0,
    val completedMergesCount: Int = 0,
    val rejectedPackagesCount: Int = 0,
    val archivedPackagesCount: Int = 0,
    val newRecordsWaiting: Int = 0,
    val updatesWaiting: Int = 0,
    val conflictsWaiting: Int = 0,
    val duplicatesDetected: Int = 0,
    val databaseTotalSurveys: Int = 0,
    val databaseWellsCount: Int = 0,
    val databaseSpringsCount: Int = 0,
    val databaseDamsCount: Int = 0,
    val databaseTotalAttachments: Int = 0,
    val databaseTotalSizeBytes: Long = 0L,
    val totalAuditLogsCount: Int = 0,
    val distinctEnumeratorsCount: Int = 0,
    val distinctDevicesCount: Int = 0
)

/**
 * Historical transition record of a sync package in the workspace.
 */
data class SyncPackageHistoryRecord(
    val historyId: String,
    val packageId: String,
    val fromState: SyncPackageState,
    val toState: SyncPackageState,
    val actorId: String,
    val actorRole: String,
    val timestamp: String,
    val actionDescription: String,
    val details: Map<String, Any?> = emptyMap()
)

/**
 * Detailed enumerator contribution statistics for district-level aggregation.
 */
data class EnumeratorContribution(
    val enumeratorId: String,
    val enumeratorUsername: String,
    val sourceDeviceId: String,
    val submittedPackagesCount: Int,
    val totalSurveysSubmitted: Int,
    val approvedSurveysCount: Int,
    val lastSubmissionDate: String
)

/**
 * District-level survey database aggregation and sync summary.
 * Prepares the architectural model for official multi-level reporting.
 */
data class DistrictSyncSummary(
    val governorate: String,
    val district: String,
    val generationTimestamp: String,
    val totalSurveys: Int,
    val wellsCount: Int,
    val springsCount: Int,
    val damsCount: Int,
    val approvedCount: Int,
    val completedCount: Int,
    val draftCount: Int,
    val totalAttachmentsCount: Int,
    val totalAttachmentSizeBytes: Long,
    val enumeratorSources: List<EnumeratorContribution> = emptyList(),
    val packagesProcessedCount: Int = 0,
    val totalAuditLogsCount: Int = 0,
    val totalRevisionsCount: Int = 0,
    val lastSyncTimestamp: String
)
