package com.yemen.watersurvey.core.sync

import com.yemen.watersurvey.domain.model.*
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Engine responsible for comparing incoming .ysync survey records against
 * existing local Room database records, classifying relationships, and generating
 * atomic field-level difference analyses.
 *
 * Adheres strictly to the Global Identity Rule:
 * - Uses `surveyUUID` as the primary global identifier across devices.
 * - `recordId` is treated as a local device human-readable identifier.
 * - Operates 100% offline and in-memory with zero side-effects on database state.
 */
class ConflictDetectionEngine {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Analyzes incoming survey package records against local database records.
     *
     * @param incomingSurveys List of surveys deserialized from incoming package.
     * @param incomingRevisions List of audit revisions deserialized from incoming package.
     * @param existingSurveys Current local surveys retrieved from Room database.
     * @param existingRevisions Current local audit revisions retrieved from Room database.
     * @return Pair of itemized merge items and summary metrics.
     */
    fun analyzeConflicts(
        incomingSurveys: List<SurveyRecord>,
        incomingRevisions: List<SurveyRevisionRecord> = emptyList(),
        existingSurveys: List<SurveyRecord>,
        existingRevisions: List<SurveyRevisionRecord> = emptyList()
    ): Pair<List<SurveyMergeItem>, SurveyMergeSummary> {
        val existingByUuid = existingSurveys.associateBy { it.surveyUUID }
        val mergeItems = mutableListOf<SurveyMergeItem>()

        for (incoming in incomingSurveys) {
            val uuid = incoming.surveyUUID
            val existing = existingByUuid[uuid]
            val recordRevisions = incomingRevisions.filter { it.surveyUUID == uuid || it.recordId == incoming.recordId }

            val item = if (existing == null) {
                // 1. NEW_RECORD: surveyUUID does not exist locally
                SurveyMergeItem(
                    surveyUUID = uuid,
                    recordId = incoming.recordId,
                    formId = incoming.formId,
                    formVersion = incoming.formVersion,
                    existingRecord = null,
                    incomingRecord = incoming,
                    incomingRevisions = recordRevisions,
                    conflictType = RecordConflictType.NEW_RECORD,
                    differences = emptyList(),
                    recommendedAction = SupervisorMergeDecision.ACCEPT_INCOMING,
                    supervisorDecision = SupervisorMergeDecision.ACCEPT_INCOMING
                )
            } else {
                // Record exists locally: analyze differences
                val diffs = generateFieldDifferences(existing, incoming)

                if (diffs.isEmpty() && incoming.revisionCount <= existing.revisionCount) {
                    // 2. DUPLICATE: Same surveyUUID, same revision and identical content
                    SurveyMergeItem(
                        surveyUUID = uuid,
                        recordId = incoming.recordId,
                        formId = incoming.formId,
                        formVersion = incoming.formVersion,
                        existingRecord = existing,
                        incomingRecord = incoming,
                        incomingRevisions = recordRevisions,
                        conflictType = RecordConflictType.DUPLICATE,
                        differences = emptyList(),
                        recommendedAction = SupervisorMergeDecision.KEEP_EXISTING,
                        supervisorDecision = SupervisorMergeDecision.KEEP_EXISTING
                    )
                } else if (incoming.revisionCount > existing.revisionCount || isTimestampNewer(incoming.updatedAt, existing.updatedAt)) {
                    // 3. UPDATE_AVAILABLE: Same surveyUUID, and incoming revision is newer
                    SurveyMergeItem(
                        surveyUUID = uuid,
                        recordId = incoming.recordId,
                        formId = incoming.formId,
                        formVersion = incoming.formVersion,
                        existingRecord = existing,
                        incomingRecord = incoming,
                        incomingRevisions = recordRevisions,
                        conflictType = RecordConflictType.UPDATE_AVAILABLE,
                        differences = diffs,
                        recommendedAction = SupervisorMergeDecision.ACCEPT_INCOMING,
                        supervisorDecision = SupervisorMergeDecision.ACCEPT_INCOMING
                    )
                } else {
                    // 4. CONFLICT: Values differ without a clear newer revision
                    SurveyMergeItem(
                        surveyUUID = uuid,
                        recordId = incoming.recordId,
                        formId = incoming.formId,
                        formVersion = incoming.formVersion,
                        existingRecord = existing,
                        incomingRecord = incoming,
                        incomingRevisions = recordRevisions,
                        conflictType = RecordConflictType.CONFLICT,
                        differences = diffs,
                        recommendedAction = SupervisorMergeDecision.PENDING_REVIEW,
                        supervisorDecision = SupervisorMergeDecision.PENDING_REVIEW
                    )
                }
            }

            mergeItems.add(item)
        }

        val summary = calculateSummary(mergeItems)
        return Pair(mergeItems, summary)
    }

