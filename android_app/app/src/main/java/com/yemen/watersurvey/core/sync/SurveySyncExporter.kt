package com.yemen.watersurvey.core.sync

import android.content.Context
import com.yemen.watersurvey.domain.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Native Offline Survey Data Exchange Package Exporter (.ywsync).
 *
 * Responsibilities:
 * - Reads survey and revision data without mutating database state.
 * - Exports selected surveys matching specified filters (All, Type, Status, Date).
 * - Copies only referenced media attachments into the package structure.
 * - Generates structured JSON files (surveys.json, revisions.json, metadata.json, manifest.json).
 * - Computes deterministic SHA-256 checksums and writes checksum.sha256.
 * - Compresses output into a standalone .ywsync (ZIP) container for manual transport
 *   via USB OTG, SD Card, or Bluetooth.
 * - 100% offline, zero network or cloud dependency.
 */
class SurveySyncExporter(
    private val context: Context
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val fileTimestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    companion object {
        const val PACKAGE_EXTENSION = ".ywsync"
        const val MANIFEST_FILE = "manifest.json"
        const val METADATA_FILE = "metadata.json"
        const val SURVEYS_FILE = "surveys.json"
        const val REVISIONS_FILE = "revisions.json"
        const val ATTACHMENTS_DIR = "attachments"
        const val CHECKSUM_FILE = "checksum.sha256"
        const val FORMAT_VERSION = "1.0"
        const val PACKAGE_TYPE = "YEMEN_WATER_SURVEY_SYNC"
    }

    /**
     * Target export directory: context.filesDir/exports/sync_packages/
     */
    val syncPackagesDir: File
        get() {
            val dir = File(context.filesDir, "exports/sync_packages")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    /**
     * Exports survey records and referenced attachments into a .ywsync package.
     *
     * @param allSurveys The full collection of available surveys in the local repository.
     * @param allRevisions The full collection of revision records in the local repository.
     * @param filter Filter criteria (Scope, Type, Status, Date Range, Admin bounds).
     * @param sourceDeviceId Unique hardware/app installation identifier.
     * @param senderRole Role of the exporting user (ENUMERATOR, DISTRICT_SUPERVISOR, etc.).
     * @param senderUsername Username of the exporting user.
     * @param district District name or code.
     * @param governorate Governorate name or code.
     */
    fun exportSyncPackage(
        allSurveys: List<SurveyRecord>,
        allRevisions: List<SurveyRevisionRecord> = emptyList(),
        filter: SyncExportFilter = SyncExportFilter(),
        sourceDeviceId: String = "DEV-YEM-${UUID.randomUUID().toString().take(8).uppercase()}",
        senderRole: String = "ENUMERATOR",
        senderUsername: String = "enumerator_field",
        district: String = "صعدة",
        governorate: String = "صعدة",
        packageId: String = UUID.randomUUID().toString()
    ): SyncExportResult {
        // 1. Filter surveys
        val filteredSurveys = applyFilter(allSurveys, filter)
        if (filteredSurveys.isEmpty()) {
            return SyncExportResult.Failure(
                errorMessage = "لا توجد استمارات مطابقة لمعايير التصدير المحددة."
            )
        }

        // 2. Extract corresponding revisions
        val filteredRecordIds = filteredSurveys.map { it.recordId }.toSet()
        val matchingRevisions = allRevisions.filter { it.recordId in filteredRecordIds }

        val timestampIso = dateFormat.format(Date())
        val timestampFile = fileTimestampFormat.format(Date())
        val sanitizedRole = senderRole.replace(" ", "_").lowercase()
        // Sanitize full packageId to guarantee filename uniqueness across rapid successive exports
        val sanitizedPkgId = packageId.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        val outputFileName = "SYNC_${sanitizedRole}_${filteredSurveys.size}surveys_${timestampFile}_${sanitizedPkgId}$PACKAGE_EXTENSION"
        val destinationZipFile = File(syncPackagesDir, outputFileName)

        // 3. Staging directory
        val stagingDir = File(context.cacheDir, "sync_stage_${packageId}")
        if (stagingDir.exists()) {
            stagingDir.deleteRecursively()
        }
        stagingDir.mkdirs()

        return try {
            val attachmentsDir = File(stagingDir, ATTACHMENTS_DIR)
            attachmentsDir.mkdirs()

            var totalCopiedAttachments = 0
            var totalAttachmentSizeBytes = 0L

            // 4. Copy referenced attachments
            for (survey in filteredSurveys) {
                if (survey.attachments.isNotEmpty()) {
                    val surveyAttDir = File(attachmentsDir, survey.recordId)
                    for (att in survey.attachments) {
                        val sourceFile = resolveAttachmentFile(att)
                        if (sourceFile != null && sourceFile.exists() && sourceFile.isFile) {
                            if (!surveyAttDir.exists()) {
                                surveyAttDir.mkdirs()
                            }
                            val targetFile = File(surveyAttDir, att.fileName)
                            sourceFile.copyTo(targetFile, overwrite = true)
                            totalCopiedAttachments++
                            totalAttachmentSizeBytes += targetFile.length()
                        }
                    }
                }
            }

            // 5. Write metadata.json
            val metadataObj = JSONObject().apply {
                put("packageId", packageId)
                put("titleAr", "حزمة تبادل البيانات الميدانية لمسح المياه")
                put("descriptionAr", filter.scopeDescriptionAr)
                put("sourceDeviceId", sourceDeviceId)
                put("senderRole", senderRole)
                put("senderUsername", senderUsername)
                put("governorate", governorate)
                put("district", district)
                put("exportScope", if (filter.isAll) "ALL" else "FILTERED")
                put("surveyTypeFilter", filter.surveyType?.name)
                put("statusFilter", filter.workflowStatus)
                put("dateFrom", filter.dateFrom)
                put("dateTo", filter.dateTo)
                put("createdAt", timestampIso)
                put("appVersion", "2.9.0")
                put("targetPlatform", "Yemen-Water-Survey-Android")
            }
            File(stagingDir, METADATA_FILE).writeText(metadataObj.toString(2), Charsets.UTF_8)

            // 6. Write surveys.json
            val surveysArray = JSONArray()
            for (survey in filteredSurveys) {
                surveysArray.put(serializeSurveyRecord(survey))
            }
            val surveysObj = JSONObject().apply {
                put("packageId", packageId)
                put("count", filteredSurveys.size)
                put("generatedAt", timestampIso)
                put("surveys", surveysArray)
            }
            File(stagingDir, SURVEYS_FILE).writeText(surveysObj.toString(2), Charsets.UTF_8)

            // 7. Write revisions.json
            val revisionsArray = JSONArray()
            for (rev in matchingRevisions) {
                revisionsArray.put(serializeRevisionRecord(rev))
            }
            val revisionsObj = JSONObject().apply {
                put("packageId", packageId)
                put("count", matchingRevisions.size)
                put("generatedAt", timestampIso)
                put("revisions", revisionsArray)
            }
            File(stagingDir, REVISIONS_FILE).writeText(revisionsObj.toString(2), Charsets.UTF_8)

            // 8. Compute individual file checksums & total payload checksum
            val fileEntries = mutableListOf<SyncPackageFileEntry>()
            val allStagingFiles = stagingDir.walkTopDown().filter { it.isFile }.toList()

            val compositeDigest = MessageDigest.getInstance("SHA-256")
            val sortedFiles = allStagingFiles.sortedBy { it.relativeTo(stagingDir).path.replace('\\', '/') }

            for (file in sortedFiles) {
                val relPath = file.relativeTo(stagingDir).path.replace('\\', '/')
                val fileHash = calculateFileSha256(file)
                val fileSize = file.length()

                fileEntries.add(
                    SyncPackageFileEntry(
                        relativePath = relPath,
                        sizeBytes = fileSize,
                        sha256 = fileHash
                    )
                )
                compositeDigest.update(relPath.toByteArray(Charsets.UTF_8))
                compositeDigest.update(file.readBytes())
            }

            val payloadChecksum = compositeDigest.digest().joinToString("") { "%02x".format(it) }

            // 9. Write checksum.sha256
            File(stagingDir, CHECKSUM_FILE).writeText(payloadChecksum, Charsets.UTF_8)

            // 10. Write manifest.json
            val manifestObj = JSONObject().apply {
                put("packageId", packageId)
                put("formatVersion", FORMAT_VERSION)
                put("packageType", PACKAGE_TYPE)
                put("sourceDeviceId", sourceDeviceId)
                put("senderRole", senderRole)
                put("senderUsername", senderUsername)
                put("district", district)
                put("governorate", governorate)
                put("creationTimestamp", timestampIso)
                put("surveyCount", filteredSurveys.size)
                put("attachmentCount", totalCopiedAttachments)
                put("totalAttachmentSizeBytes", totalAttachmentSizeBytes)
                put("checksum", payloadChecksum)

                val filesJsonArray = JSONArray()
                for (entry in fileEntries) {
                    filesJsonArray.put(JSONObject().apply {
                        put("path", entry.relativePath)
                        put("sizeBytes", entry.sizeBytes)
                        put("sha256", entry.sha256)
                    })
                }
                put("fileList", filesJsonArray)
            }
            File(stagingDir, MANIFEST_FILE).writeText(manifestObj.toString(2), Charsets.UTF_8)

            // 11. Zip into .ywsync file
            zipDirectory(stagingDir, destinationZipFile)

            val manifest = SurveySyncPackageManifest(
                packageId = packageId,
                formatVersion = FORMAT_VERSION,
                packageType = PACKAGE_TYPE,
                sourceDeviceId = sourceDeviceId,
                senderRole = senderRole,
                senderUsername = senderUsername,
                district = district,
                governorate = governorate,
                creationTimestamp = timestampIso,
                surveyCount = filteredSurveys.size,
                attachmentCount = totalCopiedAttachments,
                totalAttachmentSizeBytes = totalAttachmentSizeBytes,
                checksum = payloadChecksum,
                fileList = fileEntries
            )

            SyncExportResult.Success(
                packageFile = destinationZipFile,
                manifest = manifest,
                packageSizeBytes = destinationZipFile.length(),
                checksumSha256 = payloadChecksum,
                surveyCount = filteredSurveys.size,
                attachmentCount = totalCopiedAttachments
            )
        } catch (e: Exception) {
            SyncExportResult.Failure(
                errorMessage = "فشل في توليد حزمة التبادل الميداني: ${e.localizedMessage ?: e.message}",
                exception = e
            )
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    /**
     * Validates a .ywsync package integrity, structure, and manifest without mutating database.
     */
    fun validatePackage(packageFile: File): SyncPackageValidationResult {
        if (!packageFile.exists() || !packageFile.canRead()) {
            return SyncPackageValidationResult(
                isValid = false,
                errors = listOf("ملف الحزمة غير موجود أو غير قابل للقراءة: ${packageFile.name}")
            )
        }

        val errors = mutableListOf<String>()
        var parsedManifest: SurveySyncPackageManifest? = null

        try {
            ZipFile(packageFile).use { zip ->
                val entries = zip.entries().asSequence().map { it.name.replace('\\', '/') }.toSet()

                // Check required files
                val requiredFiles = listOf(MANIFEST_FILE, METADATA_FILE, SURVEYS_FILE, REVISIONS_FILE, CHECKSUM_FILE)
                for (req in requiredFiles) {
                    if (!entries.contains(req)) {
                        errors.add("الملف المطلوب مفقود في الحزمة: $req")
                    }
                }

                if (entries.contains(MANIFEST_FILE)) {
                    val manifestEntry = zip.getEntry(MANIFEST_FILE)
                    val manifestContent = zip.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val json = JSONObject(manifestContent)

                    val packageId = json.optString("packageId")
                    val formatVersion = json.optString("formatVersion")
                    val checksum = json.optString("checksum")
                    val surveyCount = json.optInt("surveyCount", 0)
                    val attachmentCount = json.optInt("attachmentCount", 0)

                    if (packageId.isBlank()) errors.add("معرف الحزمة packageId في manifest غير صالح.")
                    if (formatVersion != FORMAT_VERSION) errors.add("إصدار التنسيق $formatVersion غير متطابق مع النظام ($FORMAT_VERSION).")
                    if (checksum.isBlank()) errors.add("رمز التحقق checksum مفقود في manifest.")

                    parsedManifest = SurveySyncPackageManifest(
                        packageId = packageId,
                        formatVersion = formatVersion,
                        packageType = json.optString("packageType", PACKAGE_TYPE),
                        sourceDeviceId = json.optString("sourceDeviceId", "UNKNOWN"),
                        senderRole = json.optString("senderRole", "UNKNOWN"),
                        senderUsername = json.optString("senderUsername", "UNKNOWN"),
                        district = json.optString("district", ""),
                        governorate = json.optString("governorate", ""),
                        creationTimestamp = json.optString("creationTimestamp", ""),
                        surveyCount = surveyCount,
                        attachmentCount = attachmentCount,
                        totalAttachmentSizeBytes = json.optLong("totalAttachmentSizeBytes", 0L),
                        checksum = checksum
                    )
                }

                if (entries.contains(SURVEYS_FILE)) {
                    val surveysEntry = zip.getEntry(SURVEYS_FILE)
                    val surveysContent = zip.getInputStream(surveysEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val json = JSONObject(surveysContent)
                    if (!json.has("surveys")) {
                        errors.add("ملف surveys.json لا يحتوي على مصفوفة surveys.")
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("فشل قراءة أرشيف الحزمة المضغوطة: ${e.localizedMessage ?: e.message}")
        }

        return SyncPackageValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            manifest = parsedManifest
        )
    }

    /**
     * Filters list of surveys according to filter parameters.
     */
    fun applyFilter(surveys: List<SurveyRecord>, filter: SyncExportFilter): List<SurveyRecord> {
        return surveys.filter { survey ->
            val matchesType = filter.surveyType == null || survey.surveyType == filter.surveyType
            val matchesStatus = filter.workflowStatus == null || survey.workflowStatus.equals(filter.workflowStatus, ignoreCase = true)
            val matchesDistrict = filter.districtCode == null || survey.districtCode == filter.districtCode
            val matchesGovernorate = filter.governorateCode == null || survey.governorateCode == filter.governorateCode

            val matchesDate = when {
                filter.dateFrom != null && filter.dateTo != null -> {
                    survey.createdAt in filter.dateFrom..filter.dateTo
                }
                filter.dateFrom != null -> {
                    survey.createdAt >= filter.dateFrom
                }
                filter.dateTo != null -> {
                    survey.createdAt <= filter.dateTo
                }
                else -> true
            }

            matchesType && matchesStatus && matchesDistrict && matchesGovernorate && matchesDate
        }
    }

    /**
     * Calculates statistics for a potential export without generating files.
     */
    fun calculateExportStats(surveys: List<SurveyRecord>, filter: SyncExportFilter): Pair<Int, Pair<Int, Long>> {
        val filtered = applyFilter(surveys, filter)
        var totalAttachments = 0
        var totalAttachmentSize = 0L

        for (survey in filtered) {
            for (att in survey.attachments) {
                val file = resolveAttachmentFile(att)
                if (file != null && file.exists()) {
                    totalAttachments++
                    totalAttachmentSize += file.length()
                } else if (att.fileSize > 0) {
                    totalAttachments++
                    totalAttachmentSize += att.fileSize
                }
            }
        }

        return Pair(filtered.size, Pair(totalAttachments, totalAttachmentSize))
    }

    /**
     * Resolves attachment physical file on storage.
     */
    private fun resolveAttachmentFile(att: AttachmentInfo): File? {
        val directFile = File(att.filePath)
        if (directFile.exists() && directFile.isFile) return directFile

        val fromFilesDir = File(context.filesDir, att.filePath)
        if (fromFilesDir.exists() && fromFilesDir.isFile) return fromFilesDir

        val fromCacheDir = File(context.cacheDir, att.filePath)
        if (fromCacheDir.exists() && fromCacheDir.isFile) return fromCacheDir

        return null
    }

    /**
     * Serializes SurveyRecord to JSONObject.
     */
    private fun serializeSurveyRecord(record: SurveyRecord): JSONObject {
        return JSONObject().apply {
            put("recordId", record.recordId)
            put("surveyUUID", record.surveyUUID)
            put("formId", record.formId)
            put("formVersion", record.formVersion)
            put("surveyType", record.surveyType.name)
            put("governorateCode", record.governorateCode)
            put("districtCode", record.districtCode)
            put("uzlahCode", record.uzlahCode)
            put("villageCode", record.villageCode)
            put("admin1Pcode", record.admin1Pcode)
            put("admin2Pcode", record.admin2Pcode)
            put("admin3Pcode", record.admin3Pcode)
            put("villageReferenceId", record.villageReferenceId ?: JSONObject.NULL)
            put("governorateNameSnapshotAr", record.governorateNameSnapshotAr)
            put("districtNameSnapshotAr", record.districtNameSnapshotAr)
            put("subDistrictNameSnapshotAr", record.subDistrictNameSnapshotAr)
            put("villageNameSnapshotAr", record.villageNameSnapshotAr ?: JSONObject.NULL)
            put("isLocalNameOverride", record.isLocalNameOverride)
            put("localOverrideId", record.localOverrideId ?: JSONObject.NULL)
            put("enumeratorId", record.enumeratorId)
            put("enumeratorUsername", record.enumeratorUsername)
            put("workflowStatus", record.workflowStatus)
            put("revisionCount", record.revisionCount)
            put("createdAt", record.createdAt)
            put("updatedAt", record.updatedAt)
            put("gpsResolutionStatus", record.gpsResolutionStatus.name)
            put("gpsResolvedAdmin1Pcode", record.gpsResolvedAdmin1Pcode ?: JSONObject.NULL)
            put("gpsResolvedAdmin2Pcode", record.gpsResolvedAdmin2Pcode ?: JSONObject.NULL)
            put("gpsResolvedAdmin3Pcode", record.gpsResolvedAdmin3Pcode ?: JSONObject.NULL)
            put("gpsDistanceToNearestVillageM", record.gpsDistanceToNearestVillageM ?: JSONObject.NULL)
            put("gpsNearestVillageNameAr", record.gpsNearestVillageNameAr ?: JSONObject.NULL)
            put("adminRefVersionTag", record.adminRefVersionTag)
            put("registryCode", record.registryCode.ifBlank { JSONObject.NULL })

            record.gpsPoint?.let { gps ->
                put("gpsPoint", JSONObject().apply {
                    put("latitude", gps.latitude)
                    put("longitude", gps.longitude)
                    put("altitudeM", gps.altitudeM ?: JSONObject.NULL)
                    put("accuracyM", gps.accuracyM)
                    put("quality", gps.quality.name)
                    put("capturedAt", gps.capturedAt)
                    put("provider", gps.provider)
                })
            }

            record.wellDetails?.let { well ->
                put("wellDetails", JSONObject().apply {
                    put("wellNameAr", well.wellNameAr)
                    put("wellType", well.wellType)
                    put("wellDepthM", well.wellDepthM)
                    put("pumpingMechanism", well.pumpingMechanism)
                    put("operationalStatus", well.operationalStatus)
                })
            }

            record.springDetails?.let { spring ->
                put("springDetails", JSONObject().apply {
                    put("springNameAr", spring.springNameAr)
                    put("flowRateLps", spring.flowRateLps)
                    put("waterClarity", spring.waterClarity)
                    put("dischargeSeasonality", spring.dischargeSeasonality)
                })
            }

            record.damDetails?.let { dam ->
                put("damDetails", JSONObject().apply {
                    put("damNameAr", dam.damNameAr)
                    put("structureType", dam.structureType)
                    put("storageCapacityM3", dam.storageCapacityM3)
                    put("damHeightM", dam.damHeightM)
                    put("structuralCondition", dam.structuralCondition)
                })
            }

            val attArray = JSONArray()
            for (att in record.attachments) {
                attArray.put(JSONObject().apply {
                    put("attachmentId", att.attachmentId)
                    put("surveyId", att.surveyId)
                    put("surveyUUID", att.surveyUUID.ifBlank { record.surveyUUID })
                    put("attachmentType", att.attachmentType)
                    put("fileName", att.fileName)
                    put("packageRelativePath", "$ATTACHMENTS_DIR/${record.recordId}/${att.fileName}")
                    put("fileSize", att.fileSize)
                    put("timestamp", att.timestamp)
                    if (att.fileSha256.isNotBlank()) {
                        put("fileSha256", att.fileSha256)
                    }
                })
            }
            put("attachments", attArray)
        }
    }

    /**
     * Serializes SurveyRevisionRecord to JSONObject.
     */
    private fun serializeRevisionRecord(rev: SurveyRevisionRecord): JSONObject {
        return JSONObject().apply {
            put("revisionId", rev.revisionId)
            put("recordId", rev.recordId)
            put("surveyUUID", rev.surveyUUID)
            put("revisionNumber", rev.revisionNumber)
            put("modifiedBy", rev.modifiedBy)
            put("modifiedAt", rev.modifiedAt)
            put("reasonForChange", rev.reasonForChange)
            put("previousStatus", rev.previousStatus)
            put("newStatus", rev.newStatus)
            put("changedFields", JSONArray(rev.changedFields))
        }
    }

    /**
     * Computes SHA-256 of a file.
     */
    private fun calculateFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Compresses staging directory into a ZIP file.
     */
    private fun zipDirectory(sourceDir: File, outputZip: File) {
        if (outputZip.exists()) {
            outputZip.delete()
        }
        outputZip.parentFile?.mkdirs()

        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZip))).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(sourceDir).path.replace('\\', '/')
                    val entry = ZipEntry(relativePath)
                    zos.putNextEntry(entry)
                    file.inputStream().use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }
    }
}
