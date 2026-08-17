package com.yemen.watersurvey.core.sync

import android.content.Context
import com.yemen.watersurvey.data.database.SurveyAppDatabase
import com.yemen.watersurvey.data.entity.*
import com.yemen.watersurvey.domain.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

/**
 * Controlled Offline Merge Execution Service.
 *
 * Implements strict database transaction controls:
 * 1. Executes database mutations ONLY for items with explicit supervisor approval.
 * 2. Never overwrites an existing record without first creating an immutable
 *    `SurveyRevisionEntity` historical snapshot.
 * 3. Creates auditable `AuditLogEntity` records for every imported, updated, or kept survey.
 * 4. Deduplicates attachments by SHA-256 hash and links them by `surveyUUID`.
 * 5. 100% offline, zero network reliance.
 */
class ControlledMergeExecutor(
    private val context: Context,
    private val database: SurveyAppDatabase
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Executes the controlled merge of supervisor-reviewed items into Room database.
     *
     * @param packageId Identifier of the source sync package.
     * @param packageExtractedDir Temporary directory containing extracted package contents (for attachments).
     * @param reviewedItems List of items with supervisor decisions.
     * @param supervisorActorId Identifier/username of the supervisor executing the merge.
     * @param supervisorRole Role of the supervisor (e.g. DISTRICT_SUPERVISOR, GOVERNORATE_SUPERVISOR).
     * @param globalReason General reason for this merge session.
     */
    suspend fun executeMerge(
        packageId: String,
        packageExtractedDir: File? = null,
        reviewedItems: List<SurveyMergeItem>,
        supervisorActorId: String = "supervisor_admin",
        supervisorRole: String = "DISTRICT_SUPERVISOR",
        globalReason: String = "Controlled offline sync package ingestion"
    ): ControlledMergeExecutionResult {
        var importedNewCount = 0
        var updatedCount = 0
        var ignoredDuplicateCount = 0
        var retainedLocalCount = 0
        var deferredCount = 0
        var auditLogsCreated = 0
        var revisionsCreated = 0
        var attachmentsImported = 0
        val errors = mutableListOf<String>()

        val nowIso = dateFormat.format(Date())

        for (item in reviewedItems) {
            try {
                val effectiveReason = item.decisionReason?.ifBlank { globalReason } ?: globalReason

                when (item.supervisorDecision) {
                    SupervisorMergeDecision.ACCEPT_INCOMING -> {
                        if (item.conflictType == RecordConflictType.NEW_RECORD) {
                            // --- NEW RECORD INGESTION ---
                            val entity = mapRecordToEntity(
                                record = item.incomingRecord,
                                lastModifiedBy = supervisorActorId,
                                sourcePackageId = packageId
                            )
                            database.surveyRecordDao().insertOrUpdateSurvey(entity)
                            importedNewCount++

                            // Import attachments
                            val attCount = importAttachmentsForSurvey(
                                survey = item.incomingRecord,
                                packageDir = packageExtractedDir,
                                sourcePackageId = packageId
                            )
                            attachmentsImported += attCount

                            // Ingest accompanying historical revisions
                            for (rev in item.incomingRevisions) {
                                val revEntity = SurveyRevisionEntity(
                                    revisionId = rev.revisionId.ifBlank { UUID.randomUUID().toString() },
                                    surveyUUID = item.surveyUUID,
                                    recordId = item.recordId,
                                    revisionNumber = rev.revisionNumber,
                                    modifiedBy = rev.modifiedBy.ifBlank { item.incomingRecord.enumeratorUsername },
                                    modifiedAt = rev.modifiedAt.ifBlank { nowIso },
                                    reasonForChange = rev.reasonForChange.ifBlank { "Initial field submission" },
                                    previousStatus = rev.previousStatus,
                                    newStatus = rev.newStatus,
                                    changedFieldsJson = JSONArray(rev.changedFields).toString(),
                                    sourcePackageId = packageId
                                )
                                database.surveyRevisionDao().insertRevision(revEntity)
                                revisionsCreated++
                            }

                            // Create Audit Log
                            val auditLog = AuditLogEntity(
                                logId = UUID.randomUUID().toString(),
                                surveyUUID = item.surveyUUID,
                                recordId = item.recordId,
                                actionType = "IMPORT_NEW_RECORD",
                                actorId = supervisorActorId,
                                actorRole = supervisorRole,
                                timestamp = nowIso,
                                decisionReason = effectiveReason,
                                detailsJson = JSONObject().apply {
                                    put("packageId", packageId)
                                    put("surveyType", item.incomingRecord.surveyType.name)
                                    put("enumerator", item.incomingRecord.enumeratorUsername)
                                    put("attachmentsCount", attCount)
                                }.toString(),
                                packageId = packageId
                            )
                            database.auditLogDao().insertAuditLog(auditLog)
                            auditLogsCreated++

                        } else {
                            // --- UPDATE OR RESOLVED CONFLICT INGESTION ---
                            // 1. Snapshot existing local record before updating
                            val existingEntity = database.surveyRecordDao().getSurveyByUUID(item.surveyUUID)
                            if (existingEntity != null) {
                                val snapshotJson = serializeEntityToJson(existingEntity)
                                val diffList = item.differences.map { it.fieldName }

                                val revisionSnapshot = SurveyRevisionEntity(
                                    revisionId = UUID.randomUUID().toString(),
                                    surveyUUID = item.surveyUUID,
                                    recordId = item.recordId,
                                    revisionNumber = existingEntity.revisionCount,
                                    modifiedBy = supervisorActorId,
                                    modifiedAt = nowIso,
                                    reasonForChange = "Pre-merge snapshot before applying incoming package $packageId (${item.conflictType.name}): $effectiveReason",
                                    previousStatus = existingEntity.workflowStatus,
                                    newStatus = item.incomingRecord.workflowStatus,
                                    changedFieldsJson = JSONArray(diffList).toString(),
                                    snapshotDataJson = snapshotJson,
                                    sourcePackageId = packageId
                                )
                                database.surveyRevisionDao().insertRevision(revisionSnapshot)
                                revisionsCreated++
                            }

                            // 2. Apply incoming update to main survey table
                            val newRevisionCount = maxOf(
                                (existingEntity?.revisionCount ?: 0) + 1,
                                item.incomingRecord.revisionCount
                            )
                            val updatedRecord = item.incomingRecord.copy(revisionCount = newRevisionCount)
                            val updatedEntity = mapRecordToEntity(
                                record = updatedRecord,
                                lastModifiedBy = supervisorActorId,
                                sourcePackageId = packageId
                            )
                            database.surveyRecordDao().insertOrUpdateSurvey(updatedEntity)
                            updatedCount++

                            // 3. Process new attachments
                            val attCount = importAttachmentsForSurvey(
                                survey = item.incomingRecord,
                                packageDir = packageExtractedDir,
                                sourcePackageId = packageId
                            )
                            attachmentsImported += attCount

                            // 4. Create Audit Log
                            val actionName = if (item.conflictType == RecordConflictType.CONFLICT) {
                                "CONFLICT_RESOLVED_ACCEPT_INCOMING"
                            } else {
                                "UPDATE_APPLIED_FROM_PACKAGE"
                            }

                            val auditLog = AuditLogEntity(
                                logId = UUID.randomUUID().toString(),
                                surveyUUID = item.surveyUUID,
                                recordId = item.recordId,
                                actionType = actionName,
                                actorId = supervisorActorId,
                                actorRole = supervisorRole,
                                timestamp = nowIso,
                                decisionReason = effectiveReason,
                                detailsJson = JSONObject().apply {
                                    put("packageId", packageId)
                                    put("conflictType", item.conflictType.name)
                                    put("changedFieldsCount", item.differences.size)
                                    put("newRevisionNumber", newRevisionCount)
                                }.toString(),
                                packageId = packageId
                            )
                            database.auditLogDao().insertAuditLog(auditLog)
                            auditLogsCreated++
                        }
                    }

                    SupervisorMergeDecision.KEEP_EXISTING -> {
                        if (item.conflictType == RecordConflictType.DUPLICATE) {
                            // Ignored duplicate - no changes
                            ignoredDuplicateCount++
                        } else {
                            // Explicit retention of local state
                            retainedLocalCount++

                            val auditLog = AuditLogEntity(
                                logId = UUID.randomUUID().toString(),
                                surveyUUID = item.surveyUUID,
                                recordId = item.recordId,
                                actionType = "CONFLICT_RESOLVED_KEEP_LOCAL",
                                actorId = supervisorActorId,
                                actorRole = supervisorRole,
                                timestamp = nowIso,
                                decisionReason = effectiveReason,
                                detailsJson = JSONObject().apply {
                                    put("packageId", packageId)
                                    put("conflictType", item.conflictType.name)
                                    put("retainedLocalRevision", item.existingRecord?.revisionCount ?: 1)
                                }.toString(),
                                packageId = packageId
                            )
                            database.auditLogDao().insertAuditLog(auditLog)
                            auditLogsCreated++
                        }
                    }

                    SupervisorMergeDecision.REVIEW_LATER,
                    SupervisorMergeDecision.PENDING_REVIEW -> {
                        deferredCount++
                    }
                }
            } catch (e: Exception) {
                errors.add("خطأ أثناء معالجة الاستمارة ${item.recordId} (${item.surveyUUID}): ${e.message}")
            }
        }

        val summaryMessage = "تم الانتهاء من عملية الدمج الخاضعة للرقابة بنجاح: " +
                "($importedNewCount) جديدة، ($updatedCount) محدثة، " +
                "($retainedLocalCount) محتفظ بها، ($ignoredDuplicateCount) مكررة متجاهلة، " +
                "($deferredCount) مؤجلة، ($auditLogsCreated) سجل رقابي، ($revisionsCreated) لقطة مراجعة."

        return ControlledMergeExecutionResult(
            isSuccess = errors.isEmpty(),
            packageId = packageId,
            importedNewCount = importedNewCount,
            updatedCount = updatedCount,
            ignoredDuplicateCount = ignoredDuplicateCount,
            retainedLocalCount = retainedLocalCount,
            deferredCount = deferredCount,
            auditLogsCreated = auditLogsCreated,
            revisionsCreated = revisionsCreated,
            attachmentsImported = attachmentsImported,
            errors = errors,
            summaryMessageAr = summaryMessage
        )
    }

    /**
     * Imports and copies attachments into app storage, deduplicating files by SHA-256 hash.
     */
    private suspend fun importAttachmentsForSurvey(
        survey: SurveyRecord,
        packageDir: File?,
        sourcePackageId: String
    ): Int {
        if (survey.attachments.isEmpty()) return 0

        val appSurveyMediaDir = File(context.filesDir, "surveys/${survey.surveyUUID}/attachments").apply {
            mkdirs()
        }

        var imported = 0
        for (att in survey.attachments) {
            val sourceFile = resolveAttachmentSourceFile(att, packageDir, survey.recordId)
            val fileSha256 = if (att.fileSha256.isNotBlank()) {
                att.fileSha256
            } else if (sourceFile != null && sourceFile.exists()) {
                calculateFileSha256(sourceFile)
            } else {
                ""
            }

            // Check if identical file already recorded
            val existingAtt = if (fileSha256.isNotBlank()) {
                database.surveyAttachmentDao().findAttachmentBySha256(fileSha256)
            } else {
                database.surveyAttachmentDao().findAttachmentByName(survey.surveyUUID, att.fileName)
            }

            val finalLocalPath: String
            if (existingAtt != null && File(existingAtt.localFilePath).exists()) {
                // Reuse existing physical file path
                finalLocalPath = existingAtt.localFilePath
            } else if (sourceFile != null && sourceFile.exists()) {
                val targetFile = File(appSurveyMediaDir, att.fileName)
                sourceFile.copyTo(targetFile, overwrite = true)
                finalLocalPath = targetFile.absolutePath
            } else {
                finalLocalPath = att.filePath
            }

            val attachmentEntity = SurveyAttachmentEntity(
                attachmentId = att.attachmentId.ifBlank { UUID.randomUUID().toString() },
                surveyUUID = survey.surveyUUID,
                recordId = survey.recordId,
                attachmentType = att.attachmentType,
                localFilePath = finalLocalPath,
                fileName = att.fileName,
                fileSizeBytes = att.fileSize,
                fileSha256 = fileSha256,
                capturedAt = att.timestamp,
                sourcePackageId = sourcePackageId
            )
            database.surveyAttachmentDao().insertAttachment(attachmentEntity)
            imported++
        }

        return imported
    }

    private fun resolveAttachmentSourceFile(att: AttachmentInfo, packageDir: File?, recordId: String): File? {
        if (packageDir != null && packageDir.exists()) {
            val inPackage = File(packageDir, "attachments/$recordId/${att.fileName}")
            if (inPackage.exists()) return inPackage

            val relPath = File(packageDir, att.filePath)
            if (relPath.exists()) return relPath
        }

        val direct = File(att.filePath)
        if (direct.exists()) return direct

        return null
    }

    private fun calculateFileSha256(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    private fun mapRecordToEntity(
        record: SurveyRecord,
        lastModifiedBy: String,
        sourcePackageId: String
    ): SurveyRecordEntity {
        return SurveyRecordEntity(
            surveyUUID = record.surveyUUID,
            recordId = record.recordId,
            formId = record.formId,
            formVersion = record.formVersion,
            surveyType = record.surveyType.name,
            admin1Pcode = record.admin1Pcode,
            admin2Pcode = record.admin2Pcode,
            admin3Pcode = record.admin3Pcode,
            villageReferenceId = record.villageReferenceId,
            governorateCode = record.governorateCode,
            districtCode = record.districtCode,
            uzlahCode = record.uzlahCode,
            villageCode = record.villageCode,
            governorateNameSnapshotAr = record.governorateNameSnapshotAr,
            districtNameSnapshotAr = record.districtNameSnapshotAr,
            subDistrictNameSnapshotAr = record.subDistrictNameSnapshotAr,
            villageNameSnapshotAr = record.villageNameSnapshotAr,
            isLocalNameOverride = record.isLocalNameOverride,
            localOverrideId = record.localOverrideId,
            enumeratorId = record.enumeratorId,
            enumeratorUsername = record.enumeratorUsername,
            workflowStatus = record.workflowStatus,
            revisionCount = record.revisionCount,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
            latitude = record.gpsPoint?.latitude,
            longitude = record.gpsPoint?.longitude,
            altitudeM = record.gpsPoint?.altitudeM,
            accuracyM = record.gpsPoint?.accuracyM,
            gpsQuality = record.gpsPoint?.quality?.name,
            gpsCapturedAt = record.gpsPoint?.capturedAt,
            gpsProvider = record.gpsPoint?.provider,
            gpsResolutionStatus = record.gpsResolutionStatus.name,
            gpsResolvedAdmin1Pcode = record.gpsResolvedAdmin1Pcode,
            gpsResolvedAdmin2Pcode = record.gpsResolvedAdmin2Pcode,
            gpsResolvedAdmin3Pcode = record.gpsResolvedAdmin3Pcode,
            gpsDistanceToNearestVillageM = record.gpsDistanceToNearestVillageM,
            gpsNearestVillageNameAr = record.gpsNearestVillageNameAr,
            wellDetailsJson = record.wellDetails?.let { w ->
                JSONObject().apply {
                    put("wellNameAr", w.wellNameAr)
                    put("wellType", w.wellType)
                    put("wellDepthM", w.wellDepthM)
                    put("pumpingMechanism", w.pumpingMechanism)
                    put("operationalStatus", w.operationalStatus)
                }.toString()
            },
            springDetailsJson = record.springDetails?.let { s ->
                JSONObject().apply {
                    put("springNameAr", s.springNameAr)
                    put("flowRateLps", s.flowRateLps)
                    put("waterClarity", s.waterClarity)
                    put("dischargeSeasonality", s.dischargeSeasonality)
                }.toString()
            },
            damDetailsJson = record.damDetails?.let { d ->
                JSONObject().apply {
                    put("damNameAr", d.damNameAr)
                    put("structureType", d.structureType)
                    put("storageCapacityM3", d.storageCapacityM3)
                    put("damHeightM", d.damHeightM)
                    put("structuralCondition", d.structuralCondition)
                }.toString()
            },
            attachmentsJson = JSONArray().apply {
                for (a in record.attachments) {
                    put(JSONObject().apply {
                        put("attachmentId", a.attachmentId)
                        put("surveyUUID", a.surveyUUID)
                        put("fileName", a.fileName)
                        put("filePath", a.filePath)
                        put("fileSize", a.fileSize)
                        put("fileSha256", a.fileSha256)
                    })
                }
            }.toString(),
            lastModifiedBy = lastModifiedBy,
            lastSourcePackageId = sourcePackageId,
            adminRefVersionTag = record.adminRefVersionTag
        )
    }

    private fun serializeEntityToJson(entity: SurveyRecordEntity): String {
        return JSONObject().apply {
            put("surveyUUID", entity.surveyUUID)
            put("recordId", entity.recordId)
            put("formId", entity.formId)
            put("formVersion", entity.formVersion)
            put("surveyType", entity.surveyType)
            put("admin1Pcode", entity.admin1Pcode)
            put("admin2Pcode", entity.admin2Pcode)
            put("admin3Pcode", entity.admin3Pcode)
            put("villageReferenceId", entity.villageReferenceId ?: JSONObject.NULL)
            put("governorateCode", entity.governorateCode)
            put("districtCode", entity.districtCode)
            put("uzlahCode", entity.uzlahCode)
            put("villageCode", entity.villageCode)
            put("governorateNameSnapshotAr", entity.governorateNameSnapshotAr)
            put("districtNameSnapshotAr", entity.districtNameSnapshotAr)
            put("subDistrictNameSnapshotAr", entity.subDistrictNameSnapshotAr)
            put("villageNameSnapshotAr", entity.villageNameSnapshotAr ?: JSONObject.NULL)
            put("isLocalNameOverride", entity.isLocalNameOverride)
            put("localOverrideId", entity.localOverrideId ?: JSONObject.NULL)
            put("enumeratorId", entity.enumeratorId)
            put("enumeratorUsername", entity.enumeratorUsername)
            put("workflowStatus", entity.workflowStatus)
            put("revisionCount", entity.revisionCount)
            put("createdAt", entity.createdAt)
            put("updatedAt", entity.updatedAt)
            put("latitude", entity.latitude ?: JSONObject.NULL)
            put("longitude", entity.longitude ?: JSONObject.NULL)
            put("altitudeM", entity.altitudeM ?: JSONObject.NULL)
            put("accuracyM", entity.accuracyM ?: JSONObject.NULL)
            put("gpsQuality", entity.gpsQuality ?: JSONObject.NULL)
            put("gpsResolutionStatus", entity.gpsResolutionStatus)
            put("gpsResolvedAdmin1Pcode", entity.gpsResolvedAdmin1Pcode ?: JSONObject.NULL)
            put("gpsResolvedAdmin2Pcode", entity.gpsResolvedAdmin2Pcode ?: JSONObject.NULL)
            put("gpsResolvedAdmin3Pcode", entity.gpsResolvedAdmin3Pcode ?: JSONObject.NULL)
            put("gpsDistanceToNearestVillageM", entity.gpsDistanceToNearestVillageM ?: JSONObject.NULL)
            put("gpsNearestVillageNameAr", entity.gpsNearestVillageNameAr ?: JSONObject.NULL)
            put("wellDetailsJson", entity.wellDetailsJson ?: JSONObject.NULL)
            put("springDetailsJson", entity.springDetailsJson ?: JSONObject.NULL)
            put("damDetailsJson", entity.damDetailsJson ?: JSONObject.NULL)
            put("adminRefVersionTag", entity.adminRefVersionTag)
        }.toString()
    }
}