    /**
     * Performs deep field-level comparison between existing local survey and incoming survey.
     */
    fun generateFieldDifferences(existing: SurveyRecord, incoming: SurveyRecord): List<FieldDifference> {
        val diffs = mutableListOf<FieldDifference>()

        // Survey Type
        if (existing.surveyType != incoming.surveyType) {
            diffs.add(
                FieldDifference(
                    fieldName = "surveyType",
                    fieldLabelAr = "نوع المنشأة المائية",
                    existingValue = existing.surveyType.displayNameAr,
                    incomingValue = incoming.surveyType.displayNameAr
                )
            )
        }

        // Administrative: Governorate (Admin1)
        if (existing.admin1Pcode != incoming.admin1Pcode || existing.governorateNameSnapshotAr != incoming.governorateNameSnapshotAr) {
            diffs.add(
                FieldDifference(
                    fieldName = "admin1Pcode",
                    fieldLabelAr = "المحافظة (الرمز والاسم)",
                    existingValue = "${existing.admin1Pcode} - ${existing.governorateNameSnapshotAr.ifBlank { existing.governorateCode }}",
                    incomingValue = "${incoming.admin1Pcode} - ${incoming.governorateNameSnapshotAr.ifBlank { incoming.governorateCode }}"
                )
            )
        }

        // Administrative: District (Admin2)
        if (existing.admin2Pcode != incoming.admin2Pcode ||
            existing.districtNameSnapshotAr != incoming.districtNameSnapshotAr ||
            existing.districtCode != incoming.districtCode
        ) {
            diffs.add(
                FieldDifference(
                    fieldName = "admin2Pcode",
                    fieldLabelAr = "المديرية (الرمز والاسم)",
                    existingValue = "${existing.admin2Pcode} - ${existing.districtNameSnapshotAr.ifBlank { existing.districtCode }}",
                    incomingValue = "${incoming.admin2Pcode} - ${incoming.districtNameSnapshotAr.ifBlank { incoming.districtCode }}"
                )
            )
        }

        // Administrative: Sub-district (Admin3 / Uzlah)
        if (existing.admin3Pcode != incoming.admin3Pcode || existing.subDistrictNameSnapshotAr != incoming.subDistrictNameSnapshotAr) {
            diffs.add(
                FieldDifference(
                    fieldName = "admin3Pcode",
                    fieldLabelAr = "العزلة / Sub-district",
                    existingValue = "${existing.admin3Pcode} - ${existing.subDistrictNameSnapshotAr.ifBlank { existing.uzlahCode }}",
                    incomingValue = "${incoming.admin3Pcode} - ${incoming.subDistrictNameSnapshotAr.ifBlank { incoming.uzlahCode }}"
                )
            )
        }

        // Administrative: Village & Local Name Override
        if (existing.villageReferenceId != incoming.villageReferenceId ||
            existing.villageNameSnapshotAr != incoming.villageNameSnapshotAr ||
            existing.isLocalNameOverride != incoming.isLocalNameOverride
        ) {
            val exVil = existing.villageNameSnapshotAr ?: existing.villageReferenceId ?: existing.villageCode.ifBlank { "غير محدد" }
            val inVil = incoming.villageNameSnapshotAr ?: incoming.villageReferenceId ?: incoming.villageCode.ifBlank { "غير محدد" }
            diffs.add(
                FieldDifference(
                    fieldName = "villageReferenceId",
                    fieldLabelAr = "القرية / المسمى المحلي",
                    existingValue = exVil + (if (existing.isLocalNameOverride) " (تعديل محلي)" else ""),
                    incomingValue = inVil + (if (incoming.isLocalNameOverride) " (تعديل محلي)" else "")
                )
            )
        }

        // Spatial Verification Status
        if (existing.gpsResolutionStatus != incoming.gpsResolutionStatus) {
            diffs.add(
                FieldDifference(
                    fieldName = "gpsResolutionStatus",
                    fieldLabelAr = "حالة التحقق المكاني",
                    existingValue = existing.gpsResolutionStatus.titleAr,
                    incomingValue = incoming.gpsResolutionStatus.titleAr
                )
            )
        }

        // Workflow Status
        if (existing.workflowStatus != incoming.workflowStatus) {
            diffs.add(
                FieldDifference(
                    fieldName = "workflowStatus",
                    fieldLabelAr = "حالة الاستمارة",
                    existingValue = existing.workflowStatus,
                    incomingValue = incoming.workflowStatus
                )
            )
        }

        // Revision Count
        if (existing.revisionCount != incoming.revisionCount) {
            diffs.add(
                FieldDifference(
                    fieldName = "revisionCount",
                    fieldLabelAr = "رقم المراجعة",
                    existingValue = "${existing.revisionCount}",
                    incomingValue = "${incoming.revisionCount}"
                )
            )
        }

        // GPS Coordinates
        val existingGps = existing.gpsPoint
        val incomingGps = incoming.gpsPoint
        if (existingGps != null || incomingGps != null) {
            val exLat = existingGps?.latitude?.let { "%.5f".format(Locale.US, it) } ?: "غير متوفر"
            val inLat = incomingGps?.latitude?.let { "%.5f".format(Locale.US, it) } ?: "غير متوفر"
            val exLng = existingGps?.longitude?.let { "%.5f".format(Locale.US, it) } ?: "غير متوفر"
            val inLng = incomingGps?.longitude?.let { "%.5f".format(Locale.US, it) } ?: "غير متوفر"

            if (exLat != inLat || exLng != inLng) {
                diffs.add(
                    FieldDifference(
                        fieldName = "gpsCoordinates",
                        fieldLabelAr = "إحداثيات GPS (العرض / الطول)",
                        existingValue = "$exLat, $exLng",
                        incomingValue = "$inLat, $inLng"
                    )
                )
            }

            val exAcc = existingGps?.accuracyM?.let { "±${it}m" } ?: ""
            val inAcc = incomingGps?.accuracyM?.let { "±${it}m" } ?: ""
            if (exAcc != inAcc && (existingGps != null && incomingGps != null)) {
                diffs.add(
                    FieldDifference(
                        fieldName = "gpsAccuracy",
                        fieldLabelAr = "دقة موقع GPS",
                        existingValue = exAcc,
                        incomingValue = inAcc
                    )
                )
            }
        }

        // Well Details
        val exWell = existing.wellDetails
        val inWell = incoming.wellDetails
        if (exWell != null || inWell != null) {
            if (exWell?.wellNameAr != inWell?.wellNameAr) {
                diffs.add(
                    FieldDifference(
                        fieldName = "wellNameAr",
                        fieldLabelAr = "اسم البئر",
                        existingValue = exWell?.wellNameAr ?: "—",
                        incomingValue = inWell?.wellNameAr ?: "—"
                    )
                )
            }
            if (exWell?.wellType != inWell?.wellType) {
                diffs.add(
                    FieldDifference(
                        fieldName = "wellType",
                        fieldLabelAr = "نوع البئر",
                        existingValue = exWell?.wellType ?: "—",
                        incomingValue = inWell?.wellType ?: "—"
                    )
                )
            }
            if (exWell?.wellDepthM != inWell?.wellDepthM) {
                diffs.add(
                    FieldDifference(
                        fieldName = "wellDepthM",
                        fieldLabelAr = "عمق البئر (متر)",
                        existingValue = exWell?.wellDepthM?.let { "${it}م" } ?: "—",
                        incomingValue = inWell?.wellDepthM?.let { "${it}م" } ?: "—"
                    )
                )
            }
            if (exWell?.pumpingMechanism != inWell?.pumpingMechanism) {
                diffs.add(
                    FieldDifference(
                        fieldName = "pumpingMechanism",
                        fieldLabelAr = "آلية الضخ",
                        existingValue = exWell?.pumpingMechanism ?: "—",
                        incomingValue = inWell?.pumpingMechanism ?: "—"
                    )
                )
            }
            if (exWell?.operationalStatus != inWell?.operationalStatus) {
                diffs.add(
                    FieldDifference(
                        fieldName = "operationalStatus",
                        fieldLabelAr = "الحالة التشغيلية",
                        existingValue = exWell?.operationalStatus ?: "—",
                        incomingValue = inWell?.operationalStatus ?: "—"
                    )
                )
            }
        }

        // Spring Details
        val exSpring = existing.springDetails
        val inSpring = incoming.springDetails
        if (exSpring != null || inSpring != null) {
            if (exSpring?.springNameAr != inSpring?.springNameAr) {
                diffs.add(
                    FieldDifference(
                        fieldName = "springNameAr",
                        fieldLabelAr = "اسم الينبوع / العين",
                        existingValue = exSpring?.springNameAr ?: "—",
                        incomingValue = inSpring?.springNameAr ?: "—"
                    )
                )
            }
            if (exSpring?.flowRateLps != inSpring?.flowRateLps) {
                diffs.add(
                    FieldDifference(
                        fieldName = "flowRateLps",
                        fieldLabelAr = "معدل التدفق (لتر/ثانية)",
                        existingValue = exSpring?.flowRateLps?.let { "$it ل/ث" } ?: "—",
                        incomingValue = inSpring?.flowRateLps?.let { "$it ل/ث" } ?: "—"
                    )
                )
            }
            if (exSpring?.waterClarity != inSpring?.waterClarity) {
                diffs.add(
                    FieldDifference(
                        fieldName = "waterClarity",
                        fieldLabelAr = "عكارة ونقاء المياه",
                        existingValue = exSpring?.waterClarity ?: "—",
                        incomingValue = inSpring?.waterClarity ?: "—"
                    )
                )
            }
            if (exSpring?.dischargeSeasonality != inSpring?.dischargeSeasonality) {
                diffs.add(
                    FieldDifference(
                        fieldName = "dischargeSeasonality",
                        fieldLabelAr = "موسمية التدفق",
                        existingValue = exSpring?.dischargeSeasonality ?: "—",
                        incomingValue = inSpring?.dischargeSeasonality ?: "—"
                    )
                )
            }
        }

        // Dam Details
        val exDam = existing.damDetails
        val inDam = incoming.damDetails
        if (exDam != null || inDam != null) {
            if (exDam?.damNameAr != inDam?.damNameAr) {
                diffs.add(
                    FieldDifference(
                        fieldName = "damNameAr",
                        fieldLabelAr = "اسم السد / الحاجز",
                        existingValue = exDam?.damNameAr ?: "—",
                        incomingValue = inDam?.damNameAr ?: "—"
                    )
                )
            }
            if (exDam?.structureType != inDam?.structureType) {
                diffs.add(
                    FieldDifference(
                        fieldName = "structureType",
                        fieldLabelAr = "نوع المنشأة الإنشائية",
                        existingValue = exDam?.structureType ?: "—",
                        incomingValue = inDam?.structureType ?: "—"
                    )
                )
            }
            if (exDam?.storageCapacityM3 != inDam?.storageCapacityM3) {
                diffs.add(
                    FieldDifference(
                        fieldName = "storageCapacityM3",
                        fieldLabelAr = "السعة التخزينية (متر مكعب)",
                        existingValue = exDam?.storageCapacityM3?.let { "$it م³" } ?: "—",
                        incomingValue = inDam?.storageCapacityM3?.let { "$it م³" } ?: "—"
                    )
                )
            }
            if (exDam?.damHeightM != inDam?.damHeightM) {
                diffs.add(
                    FieldDifference(
                        fieldName = "damHeightM",
                        fieldLabelAr = "ارتفاع السد (متر)",
                        existingValue = exDam?.damHeightM?.let { "${it}م" } ?: "—",
                        incomingValue = inDam?.damHeightM?.let { "${it}م" } ?: "—"
                    )
                )
            }
            if (exDam?.structuralCondition != inDam?.structuralCondition) {
                diffs.add(
                    FieldDifference(
                        fieldName = "structuralCondition",
                        fieldLabelAr = "الحالة الإنشائية",
                        existingValue = exDam?.structuralCondition ?: "—",
                        incomingValue = inDam?.structuralCondition ?: "—"
                    )
                )
            }
        }

        // Attachments Count
        if (existing.attachments.size != incoming.attachments.size) {
            diffs.add(
                FieldDifference(
                    fieldName = "attachmentsCount",
                    fieldLabelAr = "عدد المرفقات والصور",
                    existingValue = "${existing.attachments.size} ملفات",
                    incomingValue = "${incoming.attachments.size} ملفات"
                )
            )
        }

        return diffs
    }

