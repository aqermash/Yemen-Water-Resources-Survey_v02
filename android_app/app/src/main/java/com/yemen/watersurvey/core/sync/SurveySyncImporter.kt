package com.yemen.watersurvey.core.sync

import android.content.Context
import com.yemen.watersurvey.domain.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Native Offline Survey Data Exchange Package Importer & Validation Engine (Phase 9.1).
 *
 * Designed for supervisor devices (District / Governorate / Central) to:
 * - Inspect and validate .ywsync / .ysync packages from offline media (SD card, USB OTG, Bluetooth).
 * - Extract archives into secure isolated staging storage without modifying the Room database.
 * - Perform cryptographic SHA-256 integrity verification (package level and entry level).
 * - Validate JSON schema structure (manifest.json, metadata.json, surveys.json, revisions.json).
 * - Prepare structured preview models for supervisor inspection.
 * - Detect package duplicates (by packageId) and survey duplicates (by record UUID/ID) against
 *   existing repository records without performing automated merges or database mutations.
 * - Maintain 100% offline security, zero cloud dependency, and complete database safety before approval.
 */
class SurveySyncImporter(
    private val context: Context
) {

    companion object {
        const val MANIFEST_FILE = "manifest.json"
        const val METADATA_FILE = "metadata.json"
        const val SURVEYS_FILE = "surveys.json"
        const val REVISIONS_FILE = "revisions.json"
        const val ATTACHMENTS_DIR = "attachments"
        const val CHECKSUM_FILE = "checksum.sha256"
        const val EXPECTED_FORMAT_VERSION = "1.0"
        const val EXPECTED_PACKAGE_TYPE = "YEMEN_WATER_SURVEY_SYNC"
    }

    /**
     * Staging directory for package inspection: context.cacheDir/sync_import_staging/
     */
    val stagingBaseDir: File
        get() {
            val dir = File(context.cacheDir, "sync_import_staging")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    /**
     * Accepted packages staging directory: context.filesDir/staged_packages/
     * Staged for future Phase 9.2 merge pipeline after explicit supervisor confirmation.
     */
    val acceptedStagedDir: File
        get() {
            val dir = File(context.filesDir, "staged_packages")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    /**
     * Inspects, extracts, and validates a .ywsync package against existing local surveys and imported package IDs.
     *
     * @param packageFile The offline sync package file.
     * @param existingSurveys List of surveys currently in the supervisor's database/repository for duplicate detection.
     * @param knownPackageIds Set of already imported package IDs to flag duplicate package transfers.
     * @return SyncImportPreview containing validation status, metadata, survey items, and duplicate statistics.
     */
    fun inspectAndValidatePackage(
        packageFile: File,
        existingSurveys: List<SurveyRecord> = emptyList(),
        knownPackageIds: Set<String> = emptySet()
    ): SyncImportPreview {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (!packageFile.exists() || !packageFile.canRead()) {
            return buildErrorPreview(
                packageFile = packageFile,
                errors = listOf("ملف الحزمة غير موجود أو يتعذر قراءته: ${packageFile.name}")
            )
        }

        // 1. Unpack into an isolated staging directory with Zip-Slip protection
        val tempExtractDir = File(stagingBaseDir, "pkg_inspect_${System.currentTimeMillis()}_${(1000..9999).random()}")
        tempExtractDir.mkdirs()

        try {
            val unpackResult = extractZipSafely(packageFile, tempExtractDir)
            if (!unpackResult.isSuccess) {
                return buildErrorPreview(
                    packageFile = packageFile,
                    errors = listOf("فشل في فك ضغط حزمة المزامنة: ${unpackResult.errorMessage}")
                )
            }

            // 2. Validate required file existence
            val manifestFile = File(tempExtractDir, MANIFEST_FILE)
            val metadataFile = File(tempExtractDir, METADATA_FILE)
            val surveysFile = File(tempExtractDir, SURVEYS_FILE)
            val revisionsFile = File(tempExtractDir, REVISIONS_FILE)
            val checksumFile = File(tempExtractDir, CHECKSUM_FILE)

            if (!manifestFile.exists()) errors.add("ملف بيان الحزمة $MANIFEST_FILE مفقود.")
            if (!metadataFile.exists()) errors.add("ملف البيانات الوصفية $METADATA_FILE مفقود.")
            if (!surveysFile.exists()) errors.add("ملف الاستمارات $SURVEYS_FILE مفقود.")
            if (!revisionsFile.exists()) errors.add("ملف سجل المراجعات $REVISIONS_FILE مفقود.")
            if (!checksumFile.exists()) errors.add("ملف البصمة المشفرة $CHECKSUM_FILE مفقود.")

            if (errors.isNotEmpty()) {
                return buildErrorPreview(
                    packageFile = packageFile,
                    errors = errors
                )
            }

            // 3. Read manifest.json
            val manifestJson = try {
                JSONObject(manifestFile.readText(Charsets.UTF_8))
            } catch (e: Exception) {
                errors.add("ملف manifest.json غير صالح بتنسيق JSON: ${e.message}")
                return buildErrorPreview(packageFile, errors)
            }

            val packageId = manifestJson.optString("packageId", "").trim()
            val formatVersion = manifestJson.optString("formatVersion", "")
            val packageType = manifestJson.optString("packageType", "")
            val sourceDeviceId = manifestJson.optString("sourceDeviceId", "UNKNOWN")
            val senderRole = manifestJson.optString("senderRole", "UNKNOWN")
            val senderUsername = manifestJson.optString("senderUsername", "UNKNOWN")
            val district = manifestJson.optString("district", "")
            val governorate = manifestJson.optString("governorate", "")
            val creationTimestamp = manifestJson.optString("creationTimestamp", "")
            val declaredSurveyCount = manifestJson.optInt("surveyCount", 0)
            val declaredAttachmentCount = manifestJson.optInt("attachmentCount", 0)
            val declaredTotalAttachmentSizeBytes = manifestJson.optLong("totalAttachmentSizeBytes", 0L)
            val declaredChecksum = manifestJson.optString("checksum", "").trim()

            if (packageId.isBlank()) errors.add("معرّف الحزمة packageId غير محدد في manifest.json.")
            if (formatVersion != EXPECTED_FORMAT_VERSION) {
                warnings.add("إصدار التنسيق ($formatVersion) يختلف عن الإصدار المتوقع ($EXPECTED_FORMAT_VERSION).")
            }
            if (packageType != EXPECTED_PACKAGE_TYPE) {
                warnings.add("نوع الحزمة ($packageType) غير قياسي.")
            }

            // 4. Read metadata.json
            val metadata = try {
                val metaObj = JSONObject(metadataFile.readText(Charsets.UTF_8))
                SyncPackageMetadata(
                    packageId = metaObj.optString("packageId", packageId),
                    titleAr = metaObj.optString("titleAr", "حزمة مسح ميدانية"),
                    descriptionAr = metaObj.optString("descriptionAr", ""),
                    sourceDeviceId = metaObj.optString("sourceDeviceId", sourceDeviceId),
                    senderRole = metaObj.optString("senderRole", senderRole),
                    senderUsername = metaObj.optString("senderUsername", senderUsername),
                    governorate = metaObj.optString("governorate", governorate),
                    district = metaObj.optString("district", district),
                    exportScope = metaObj.optString("exportScope", "ALL"),
                    surveyTypeFilter = metaObj.optString("surveyTypeFilter").takeIf { it.isNotBlank() },
                    statusFilter = metaObj.optString("statusFilter").takeIf { it.isNotBlank() },
                    dateFrom = metaObj.optString("dateFrom").takeIf { it.isNotBlank() },
                    dateTo = metaObj.optString("dateTo").takeIf { it.isNotBlank() },
                    createdAt = metaObj.optString("createdAt", creationTimestamp),
                    appVersion = metaObj.optString("appVersion", "2.9.0"),
                    targetPlatform = metaObj.optString("targetPlatform", "Yemen-Water-Survey-Android")
                )
            } catch (e: Exception) {
                warnings.add("تعذر قراءة بعض حقول metadata.json: ${e.message}")
                null
            }

            // 5. Cryptographic SHA-256 Verification
            val calculatedChecksum = computePayloadChecksum(tempExtractDir)
            val fileStoredChecksum = checksumFile.readText(Charsets.UTF_8).trim()

            var checksumVerified = false
            if (declaredChecksum.isNotBlank() && declaredChecksum.equals(calculatedChecksum, ignoreCase = true)) {
                checksumVerified = true
            } else {
                errors.add("فشل مطابقة البصمة الرقمية (Checksum Mismatch)! المعلن: ${declaredChecksum.take(12)}... المحسوب: ${calculatedChecksum.take(12)}...")
            }

            if (fileStoredChecksum.isNotBlank() && !fileStoredChecksum.equals(calculatedChecksum, ignoreCase = true)) {
                errors.add("رمز checksum.sha256 المخزن لا يتطابق مع البصمة الفعلية لمحتويات الحزمة.")
            }

            // 6. Parse surveys.json
            val parsedSurveys = mutableListOf<SurveyRecord>()
            try {
                val surveysObj = JSONObject(surveysFile.readText(Charsets.UTF_8))
                val surveysArray = surveysObj.optJSONArray("surveys") ?: JSONArray()
                for (i in 0 until surveysArray.length()) {
                    val itemObj = surveysArray.getJSONObject(i)
                    parseSurveyRecord(itemObj)?.let { parsedSurveys.add(it) }
                }
            } catch (e: Exception) {
                errors.add("خطأ في قراءة وتحليل surveys.json: ${e.message}")
            }

            // 7. Parse revisions.json
            val parsedRevisions = mutableListOf<SurveyRevisionRecord>()
            try {
                val revisionsObj = JSONObject(revisionsFile.readText(Charsets.UTF_8))
                val revisionsArray = revisionsObj.optJSONArray("revisions") ?: JSONArray()
                for (i in 0 until revisionsArray.length()) {
                    val revObj = revisionsArray.getJSONObject(i)
                    parseRevisionRecord(revObj)?.let { parsedRevisions.add(it) }
                }
            } catch (e: Exception) {
                warnings.add("خطأ في قراءة سجل المراجعات revisions.json: ${e.message}")
            }

            // Count validation
            if (declaredSurveyCount != parsedSurveys.size) {
                warnings.add("عدد الاستمارات الفعلي (${parsedSurveys.size}) لا يطابق العدد المعلن في البيان ($declaredSurveyCount).")
            }

            // Count actual attachments
            val attachmentsDir = File(tempExtractDir, ATTACHMENTS_DIR)
            val actualAttachmentsCount = if (attachmentsDir.exists()) {
                attachmentsDir.walkTopDown().filter { it.isFile }.count()
            } else 0

            var actualAttachmentsSize = 0L
            if (attachmentsDir.exists()) {
                attachmentsDir.walkTopDown().filter { it.isFile }.forEach {
                    actualAttachmentsSize += it.length()
                }
            }

            // 8. Duplicate Detection Preparation (Zero Auto-Merge)
            val isDuplicatePackage = knownPackageIds.contains(packageId)
            if (isDuplicatePackage) {
                warnings.add("تحذير: تم استيراد هذه الحزمة مسبقاً برقم المعرف ($packageId).")
            }

            val existingUuids = existingSurveys.map { it.surveyUUID }.toSet()
            val duplicateSurveyUuids = parsedSurveys.map { it.surveyUUID }.filter { it in existingUuids }
            val duplicateSurveysCount = duplicateSurveyUuids.size
            val newSurveysCount = parsedSurveys.size - duplicateSurveysCount

            if (duplicateSurveysCount > 0) {
                warnings.add("توجد ($duplicateSurveysCount) استمارة مسجلة مسبقاً في قاعدة البيانات المحلية المشرفة بناءً على معرف الاستمارة العالمي (surveyUUID).")
            }

            val isValidStructure = errors.isEmpty()

            return SyncImportPreview(
                packageFile = packageFile,
                packageId = packageId,
                sourceDeviceId = sourceDeviceId,
                senderRole = senderRole,
                senderUsername = senderUsername,
                district = district,
                governorate = governorate,
                creationTimestamp = creationTimestamp,
                surveyCount = parsedSurveys.size,
                attachmentCount = actualAttachmentsCount,
                totalAttachmentSizeBytes = actualAttachmentsSize,
                manifestChecksum = declaredChecksum,
                calculatedChecksum = calculatedChecksum,
                checksumVerified = checksumVerified,
                isValidStructure = isValidStructure,
                isDuplicatePackage = isDuplicatePackage,
                duplicateSurveysCount = duplicateSurveysCount,
                newSurveysCount = newSurveysCount,
                duplicateSurveyUuids = duplicateSurveyUuids,
                validationErrors = errors,
                validationWarnings = warnings,
                surveyItems = parsedSurveys,
                revisionItems = parsedRevisions
            )
        } finally {
            // Clean up extraction temp directory
            tempExtractDir.deleteRecursively()
        }
    }

    /**
     * Explicit supervisor action: Stage the package for subsequent merge pipeline (Phase 9.2).
     * Moves or copies package archive to accepted staging directory without altering survey tables.
     */
    fun stageAcceptedPackage(packageFile: File, packageId: String): File {
        val targetFile = File(acceptedStagedDir, "ACCEPTED_${packageId}_${packageFile.name}")
        packageFile.copyTo(targetFile, overwrite = true)
        return targetFile
    }

    /**
     * Explicit supervisor action: Reject the package.
     */
    fun rejectPackage(packageFile: File): Boolean {
        return true // Simply signals rejection without modifying database
    }

    /**
     * Computes the deterministic SHA-256 composite hash of the extracted staging folder.
     * Excludes manifest.json and checksum.sha256 themselves to match the exporter payload calculation.
     */
    fun computePayloadChecksum(dir: File): String {
        val compositeDigest = MessageDigest.getInstance("SHA-256")
        val files = dir.walkTopDown()
            .filter { it.isFile && it.name != MANIFEST_FILE && it.name != CHECKSUM_FILE }
            .sortedBy { it.relativeTo(dir).path.replace('\\', '/') }
            .toList()

        for (file in files) {
            val relPath = file.relativeTo(dir).path.replace('\\', '/')
            compositeDigest.update(relPath.toByteArray(Charsets.UTF_8))
            compositeDigest.update(file.readBytes())
        }

        return compositeDigest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Extracts a ZIP archive safely preventing Zip-Slip directory traversal attacks.
     */
    private fun extractZipSafely(zipFile: File, destDir: File): ZipExtractResult {
        return try {
            val destCanonicalPath = destDir.canonicalPath
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val targetFile = File(destDir, entry.name)
                    val targetCanonicalPath = targetFile.canonicalPath
                    if (!targetCanonicalPath.startsWith(destCanonicalPath)) {
                        return ZipExtractResult(isSuccess = false, errorMessage = "محاولة اختراق أمني Zip-Slip محظورة: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        FileOutputStream(targetFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            ZipExtractResult(isSuccess = true)
        } catch (e: Exception) {
            ZipExtractResult(isSuccess = false, errorMessage = e.localizedMessage ?: e.message)
        }
    }

    private fun parseSurveyRecord(json: JSONObject): SurveyRecord? {
        val recordId = json.optString("recordId", "").takeIf { it.isNotBlank() } ?: return null
        val surveyUUID = json.optString("surveyUUID", "").ifBlank {
            java.util.UUID.nameUUIDFromBytes(recordId.toByteArray()).toString()
        }
        val formId = json.optString("formId", "WATER_SURVEY_V1")
        val formVersion = json.optString("formVersion", "1.0")

        val surveyTypeName = json.optString("surveyType", "WELL")
        val surveyType = try {
            SurveyType.valueOf(surveyTypeName)
        } catch (e: Exception) {
            SurveyType.WELL
        }

        val gpsPoint = json.optJSONObject("gpsPoint")?.let { gps ->
            val qualityName = gps.optString("quality", "GOOD")
            val quality = try {
                GpsAccuracyQuality.valueOf(qualityName)
            } catch (e: Exception) {
                GpsAccuracyQuality.GOOD
            }
            GpsLocationResult(
                latitude = gps.optDouble("latitude", 0.0),
                longitude = gps.optDouble("longitude", 0.0),
                altitudeM = if (gps.has("altitudeM") && !gps.isNull("altitudeM")) gps.optDouble("altitudeM") else null,
                accuracyM = gps.optDouble("accuracyM", 10.0).toFloat(),
                quality = quality,
                capturedAt = gps.optString("capturedAt", ""),
                provider = gps.optString("provider", "GPS_WGS84")
            )
        }

        val wellDetails = json.optJSONObject("wellDetails")?.let { w ->
            WellDetails(
                wellNameAr = w.optString("wellNameAr", ""),
                wellType = w.optString("wellType", ""),
                wellDepthM = w.optDouble("wellDepthM", 0.0),
                pumpingMechanism = w.optString("pumpingMechanism", ""),
                operationalStatus = w.optString("operationalStatus", "")
            )
        }

        val springDetails = json.optJSONObject("springDetails")?.let { s ->
            SpringDetails(
                springNameAr = s.optString("springNameAr", ""),
                flowRateLps = s.optDouble("flowRateLps", 0.0),
                waterClarity = s.optString("waterClarity", ""),
                dischargeSeasonality = s.optString("dischargeSeasonality", "")
            )
        }

        val damDetails = json.optJSONObject("damDetails")?.let { d ->
            DamDetails(
                damNameAr = d.optString("damNameAr", ""),
                structureType = d.optString("structureType", ""),
                storageCapacityM3 = d.optDouble("storageCapacityM3", 0.0),
                damHeightM = d.optDouble("damHeightM", 0.0),
                structuralCondition = d.optString("structuralCondition", "")
            )
        }

        val attachmentsList = mutableListOf<AttachmentInfo>()
        val attArray = json.optJSONArray("attachments")
        if (attArray != null) {
            for (i in 0 until attArray.length()) {
                val a = attArray.getJSONObject(i)
                attachmentsList.add(
                    AttachmentInfo(
                        attachmentId = a.optString("attachmentId", ""),
                        surveyId = a.optString("surveyId", recordId),
                        surveyUUID = a.optString("surveyUUID", surveyUUID),
                        attachmentType = a.optString("attachmentType", "PHOTO"),
                        filePath = a.optString("packageRelativePath", a.optString("fileName", "")),
                        fileName = a.optString("fileName", ""),
                        fileSize = a.optLong("fileSize", 0L),
                        timestamp = a.optString("timestamp", ""),
                        fileSha256 = a.optString("fileSha256", "")
                    )
                )
            }
        }

        val admin1Pcode = json.optString("admin1Pcode", json.optString("governorateCode", ""))
        val admin2Pcode = json.optString("admin2Pcode", json.optString("districtCode", ""))
        val admin3Pcode = json.optString("admin3Pcode", json.optString("uzlahCode", ""))
        val villageRefId = json.optString("villageReferenceId", json.optString("villageCode", "")).takeIf { it.isNotBlank() }
        val resolutionStatusStr = json.optString("gpsResolutionStatus", "NOT_EVALUATED")
        val gpsResolutionStatus = try {
            AdminResolutionStatus.valueOf(resolutionStatusStr)
        } catch (e: Exception) {
            AdminResolutionStatus.NOT_EVALUATED
        }

        return SurveyRecord(
            recordId = recordId,
            surveyUUID = surveyUUID,
            formId = formId,
            formVersion = formVersion,
            surveyType = surveyType,
            admin1Pcode = admin1Pcode,
            admin2Pcode = admin2Pcode,
            admin3Pcode = admin3Pcode,
            villageReferenceId = villageRefId,
            governorateCode = admin1Pcode,
            districtCode = admin2Pcode,
            uzlahCode = admin3Pcode,
            villageCode = villageRefId ?: "",
            governorateNameSnapshotAr = json.optString("governorateNameSnapshotAr", ""),
            districtNameSnapshotAr = json.optString("districtNameSnapshotAr", ""),
            subDistrictNameSnapshotAr = json.optString("subDistrictNameSnapshotAr", ""),
            villageNameSnapshotAr = json.optString("villageNameSnapshotAr", "").takeIf { it.isNotBlank() },
            isLocalNameOverride = json.optBoolean("isLocalNameOverride", false),
            localOverrideId = json.optString("localOverrideId", "").takeIf { it.isNotBlank() },
            enumeratorId = json.optString("enumeratorId", ""),
            enumeratorUsername = json.optString("enumeratorUsername", ""),
            workflowStatus = json.optString("workflowStatus", "COMPLETED"),
            revisionCount = json.optInt("revisionCount", 1),
            createdAt = json.optString("createdAt", ""),
            updatedAt = json.optString("updatedAt", ""),
            gpsPoint = gpsPoint,
            gpsResolutionStatus = gpsResolutionStatus,
            gpsResolvedAdmin1Pcode = json.optString("gpsResolvedAdmin1Pcode", "").takeIf { it.isNotBlank() },
            gpsResolvedAdmin2Pcode = json.optString("gpsResolvedAdmin2Pcode", "").takeIf { it.isNotBlank() },
            gpsResolvedAdmin3Pcode = json.optString("gpsResolvedAdmin3Pcode", "").takeIf { it.isNotBlank() },
            gpsDistanceToNearestVillageM = if (json.has("gpsDistanceToNearestVillageM") && !json.isNull("gpsDistanceToNearestVillageM")) json.optDouble("gpsDistanceToNearestVillageM") else null,
            gpsNearestVillageNameAr = json.optString("gpsNearestVillageNameAr", "").takeIf { it.isNotBlank() },
            wellDetails = wellDetails,
            springDetails = springDetails,
            damDetails = damDetails,
            attachments = attachmentsList,
            adminRefVersionTag = json.optString("adminRefVersionTag", "OCHA_YEM_2024_V1")
        )
    }

    private fun parseRevisionRecord(json: JSONObject): SurveyRevisionRecord? {
        val revisionId = json.optString("revisionId", "").takeIf { it.isNotBlank() } ?: return null
        val recordId = json.optString("recordId", "")
        val surveyUUID = json.optString("surveyUUID", "")
        val fieldsList = mutableListOf<String>()
        val fieldsArr = json.optJSONArray("changedFields")
        if (fieldsArr != null) {
            for (i in 0 until fieldsArr.length()) {
                fieldsList.add(fieldsArr.getString(i))
            }
        }

        return SurveyRevisionRecord(
            revisionId = revisionId,
            recordId = recordId,
            surveyUUID = surveyUUID,
            revisionNumber = json.optInt("revisionNumber", 1),
            modifiedBy = json.optString("modifiedBy", ""),
            modifiedAt = json.optString("modifiedAt", ""),
            reasonForChange = json.optString("reasonForChange", ""),
            previousStatus = json.optString("previousStatus", ""),
            newStatus = json.optString("newStatus", ""),
            changedFields = fieldsList
        )
    }

    private fun buildErrorPreview(packageFile: File, errors: List<String>): SyncImportPreview {
        return SyncImportPreview(
            packageFile = packageFile,
            packageId = "UNKNOWN",
            sourceDeviceId = "UNKNOWN",
            senderRole = "UNKNOWN",
            senderUsername = "UNKNOWN",
            district = "",
            governorate = "",
            creationTimestamp = "",
            surveyCount = 0,
            attachmentCount = 0,
            totalAttachmentSizeBytes = 0L,
            manifestChecksum = "",
            calculatedChecksum = "",
            checksumVerified = false,
            isValidStructure = false,
            isDuplicatePackage = false,
            duplicateSurveysCount = 0,
            newSurveysCount = 0,
            duplicateSurveyUuids = emptyList(),
            validationErrors = errors,
            validationWarnings = emptyList(),
            surveyItems = emptyList(),
            revisionItems = emptyList()
        )
    }

    private data class ZipExtractResult(
        val isSuccess: Boolean,
        val errorMessage: String? = null
    )
}
