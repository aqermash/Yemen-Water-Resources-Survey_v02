package com.yemen.watersurvey.core.form

import android.content.Context
import com.yemen.watersurvey.data.dao.DeviceSequenceDao
import com.yemen.watersurvey.data.dao.FormPackageDao
import com.yemen.watersurvey.data.dao.SurveyRecordDao
import com.yemen.watersurvey.data.database.SurveyAppDatabase
import com.yemen.watersurvey.data.entity.FormPackageEntity
import com.yemen.watersurvey.domain.model.FormPackage
import com.yemen.watersurvey.domain.model.PackageImportResult
import com.yemen.watersurvey.domain.model.PackageMetadata
import com.yemen.watersurvey.domain.model.PackageValidationResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Native Offline Form Package Management Engine for Yemen Water Survey.
 *
 * Responsibilities:
 * - Import versioned survey form packages from local storage / zip archive.
 * - Validate required files (metadata.json, form_definition.json, choices.json)
 *   and optional files (official_template.pdf, pdf_mapping.json, sequence_pool.json).
 * - Structural validation, element uniqueness, and DAG cycle detection (Phase 12A).
 * - Extract and securely store packages in internal sandboxed storage.
 * - Manage version activation & state persistence in Room database.
 * - 100% offline, zero network or cloud dependency.
 */