    /**
     * Calculates summary counters from itemized merge list.
     */
    fun calculateSummary(items: List<SurveyMergeItem>): SurveyMergeSummary {
        var newCount = 0
        var updatesCount = 0
        var duplicatesCount = 0
        var conflictsCount = 0
        var acceptCount = 0
        var keepCount = 0
        var reviewLaterCount = 0
        var pendingCount = 0

        for (item in items) {
            when (item.conflictType) {
                RecordConflictType.NEW_RECORD -> newCount++
                RecordConflictType.UPDATE_AVAILABLE -> updatesCount++
                RecordConflictType.DUPLICATE -> duplicatesCount++
                RecordConflictType.CONFLICT -> conflictsCount++
            }

            when (item.supervisorDecision) {
                SupervisorMergeDecision.ACCEPT_INCOMING -> acceptCount++
                SupervisorMergeDecision.KEEP_EXISTING -> keepCount++
                SupervisorMergeDecision.REVIEW_LATER -> reviewLaterCount++
                SupervisorMergeDecision.PENDING_REVIEW -> pendingCount++
            }
        }

        return SurveyMergeSummary(
            totalIncoming = items.size,
            newRecordsCount = newCount,
            updatesCount = updatesCount,
            duplicatesCount = duplicatesCount,
            conflictsCount = conflictsCount,
            decidedAcceptCount = acceptCount,
            decidedKeepCount = keepCount,
            decidedReviewLaterCount = reviewLaterCount,
            pendingCount = pendingCount
        )
    }

    private fun isTimestampNewer(incomingTs: String, existingTs: String): Boolean {
        return try {
            val d1 = dateFormat.parse(incomingTs)
            val d2 = dateFormat.parse(existingTs)
            if (d1 != null && d2 != null) {
                d1.after(d2)
            } else {
                incomingTs > existingTs
            }
        } catch (e: Exception) {
            incomingTs > existingTs
        }
    }
}
