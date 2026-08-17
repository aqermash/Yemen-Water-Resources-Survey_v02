package com.yemen.watersurvey.domain.model

/**
 * Classification of relationship between an incoming survey and local database state.
 */
enum class RecordConflictType(
    val titleAr: String,
    val descriptionAr: String
) {
    NEW_RECORD(
        titleAr = "استمارة جديدة",
        descriptionAr = "المعرف العالمي surveyUUID غير مسجل محلياً في قاعدة البيانات."
    ),
    UPDATE_AVAILABLE(
        titleAr = "تحديث أحدث متاح",
        descriptionAr = "الاستمارة مسجلة محلياً والمراجعة الواردة تحمل رقم مراجعة أو تاريخ أحدث."
    ),
    DUPLICATE(
        titleAr = "استمارة مكررة ومتطابقة",
        descriptionAr = "نفس المعرف العالمي ونفس المراجعة والبيانات مسجلة مسبقاً بنفس الحالة."
    ),
    CONFLICT(
        titleAr = "تعارض في حقول البيانات",
        descriptionAr = "الاستمارة مسجلة محلياً مع وجود اختلافات في الحقول دون تسلسل مراجعة قطعي."
    )
}

/**
 * Supervisor's explicit decision for an individual survey record during merge review.
 */
enum class SupervisorMergeDecision(
    val labelAr: String
) {
    PENDING_REVIEW("قيد المراجعة"),
    ACCEPT_INCOMING("قبول الوارد"),
    KEEP_EXISTING("الاحتفاظ بالمحلي"),
    REVIEW_LATER("تأجيل للمراجعة اللاحقة")
}

/**
 * Atomic field-level comparison between existing local value and incoming value.
 */
data class FieldDifference(
    val fieldName: String,
    val fieldLabelAr: String,
    val existingValue: String?,
    val incomingValue: String?,
    val isChanged: Boolean = existingValue != incomingValue
)

/**
 * Itemized merge decision model representing a single survey record under review.
 */
data class SurveyMergeItem(
    val surveyUUID: String,
    val recordId: String,
    val formId: String = "WATER_SURVEY_V1",
    val formVersion: String = "1.0",
    val existingRecord: SurveyRecord?,
    val incomingRecord: SurveyRecord,
    val incomingRevisions: List<SurveyRevisionRecord> = emptyList(),
    val conflictType: RecordConflictType,
    val differences: List<FieldDifference> = emptyList(),
    val recommendedAction: SupervisorMergeDecision,
    var supervisorDecision: SupervisorMergeDecision = SupervisorMergeDecision.PENDING_REVIEW,
    var decisionUser: String? = null,
    var decisionTimestamp: String? = null,
    var decisionReason: String? = null
)

/**
 * Summary metrics of all merge items in an imported package.
 */
data class SurveyMergeSummary(
    val totalIncoming: Int,
    val newRecordsCount: Int,
    val updatesCount: Int,
    val duplicatesCount: Int,
    val conflictsCount: Int,
    val decidedAcceptCount: Int,
    val decidedKeepCount: Int,
    val decidedReviewLaterCount: Int,
    val pendingCount: Int
)

/**
 * Result of executing an approved controlled merge operation.
 */
data class ControlledMergeExecutionResult(
    val isSuccess: Boolean,
    val packageId: String,
    val importedNewCount: Int = 0,
    val updatedCount: Int = 0,
    val ignoredDuplicateCount: Int = 0,
    val retainedLocalCount: Int = 0,
    val deferredCount: Int = 0,
    val auditLogsCreated: Int = 0,
    val revisionsCreated: Int = 0,
    val attachmentsImported: Int = 0,
    val errors: List<String> = emptyList(),
    val summaryMessageAr: String = ""
)