class FormPackageManager(
    private val context: Context,
    private val formPackageDao: FormPackageDao = SurveyAppDatabase.getInstance(context).formPackageDao(),
    private val deviceSequenceDao: DeviceSequenceDao = SurveyAppDatabase.getInstance(context).deviceSequenceDao(),
    private val surveyRecordDao: SurveyRecordDao = SurveyAppDatabase.getInstance(context).surveyRecordDao(),
    private val parser: FormDefinitionParser = FormDefinitionParser(),
    private val validator: FormPackageValidator = FormPackageValidator(parser)
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    companion object {
        const val REQUIRED_METADATA_FILE = "metadata.json"
        const val REQUIRED_FORM_DEF_FILE = "form_definition.json"
        const val REQUIRED_CHOICES_FILE = "choices.json"
        const val OPTIONAL_TEMPLATE_PDF = "official_template.pdf"
        const val OPTIONAL_PDF_MAPPING = "pdf_mapping.json"
        const val OPTIONAL_SEQUENCE_POOL = "sequence_pool.json"
    }

    /**
     * Root directory for installed form packages: context.filesDir/form_packages/
     */
    val packagesBaseDir: File
        get() {
            val dir = File(context.filesDir, "form_packages")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    /**
     * Imports a form package from a local .zip file.
     */
    suspend fun importPackageFromZip(zipFile: File): PackageImportResult {
        if (!zipFile.exists() || !zipFile.canRead()) {
            return PackageImportResult.Failure(
                errors = listOf("ملف الحزمة المضغوطة غير موجود أو يتعذر قراءته: ${zipFile.name}"),
                stage = "FILE_ACCESS"
            )
        }

        val stagingDir = File(context.cacheDir, "pkg_staging_${System.currentTimeMillis()}")
        stagingDir.mkdirs()

        return try {
            extractZipToDirectory(zipFile, stagingDir)
            val importResult = importPackageFromDirectory(stagingDir)
            importResult
        } catch (e: Exception) {
            PackageImportResult.Failure(
                errors = listOf("فشل في فك ضغط واستخراج الحزمة: ${e.localizedMessage ?: e.message}"),
                stage = "EXTRACTION"
            )
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    /**
     * Imports a form package from an unzipped local folder.
     */
    suspend fun importPackageFromDirectory(sourceDir: File): PackageImportResult {
        val validation = validatePackage(sourceDir)
        if (!validation.isValid || validation.metadata == null) {
            return PackageImportResult.Failure(
                errors = validation.errors,
                stage = "VALIDATION"
            )
        }

        val meta = validation.metadata
        val targetVersionDir = File(packagesBaseDir, "${meta.formId}/${meta.version}")
        if (targetVersionDir.exists()) {
            targetVersionDir.deleteRecursively()
        }
        targetVersionDir.mkdirs()

        try {
            // Copy validated files to target version folder
            sourceDir.copyRecursively(targetVersionDir, overwrite = true)

            val nowStr = dateFormat.format(Date())
            val checksum = validation.calculatedChecksum.ifBlank {
                calculateDirectoryChecksum(targetVersionDir)
            }

            // Check if any active version exists for this formId
            val existingActive = formPackageDao.getActivePackageForForm(meta.formId)
            val shouldBeActive = if (existingActive != null) {
                existingActive.version == meta.version // Preserve active state if re-importing the active version
            } else {
                true // Activate if no active package exists for this formId
            }

            // Hydrate complete domain model
            val formPackage = parser.hydratePackageFromDirectory(
                packageDir = targetVersionDir,
                isActive = shouldBeActive,
                installationDate = nowStr,
                checksum = checksum
            )

            // Persist to Room
            val entity = FormPackageEntity.fromDomainModel(formPackage, nowStr)
            formPackageDao.insertPackage(entity)

            if (shouldBeActive) {
                formPackageDao.setActiveVersion(meta.formId, meta.version, nowStr)
            }

            // Ingest sequence_pool.json if present
            val sequencePoolFile = File(targetVersionDir, OPTIONAL_SEQUENCE_POOL)
            if (sequencePoolFile.exists()) {
                try {
                    val ranges = parser.parseSequencePool(sequencePoolFile.readText())
                    if (ranges.isNotEmpty()) {
                        val pools = ranges.map { r ->
                            com.yemen.watersurvey.data.entity.DeviceSequencePoolEntity(
                                adminBucketKey = r.adminBucketKey,
                                facilityType = r.facilityType,
                                rangeStart = r.rangeStart,
                                rangeEnd = r.rangeEnd,
                                currentNext = r.currentNext
                            )
                        }
                        deviceSequenceDao.insertOrUpdatePools(pools)
                    }
                } catch (e: Exception) {
                    // Non-fatal parse warning
                }
            }

            return PackageImportResult.Success(
                formPackage = formPackage,
                message = "تم استيراد حزمة الاستمارة بنجاح: ${meta.name} (الإصدار ${meta.version})"
            )
        } catch (e: Exception) {
            targetVersionDir.deleteRecursively()
            return PackageImportResult.Failure(
                errors = listOf("فشل في تثبيت وتسجيل الحزمة: ${e.localizedMessage ?: e.message}"),
                stage = "INSTALLATION"
            )
        }
    }

    /**
     * Validates package structure and JSON contents against specification.
     */
    fun validatePackage(packageDir: File): PackageValidationResult {
        return validator.validatePackage(packageDir)
    }

    /**
     * Activates a specific version of a form package.
     */
    suspend fun activateVersion(formId: String, version: String): Boolean {
        val target = formPackageDao.getPackageByVersion(formId, version) ?: return false
        val nowStr = dateFormat.format(Date())
        formPackageDao.setActiveVersion(formId, version, nowStr)
        return true
    }

    /**
     * Retrieves all installed packages.
     */
    suspend fun getInstalledPackages(): List<FormPackage> {
        return formPackageDao.getAllPackages().map { it.toDomainModel() }
    }

    /**
     * Retrieves active package for a given formId, fully hydrated from disk.
     */
    suspend fun getActivePackage(formId: String): FormPackage? {
        val entity = formPackageDao.getActivePackageForForm(formId) ?: return null
        return hydratePackageEntity(entity)
    }

    /**
     * Retrieves a specific version of a form package (historical or active), fully hydrated from disk.
     */
    suspend fun getPackage(formId: String, version: String): FormPackage? {
        val entity = formPackageDao.getPackageByVersion(formId, version) ?: return null
        return hydratePackageEntity(entity)
    }

    /**
     * Resolves the active FormPackage for a given SurveyType.
     *
     * Contract: prefer the canonical, approved package IDs for the active system package, but also
     * accept legacy and test packages whose form IDs are normalized to the same facility type.
     * This keeps the Phase 14 package-aware flow intact while preserving the legacy no-package fallback.
     */
    suspend fun getActivePackageForSurveyType(surveyType: com.yemen.watersurvey.domain.model.SurveyType): FormPackage? {
        val canonicalCandidates = when (surveyType) {
            com.yemen.watersurvey.domain.model.SurveyType.WELL -> listOf(
                "yem_water_wells_saadah",
                "form-well-standard",
                "well",
                "water_well"
            )
            com.yemen.watersurvey.domain.model.SurveyType.SPRING -> listOf(
                "yem_water_springs_saadah",
                "form-spring-standard",
                "spring",
                "water_spring"
            )
            com.yemen.watersurvey.domain.model.SurveyType.DAM -> listOf(
                "yem_water_harvesting_saadah",
                "form-dam-standard",
                "dam",
                "water_harvesting"
            )
        }

        for (candidate in canonicalCandidates) {
            val candidateActive = formPackageDao.getActivePackageForForm(candidate)
            if (candidateActive != null) {
                return hydratePackageEntity(candidateActive)
            }
        }

        val allActive = formPackageDao.getActivePackages()
        for (pkgEntity in allActive) {
            val hydrated = hydratePackageEntity(pkgEntity)
            val matches = when (surveyType) {
                com.yemen.watersurvey.domain.model.SurveyType.WELL ->
                    hydrated.formId.contains("well", ignoreCase = true) ||
                    hydrated.formId.contains("water_well", ignoreCase = true) ||
                    hydrated.targetFacilityType.equals("WELL", ignoreCase = true) ||
                    hydrated.targetFacilityType.equals("WL", ignoreCase = true)
                com.yemen.watersurvey.domain.model.SurveyType.SPRING ->
                    hydrated.formId.contains("spring", ignoreCase = true) ||
                    hydrated.formId.contains("water_spring", ignoreCase = true) ||
                    hydrated.targetFacilityType.equals("SPRING", ignoreCase = true) ||
                    hydrated.targetFacilityType.equals("SP", ignoreCase = true)
                com.yemen.watersurvey.domain.model.SurveyType.DAM ->
                    hydrated.formId.contains("dam", ignoreCase = true) ||
                    hydrated.formId.contains("harvesting", ignoreCase = true) ||
                    hydrated.targetFacilityType.equals("DAM", ignoreCase = true) ||
                    hydrated.targetFacilityType.equals("WATER_HARVESTING", ignoreCase = true) ||
                    hydrated.targetFacilityType.equals("WH", ignoreCase = true)
            }
            if (matches) {
                return hydrated
            }
        }
        return null
    }

    private fun hydratePackageEntity(entity: FormPackageEntity): FormPackage {
        val pkgDir = File(entity.packagePath)
        return if (pkgDir.exists() && pkgDir.isDirectory) {
            try {
                parser.hydratePackageFromDirectory(
                    packageDir = pkgDir,
                    isActive = entity.isActive,
                    installationDate = entity.installationDate,
                    checksum = entity.checksum
                )
            } catch (e: Exception) {
                entity.toDomainModel()
            }
        } else {
            entity.toDomainModel()
        }
    }

    /**
     * Checks if a form package version is referenced by any existing survey records.
     */
    suspend fun isPackageReferencedBySurveys(formId: String, version: String): Boolean {
        return surveyRecordDao.countSurveysWithFormPackage(formId, version) > 0
    }

    /**
     * Deletes a package version from disk and Room database only if it is NOT referenced by any survey records.
     * Returns false if the package is referenced by existing surveys or does not exist.
     */
    suspend fun deletePackage(formId: String, version: String): Boolean {
        if (isPackageReferencedBySurveys(formId, version)) {
            return false
        }
        val target = formPackageDao.getPackageByVersion(formId, version) ?: return false
        val pkgDir = File(target.packagePath)
        if (pkgDir.exists()) {
            pkgDir.deleteRecursively()
        }
        formPackageDao.deletePackage(formId, version)
        return true
    }

    /**
     * Calculates SHA-256 checksum across all files in a package directory deterministically.
     */
    fun calculateDirectoryChecksum(directory: File): String {
        return validator.calculateDirectoryChecksum(directory)
    }

    /**
     * Helper to extract a ZIP archive into a destination directory safely.
     */
    fun extractZipToDirectory(zipFile: File, destinationDir: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val newFile = File(destinationDir, entry.name)
                // Prevent Zip Slip vulnerability
                val canonicalDestPath = destinationDir.canonicalPath
                val canonicalEntryPath = newFile.canonicalPath
                if (!canonicalEntryPath.startsWith(canonicalDestPath + File.separator) && canonicalEntryPath != canonicalDestPath) {
                    throw SecurityException("محاولة استخراج غير آمنة خارج مسار الحزمة: ${entry.name}")
                }

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        val buffer = ByteArray(8192)
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * Creates a valid downloadable Form Package ZIP archive locally for testing or offline deployment.
     */
    fun createSamplePackageZip(
        outputZipFile: File,
        formId: String = "form-well-standard",
        version: String = "1.0",
        nameAr: String = "استمارة حصر وتوثيق آبار المياه القياسية",
        descriptionAr: String = "الاستمارة المعتمدة رسمياً لتوثيق الآبار الارتوازية واليدوية",
        surveyType: String = "WELL"
    ): File {
        val tempFolder = File(context.cacheDir, "sample_builder_${System.currentTimeMillis()}")
        tempFolder.mkdirs()

        try {
            // 1. metadata.json
            val metaJson = JSONObject().apply {
                put("formId", formId)
                put("version", version)
                put("name", nameAr)
                put("description", descriptionAr)
                put("publisher", "وزارة المياه والبيئة - قطاع الموارد المائية")
                put("surveyType", surveyType)
                put("minAppVersion", "1.0.0")
                put("targetMinistry", "وزارة المياه والبيئة - الجمهورية اليمنية")
            }
            File(tempFolder, REQUIRED_METADATA_FILE).writeText(metaJson.toString(2))

            // 2. choices.json
            val choicesJson = JSONObject().apply {
                val wellTypes = JSONArray().apply {
                    put(JSONObject().apply { put("name", "artesian"); put("labelAr", "ارتوازي حفر آلي عميق") })
                    put(JSONObject().apply { put("name", "dug"); put("labelAr", "بئر يدوي سطحي مفتوح") })
                    put(JSONObject().apply { put("name", "hybrid"); put("labelAr", "يدوي مطور بمضخة") })
                }
                val opStatus = JSONArray().apply {
                    put(JSONObject().apply { put("name", "active"); put("labelAr", "شغال بنشاط") })
                    put(JSONObject().apply { put("name", "stopped"); put("labelAr", "متوقف مؤقتاً") })
                    put(JSONObject().apply { put("name", "abandoned"); put("labelAr", "مهجور / غير صالح") })
                }
                put("well_types", wellTypes)
                put("op_status", opStatus)
            }
            File(tempFolder, REQUIRED_CHOICES_FILE).writeText(choicesJson.toString(2))

            // 3. form_definition.json
            val formDefJson = JSONObject().apply {
                put("formId", formId)
                put("title", nameAr)
                val elements = JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", "well_name")
                        put("type", "text")
                        put("labelAr", "اسم البئر الميداني")
                        put("required", true)
                    })
                    put(JSONObject().apply {
                        put("name", "well_type")
                        put("type", "select_one")
                        put("labelAr", "نوع البئر")
                        put("choicesList", "well_types")
                        put("required", true)
                    })
                    put(JSONObject().apply {
                        put("name", "well_depth")
                        put("type", "decimal")
                        put("labelAr", "العمق الإجمالي (متر)")
                        put("required", false)
                    })
                    put(JSONObject().apply {
                        put("name", "operational_status")
                        put("type", "select_one")
                        put("labelAr", "الحالة التشغيلية")
                        put("choicesList", "op_status")
                        put("required", true)
                    })
                }
                put("elements", elements)
                put("questions", elements)
            }
            File(tempFolder, REQUIRED_FORM_DEF_FILE).writeText(formDefJson.toString(2))

            // 4. pdf_mapping.json
            val pdfMappingJson = JSONObject().apply {
                put("surveyType", surveyType)
                put("templateTitleAr", nameAr)
                put("pageWidthPt", 595.28)
                put("pageHeightPt", 841.89)
                val fields = JSONArray().apply {
                    put(JSONObject().apply { put("key", "wellNameAr"); put("labelAr", "اسم البئر"); put("x", 520.0); put("y", 220.0) })
                    put(JSONObject().apply { put("key", "wellType"); put("labelAr", "نوع البئر"); put("x", 520.0); put("y", 250.0) })
                    put(JSONObject().apply { put("key", "operationalStatus"); put("labelAr", "الحالة التشغيلية"); put("x", 250.0); put("y", 280.0) })
                }
                put("fields", fields)
            }
            File(tempFolder, OPTIONAL_PDF_MAPPING).writeText(pdfMappingJson.toString(2))

            // 5. sequence_pool.json
            val sequencePoolJson = JSONObject().apply {
                val pools = JSONArray().apply {
                    put(JSONObject().apply {
                        put("adminBucketKey", "YE110101")
                        put("facilityType", "WL")
                        put("rangeStart", 1)
                        put("rangeEnd", 100)
                        put("currentNext", 1)
                    })
                    put(JSONObject().apply {
                        put("adminBucketKey", "YE110101")
                        put("facilityType", "SP")
                        put("rangeStart", 1)
                        put("rangeEnd", 100)
                        put("currentNext", 1)
                    })
                    put(JSONObject().apply {
                        put("adminBucketKey", "YE110101")
                        put("facilityType", "WH")
                        put("rangeStart", 1)
                        put("rangeEnd", 100)
                        put("currentNext", 1)
                    })
                }
                put("provisionedPools", pools)
            }
            File(tempFolder, OPTIONAL_SEQUENCE_POOL).writeText(sequencePoolJson.toString(2))

            // 6. Build ZIP
            outputZipFile.parentFile?.mkdirs()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZipFile))).use { zos ->
                val files = tempFolder.listFiles() ?: emptyArray()
                for (file in files) {
                    val entry = ZipEntry(file.name)
                    zos.putNextEntry(entry)
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }

            return outputZipFile
        } finally {
            tempFolder.deleteRecursively()
        }
    }
}
