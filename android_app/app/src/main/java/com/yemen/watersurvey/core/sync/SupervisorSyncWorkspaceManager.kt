package com.yemen.watersurvey.core.sync

import android.content.Context
import com.yemen.watersurvey.data.database.SurveyAppDatabase
import com.yemen.watersurvey.data.entity.AuditLogEntity
import com.yemen.watersurvey.data.entity.SyncPackageEntity
import com.yemen.watersurvey.data.entity.SyncPackageHistoryEntity
import com.yemen.watersurvey.domain.model.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

/**
 * Offline Supervisor Synchronization Workspace Manager.
 *
 * Coordinates multi-package data exchange, offline inbox management,
 * lifecycle state transitions, conflict preparation, and district-level aggregation.
 *
 * Guarantees:
 * 1. 100% Offline-first operation.
 * 2. Zero silent overwrites or automatic data mutations.
 * 3. Immutable audit logs for all supervisor transitions.
 * 4. Duplicate package detection and rejection via packageId and SHA-256.
 * 5. Isolation of multiple enumerator packages until explicit supervisor approval.
 */
class SupervisorSyncWorkspaceManager(
    private val context: Context,
    private val database: SurveyAppDatabase
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val syncImporter = SurveySyncImporter(context)
    private val conflictEngine = ConflictDetectionEngine()

    /**
     * Scans storage directories (inbox, staged, exports) and registers discovered packages.
     */
    suspend fun discoverAndRegisterPackages(
        supervisorActorId: String = "supervisor_admin",
        supervisorRole: String = "DISTRICT_SUPERVISOR"
    ): List<SyncPackageWorkspaceItem> {
        val syncDir = File(context.filesDir, "sync_packages").apply { if (!exists()) mkdirs() }
        val stagedDir = File(context.filesDir, "staged_packages").apply { if (!exists()) mkdirs() }
        val cacheDir = File(context.cacheDir, "sync_temp").apply { if (!exists()) mkdirs() }

        val foundFiles = mutableListOf<File>()
        listOf(syncDir, stagedDir, cacheDir).forEach { dir ->
            dir.listFiles { file -> file.isFile && file.name.endsWith(".ywsync") }?.let {
                foundFiles.addAll(it)
            }
        }

        for (file in foundFiles) {
            registerPackageFile(file, supervisorActorId, supervisorRole)
        }

        return getWorkspacePackages()
    }

    /**
     * Registers a single `.ywsync` file into the supervisor workspace database.
     * Prevents duplicate imports using package ID and SHA-256 checksum.
     */
    suspend fun registerPackageFile(
        packageFile: File,
        supervisorActorId: String = "supervisor_admin",
        supervisorRole: String = "DISTRICT_SUPERVISOR"
    ): Result<SyncPackageEntity> {
        if (!packageFile.exists() || !packageFile.canRead()) {
            return Result.failure(IllegalArgumentException("ملف الحزمة غير موجود أو غير قابل للقراءة: ${packageFile.absolutePath}"))
        }

        val calculatedSha256 = calculateFileSha256(packageFile)
        val nowIso = dateFormat.format(Date())

        // 1. Check if SHA-256 already exists in database
        val existingBySha256 = database.syncPackageDao().findPackageBySha256(calculatedSha256)
        if (existingBySha256 != null) {
            return Result.failure(IllegalStateException("الحزمة مسجلة مسبقاً بنفس البصمة الرقمية SHA-256 (معرف الحزمة: ${existingBySha256.packageId})"))
        }

        // 2. Perform safe preliminary extraction to read manifest
        val inspection = syncImporter.inspectAndValidatePackage(packageFile)
        val packageId = inspection.packageId.ifBlank { "PKG-${packageFile.nameWithoutExtension}" }

        // 3. Check if Package ID already exists
        val existingById = database.syncPackageDao().getPackageById(packageId)
        if (existingById != null) {
            return Result.failure(IllegalStateException("معرف الحزمة مسجل مسبقاً في قاعدة بيانات المشرف: $packageId"))
        }

        val initialEntity = SyncPackageEntity(
            packageId = packageId,
            originalFileName = packageFile.name,
            packageFilePath = packageFile.absolutePath,
            extractedDirPath = null,
            packageSha256 = calculatedSha256,
            sourceDeviceId = inspection.sourceDeviceId,
            senderUsername = inspection.senderUsername,
            senderRole = inspection.senderRole,
            governorate = inspection.governorate,
            district = inspection.district,
            exportTimestamp = inspection.creationTimestamp.ifBlank { nowIso },
            receivedTimestamp = nowIso,
            surveyCount = inspection.surveyCount,
            attachmentCount = inspection.attachmentCount,
            totalAttachmentSizeBytes = inspection.totalAttachmentSizeBytes,
            packageSizeBytes = packageFile.length(),
            state = SyncPackageState.RECEIVED.code,
            validationStatus = if (inspection.checksumVerified) "VALID" else if (inspection.validationErrors.isNotEmpty()) "INVALID" else "PENDING",
            validationErrorsJson = JSONArray(inspection.validationErrors).toString(),
            validationWarningsJson = JSONArray(inspection.validationWarnings).toString(),
            newRecordsCount = inspection.newSurveysCount,
            updatesCount = 0,
            conflictsCount = 0,
            duplicatesCount = inspection.duplicateSurveysCount,
            mergedRecordsCount = 0,
            isArchived = false
        )

        database.syncPackageDao().insertOrUpdatePackage(initialEntity)

        // Record history log
        recordHistoryTransition(
            packageId = packageId,
            fromState = SyncPackageState.RECEIVED,
            toState = SyncPackageState.RECEIVED,
            actorId = supervisorActorId,
            actorRole = supervisorRole,
            description = "استلام حزمة التبادل الميداني وتسجيلها في صندوق الوارد",
            details = mapOf("sha256" to calculatedSha256, "file" to packageFile.name)
        )

        // Write Audit Log
        database.auditLogDao().insertAuditLog(
            AuditLogEntity(
                logId = UUID.randomUUID().toString(),
                surveyUUID = "WORKSPACE-SYNC",
                recordId = packageId,
                actionType = "SYNC_PACKAGE_RECEIVED",
                actorId = supervisorActorId,
                actorRole = supervisorRole,
                timestamp = nowIso,
                decisionReason = "Received and registered sync package in local workspace inbox",
                packageId = packageId,
                detailsJson = JSONObject().apply {
                    put("fileName", packageFile.name)
                    put("fileSize", packageFile.length())
                    put("sha256", calculatedSha256)
                    put("surveyCount", inspection.surveyCount)
                }.toString()
            )
        )

        return Result.success(initialEntity)
    }

    /**
     * Validates package integrity and verifies SHA-256 checksums.
     * Transitions state from RECEIVED -> VALIDATED.
     */
    suspend fun validatePackageIntegrity(
        packageId: String,
        supervisorActorId: String = "supervisor_admin",
        supervisorRole: String = "DISTRICT_SUPERVISOR"
    ): Result<SyncImportPreview> {
        val entity = database.syncPackageDao().getPackageById(packageId)
            ?: return Result.failure(IllegalArgumentException("الحزمة غير موجودة في قاعدة البيانات: $packageId"))

        val file = File(entity.packageFilePath)
        if (!file.exists()) {
            return Result.failure(IllegalStateException("ملف الحزمة غير متوفر على مسار التخزين: ${entity.packageFilePath}"))
        }

        val inspection = syncImporter.inspectAndValidatePackage(file)
        val nowIso = dateFormat.format(Date())

        if (!inspection.checksumVerified || !inspection.isValidStructure) {
            val errorsJson = JSONArray(inspection.validationErrors).toString()
            val warningsJson = JSONArray(inspection.validationWarnings).toString()
            database.syncPackageDao().insertOrUpdatePackage(
                entity.copy(
                    validationStatus = if (!inspection.checksumVerified) "CHECKSUM_MISMATCH" else "STRUCTURE_INVALID",
                    validationErrorsJson = errorsJson,
                    validationWarningsJson = warningsJson,
                    lastReviewedAt = nowIso,
                    reviewedBy = supervisorActorId
                )
            )

            recordHistoryTransition(
                packageId = packageId,
                fromState = SyncPackageState.fromCode(entity.state),
                toState = SyncPackageState.fromCode(entity.state),
                actorId = supervisorActorId,
                actorRole = supervisorRole,
                description = "فشل التحقق من سلامة الحزمة أو البصمة الرقمية",
                details = mapOf("errors" to inspection.validationErrors)
            )

            return Result.failure(IllegalStateException("فشل فحص الحزمة: ${inspection.validationErrors.joinToString()}"))
        }

        // Successfully validated
        val updatedEntity = entity.copy(
            state = SyncPackageState.VALIDATED.code,
            validationStatus = "VALID",
            validationErrorsJson = "[]",
            validationWarningsJson = JSONArray(inspection.validationWarnings).toString(),
            lastReviewedAt = nowIso,
            reviewedBy = supervisorActorId
        )
        database.syncPackageDao().insertOrUpdatePackage(updatedEntity)

        recordHistoryTransition(
            packageId = packageId,
            fromState = SyncPackageState.fromCode(entity.state),
            toState = SyncPackageState.VALIDATED,
            actorId = supervisorActorId,
            actorRole = supervisorRole,
            description = "تم التحقق من سلامة الحزمة ومطابقة البصمة الرقمية بنجاح",
            details = mapOf("surveys" to inspection.surveyCount, "attachments" to inspection.attachmentCount)
        )

        database.auditLogDao().insertAuditLog(
            AuditLogEntity(
                logId = UUID.randomUUID().toString(),
                surveyUUID = "WORKSPACE-SYNC",
                recordId = packageId,
                actionType = "SYNC_PACKAGE_VALIDATED",
                actorId = supervisorActorId,
                actorRole = supervisorRole,
                timestamp = nowIso,
                decisionReason = "Validated package structure and cryptographic checksum",
                packageId = packageId
            )
        )

        return Result.success(inspection)
    }

    /**
     * Runs conflict analysis on the package against local database and transitions to REVIEW_PENDING.
     */
    suspend fun analyzeConflictsForPackage(
        packageId: String,
        supervisorActorId: String = "supervisor_admin",
        supervisorRole: String = "DISTRICT_SUPERVISOR"
    ): Result<Pair<List<SurveyMergeItem>, SurveyMergeSummary>> {
        val entity = database.syncPackageDao().getPackageById(packageId)
            ?: return Result.failure(IllegalArgumentException("الحزمة غير مسجلة: $packageId"))

        val file = File(entity.packageFilePath)
        if (!file.exists()) {
            return Result.failure(IllegalStateException("ملف الحزمة غير متوفر: ${entity.packageFilePath}"))
        }

        val inspection = syncImporter.inspectAndValidatePackage(file)
        if (inspection.surveyItems.isEmpty()) {
            return Result.failure(IllegalStateException("الحزمة لا تحتوي على استمارات ميدانية صالحة."))
        }

        // Fetch existing records from local database
        val existingEntities = database.surveyRecordDao().getAllSurveysSync()
        val existingRecords = existingEntities.map { mapEntityToRecord(it) }

        val (mergeItems, summary) = conflictEngine.analyzeConflicts(
            incomingSurveys = inspection.surveyItems,
            existingSurveys = existingRecords,
            incomingRevisions = inspection.revisionItems
        )

        val nowIso = dateFormat.format(Date())
        val updatedEntity = entity.copy(
            state = SyncPackageState.REVIEW_PENDING.code,
            newRecordsCount = summary.newRecordsCount,
            updatesCount = summary.updatesCount,
            conflictsCount = summary.conflictsCount,
            duplicatesCount = summary.duplicatesCount,
            lastReviewedAt = nowIso,
            reviewedBy = supervisorActorId
        )
        database.syncPackageDao().insertOrUpdatePackage(updatedEntity)

        recordHistoryTransition(
            packageId = packageId,
            fromState = SyncPackageState.fromCode(entity.state),
            toState = SyncPackageState.REVIEW_PENDING,
            actorId = supervisorActorId,
            actorRole = supervisorRole,
            description = "تحليل التعارضات وتصنيف الاستمارات تمهيداً لمراجعة المشرف",
            details = mapOf(
                "new" to summary.newRecordsCount,
                "updates" to summary.updatesCount,
                "conflicts" to summary.conflictsCount,
                "duplicates" to summary.duplicatesCount
            )
        )

        return Result.success(Pair(mergeItems, summary))
    }

    /**
     * Marks package state after supervisor completes or partially completes merge.
     */
    suspend fun markPackageMerged(
        packageId: String,
        isFullyMerged: Boolean,
        mergedCount: Int,
        supervisorActorId: String = "supervisor_admin",
        supervisorRole: String = "DISTRICT_SUPERVISOR",
        reason: String = "Approved survey merge execution"
    ) {
        val entity = database.syncPackageDao().getPackageById(packageId) ?: return
        val targetState = if (isFullyMerged) SyncPackageState.MERGED else SyncPackageState.PARTIALLY_MERGED
        val nowIso = dateFormat.format(Date())

        database.syncPackageDao().markPackageMerged(
            packageId = packageId,
            mergedCount = mergedCount,
            newState = targetState.code,
            timestamp = nowIso,
            actorId = supervisorActorId
        )

        recordHistoryTransition(
            packageId = packageId,
            fromState = SyncPackageState.fromCode(entity.state),
            toState = targetState,
            actorId = supervisorActorId,
            actorRole = supervisorRole,
            description = if (isFullyMerged) "تم إكمال دمج جميع استمارات الحزمة في قاعدة البيانات" else "تم دمج جزء من استمارات الحزمة بنجاح",
            details = mapOf("mergedCount" to mergedCount, "reason" to reason)
        )
    }

    /**
     * Rejects an entire package upon supervisor decision.
     */
    suspend fun rejectPackage(
        packageId: String,
        supervisorActorId: String = "supervisor_admin",
        supervisorRole: String = "DISTRICT_SUPERVISOR",
        reason: String
    ) {
        val entity = database.syncPackageDao().getPackageById(packageId) ?: return
        val nowIso = dateFormat.format(Date())

        database.syncPackageDao().updatePackageState(
            packageId = packageId,
            newState = SyncPackageState.REJECTED.code,
            timestamp = nowIso,
            actorId = supervisorActorId,
            reason = reason
        )

        recordHistoryTransition(
            packageId = packageId,
            fromState = SyncPackageState.fromCode(entity.state),
            toState = SyncPackageState.REJECTED,
            actorId = supervisorActorId,
            actorRole = supervisorRole,
            description = "رفض حزمة التبادل الميداني من قبل المشرف",
            details = mapOf("reason" to reason)
        )

        database.auditLogDao().insertAuditLog(
            AuditLogEntity(
                logId = UUID.randomUUID().toString(),
                surveyUUID = "WORKSPACE-SYNC",
                recordId = packageId,
                actionType = "SYNC_PACKAGE_REJECTED",
                actorId = supervisorActorId,
                actorRole = supervisorRole,
                timestamp = nowIso,
                decisionReason = reason,
                packageId = packageId
            )
        )
    }

    /**
     * Sets package archive status (Archived / Unarchived).
     */
    suspend fun setPackageArchived(
        packageId: String,
        isArchived: Boolean,
        supervisorActorId: String = "supervisor_admin",
        supervisorRole: String = "DISTRICT_SUPERVISOR"
    ) {
        val entity = database.syncPackageDao().getPackageById(packageId) ?: return
        val nowIso = dateFormat.format(Date())

        val targetState = if (isArchived) SyncPackageState.ARCHIVED.code else {
            if (entity.mergedRecordsCount > 0) SyncPackageState.MERGED.code else SyncPackageState.REVIEW_PENDING.code
        }

        database.syncPackageDao().setPackageArchived(packageId, isArchived)
        database.syncPackageDao().updatePackageState(packageId, targetState, nowIso, supervisorActorId)

        recordHistoryTransition(
            packageId = packageId,
            fromState = SyncPackageState.fromCode(entity.state),
            toState = SyncPackageState.fromCode(targetState),
            actorId = supervisorActorId,
            actorRole = supervisorRole,
            description = if (isArchived) "أرشفة الحزمة وحفظها تاريخياً" else "استعادة الحزمة من الأرشيف",
            details = mapOf("isArchived" to isArchived)
        )
    }

    /**
     * Retrieves all workspace packages mapped to domain items.
     */
    suspend fun getWorkspacePackages(includeArchived: Boolean = true): List<SyncPackageWorkspaceItem> {
        val entities = if (includeArchived) {
            database.syncPackageDao().getAllPackagesSync()
        } else {
            database.syncPackageDao().getActivePackagesSync()
        }
        return entities.map { mapEntityToWorkspaceItem(it) }
    }

    /**
     * Calculates offline workspace dashboard statistics.
     */
    suspend fun computeDashboardStats(): WorkspaceDashboardStats {
        val packages = database.syncPackageDao().getAllPackagesSync()
        val surveys = database.surveyRecordDao().getAllSurveysSync()
        val attachments = database.surveyAttachmentDao().getAttachmentsForSurvey("%") // all attachments
        val auditLogs = database.auditLogDao().getAllAuditLogs().first()

        var pendingReviews = 0
        var validatedReady = 0
        var completedMerges = 0
        var rejectedCount = 0
        var archivedCount = 0
        var newWaiting = 0
        var updatesWaiting = 0
        var conflictsWaiting = 0
        var duplicatesCount = 0

        val distinctEnumerators = mutableSetOf<String>()
        val distinctDevices = mutableSetOf<String>()

        packages.forEach { pkg ->
            distinctEnumerators.add(pkg.senderUsername)
            distinctDevices.add(pkg.sourceDeviceId)

            if (pkg.isArchived) {
                archivedCount++
            } else {
                when (pkg.state) {
                    SyncPackageState.RECEIVED.code, SyncPackageState.REVIEW_PENDING.code -> pendingReviews++
                    SyncPackageState.VALIDATED.code -> validatedReady++
                    SyncPackageState.MERGED.code -> completedMerges++
                    SyncPackageState.REJECTED.code -> rejectedCount++
                }
                newWaiting += pkg.newRecordsCount
                updatesWaiting += pkg.updatesCount
                conflictsWaiting += pkg.conflictsCount
                duplicatesCount += pkg.duplicatesCount
            }
        }

        var wellsCount = 0
        var springsCount = 0
        var damsCount = 0

        surveys.forEach { survey ->
            when (survey.surveyType.uppercase()) {
                "WELL" -> wellsCount++
                "SPRING" -> springsCount++
                "DAM" -> damsCount++
            }
        }

        var totalAttachmentSizeBytes = 0L
        attachments.forEach {
            totalAttachmentSizeBytes += it.fileSizeBytes
        }

        return WorkspaceDashboardStats(
            totalReceivedPackages = packages.size,
            pendingReviewsCount = pendingReviews,
            validatedReadyCount = validatedReady,
            completedMergesCount = completedMerges,
            rejectedPackagesCount = rejectedCount,
            archivedPackagesCount = archivedCount,
            newRecordsWaiting = newWaiting,
            updatesWaiting = updatesWaiting,
            conflictsWaiting = conflictsWaiting,
            duplicatesDetected = duplicatesCount,
            databaseTotalSurveys = surveys.size,
            databaseWellsCount = wellsCount,
            databaseSpringsCount = springsCount,
            databaseDamsCount = damsCount,
            databaseTotalAttachments = attachments.size,
            databaseTotalSizeBytes = totalAttachmentSizeBytes,
            totalAuditLogsCount = auditLogs.size,
            distinctEnumeratorsCount = distinctEnumerators.size,
            distinctDevicesCount = distinctDevices.size
        )
    }

    /**
     * Generates a comprehensive district-level synchronization summary for official reporting.
     */
    suspend fun generateDistrictSyncSummary(
        governorate: String,
        district: String
    ): DistrictSyncSummary {
        val nowIso = dateFormat.format(Date())
        val surveys = database.surveyRecordDao().getAllSurveysSync().filter {
            (it.governorateCode.contains(governorate, ignoreCase = true) || it.governorateCode.isBlank()) &&
            (it.districtCode.contains(district, ignoreCase = true) || it.districtCode.isBlank())
        }

        val packages = database.syncPackageDao().getAllPackagesSync().filter {
            (it.governorate.contains(governorate, ignoreCase = true) || it.governorate.isBlank()) &&
            (it.district.contains(district, ignoreCase = true) || it.district.isBlank())
        }

        val auditLogs = database.auditLogDao().getAllAuditLogs().first()
        val revisions = database.surveyRevisionDao().getAllRevisionsSync()

        var wells = 0
        var springs = 0
        var dams = 0
        var approved = 0
        var completed = 0
        var draft = 0

        surveys.forEach {
            when (it.surveyType.uppercase()) {
                "WELL" -> wells++
                "SPRING" -> springs++
                "DAM" -> dams++
            }
            when (it.workflowStatus.uppercase()) {
                "APPROVED" -> approved++
                "COMPLETED" -> completed++
                "DRAFT" -> draft++
            }
        }

        // Group enumerator contributions
        val enumMap = mutableMapOf<String, MutableList<SyncPackageEntity>>()
        packages.forEach { pkg ->
            enumMap.getOrPut(pkg.senderUsername) { mutableListOf() }.add(pkg)
        }

        val enumeratorContributions = enumMap.map { (username, pkgs) ->
            val totalSubmitted = pkgs.sumOf { it.surveyCount }
            val lastDate = pkgs.maxOfOrNull { it.exportTimestamp } ?: nowIso
            val deviceId = pkgs.firstOrNull()?.sourceDeviceId ?: "UNKNOWN"

            EnumeratorContribution(
                enumeratorId = "usr-$username",
                enumeratorUsername = username,
                sourceDeviceId = deviceId,
                submittedPackagesCount = pkgs.size,
                totalSurveysSubmitted = totalSubmitted,
                approvedSurveysCount = pkgs.sumOf { it.mergedRecordsCount },
                lastSubmissionDate = lastDate
            )
        }

        val totalAttachments = surveys.sumOf {
            if (it.attachmentsJson.isNullOrBlank()) 0 else runCatching { JSONArray(it.attachmentsJson).length() }.getOrDefault(0)
        }

        return DistrictSyncSummary(
            governorate = governorate,
            district = district,
            generationTimestamp = nowIso,
            totalSurveys = surveys.size,
            wellsCount = wells,
            springsCount = springs,
            damsCount = dams,
            approvedCount = approved,
            completedCount = completed,
            draftCount = draft,
            totalAttachmentsCount = totalAttachments,
            totalAttachmentSizeBytes = packages.sumOf { it.totalAttachmentSizeBytes },
            enumeratorSources = enumeratorContributions,
            packagesProcessedCount = packages.size,
            totalAuditLogsCount = auditLogs.size,
            totalRevisionsCount = revisions.size,
            lastSyncTimestamp = packages.maxOfOrNull { it.receivedTimestamp } ?: nowIso
        )
    }

    /**
     * Fetches historical transition logs for a package.
     */
    suspend fun getPackageHistory(packageId: String): List<SyncPackageHistoryRecord> {
        return database.syncPackageDao().getHistoryForPackage(packageId).map { entity ->
            SyncPackageHistoryRecord(
                historyId = entity.historyId,
                packageId = entity.packageId,
                fromState = SyncPackageState.fromCode(entity.fromState),
                toState = SyncPackageState.fromCode(entity.toState),
                actorId = entity.actorId,
                actorRole = entity.actorRole,
                timestamp = entity.timestamp,
                actionDescription = entity.actionDescription,
                details = parseJsonMap(entity.detailsJson)
            )
        }
    }

    private suspend fun recordHistoryTransition(
        packageId: String,
        fromState: SyncPackageState,
        toState: SyncPackageState,
        actorId: String,
        actorRole: String,
        description: String,
        details: Map<String, Any?> = emptyMap()
    ) {
        val nowIso = dateFormat.format(Date())
        database.syncPackageDao().insertHistory(
            SyncPackageHistoryEntity(
                historyId = UUID.randomUUID().toString(),
                packageId = packageId,
                fromState = fromState.code,
                toState = toState.code,
                actorId = actorId,
                actorRole = actorRole,
                timestamp = nowIso,
                actionDescription = description,
                detailsJson = JSONObject(details).toString()
            )
        )
    }

    private fun calculateFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun mapEntityToWorkspaceItem(entity: SyncPackageEntity): SyncPackageWorkspaceItem {
        return SyncPackageWorkspaceItem(
            packageId = entity.packageId,
            originalFileName = entity.originalFileName,
            packageFilePath = entity.packageFilePath,
            extractedDirPath = entity.extractedDirPath,
            packageSha256 = entity.packageSha256,
            sourceDeviceId = entity.sourceDeviceId,
            senderUsername = entity.senderUsername,
            senderRole = entity.senderRole,
            governorate = entity.governorate,
            district = entity.district,
            exportTimestamp = entity.exportTimestamp,
            receivedTimestamp = entity.receivedTimestamp,
            surveyCount = entity.surveyCount,
            attachmentCount = entity.attachmentCount,
            totalAttachmentSizeBytes = entity.totalAttachmentSizeBytes,
            packageSizeBytes = entity.packageSizeBytes,
            state = SyncPackageState.fromCode(entity.state),
            validationStatus = entity.validationStatus,
            validationErrors = parseJsonList(entity.validationErrorsJson),
            validationWarnings = parseJsonList(entity.validationWarningsJson),
            newRecordsCount = entity.newRecordsCount,
            updatesCount = entity.updatesCount,
            conflictsCount = entity.conflictsCount,
            duplicatesCount = entity.duplicatesCount,
            mergedRecordsCount = entity.mergedRecordsCount,
            lastReviewedAt = entity.lastReviewedAt,
            reviewedBy = entity.reviewedBy,
            decisionReason = entity.decisionReason,
            isArchived = entity.isArchived,
            notes = entity.notes
        )
    }

    private fun mapEntityToRecord(entity: com.yemen.watersurvey.data.entity.SurveyRecordEntity): SurveyRecord {
        return SurveyRecord(
            recordId = entity.recordId,
            surveyUUID = entity.surveyUUID,
            surveyType = runCatching { SurveyType.valueOf(entity.surveyType) }.getOrDefault(SurveyType.WELL),
            admin1Pcode = entity.admin1Pcode,
            admin2Pcode = entity.admin2Pcode,
            admin3Pcode = entity.admin3Pcode,
            villageReferenceId = entity.villageCode,
            enumeratorId = entity.enumeratorId,
            enumeratorUsername = entity.enumeratorUsername,
            workflowStatus = entity.workflowStatus,
            revisionCount = entity.revisionCount,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            gpsPoint = if (entity.latitude != null && entity.longitude != null) {
                GpsLocationResult(
                    latitude = entity.latitude,
                    longitude = entity.longitude,
                    altitudeM = entity.altitudeM ?: 0.0,
                    accuracyM = entity.accuracyM ?: 5.0f,
                    quality = runCatching { GpsAccuracyQuality.valueOf(entity.gpsQuality ?: "GOOD") }.getOrDefault(GpsAccuracyQuality.GOOD),
                    capturedAt = entity.gpsCapturedAt ?: entity.updatedAt,
                    provider = entity.gpsProvider ?: "gps"
                )
            } else null
        )
    }

    private fun parseJsonList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            List(arr.length()) { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    private fun parseJsonMap(json: String?): Map<String, Any?> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, Any?>()
            obj.keys().forEach { key ->
                map[key] = obj.opt(key)
            }
            map
        }.getOrDefault(emptyMap())
    }
}
