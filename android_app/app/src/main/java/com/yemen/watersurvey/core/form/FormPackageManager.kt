package com.yemen.watersurvey.core.form

import android.content.Context
import com.yemen.watersurvey.data.dao.DeviceSequenceDao
import com.yemen.watersurvey.data.dao.FormPackageDao
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
 *   and optional files (official_template.pdf, pdf_mapping.json).
 * - Extract and securely store packages in internal sandboxed storage.
 * - Manage version activation & state persistence in Room database.
 * - 100% offline, zero network or cloud dependency.
 */
class FormPackageManager(
    private val context: Context,
    private val formPackageDao: FormPackageDao = SurveyAppDatabase.getInstance(context).formPackageDao(),
    private val deviceSequenceDao: DeviceSequenceDao = SurveyAppDatabase.getInstance(context).deviceSequenceDao()
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

            val hasPdfTemplate = File(targetVersionDir, OPTIONAL_TEMPLATE_PDF).exists()
            val hasPdfMapping = File(targetVersionDir, OPTIONAL_PDF_MAPPING).exists()

            // Check if any active version exists for this formId
            val existingActive = formPackageDao.getActivePackageForForm(meta.formId)
            val shouldBeActive = existingActive == null

            val formPackage = FormPackage(
                formId = meta.formId,
                version = meta.version,
                name = meta.name,
                description = meta.description,
                publisher = meta.publisher,
                checksum = checksum,
                installationDate = nowStr,
                isActive = shouldBeActive,
                packagePath = targetVersionDir.absolutePath,
                hasFormDefinition = true,
                hasChoices = true,
                hasPdfTemplate = hasPdfTemplate,
                hasPdfMapping = hasPdfMapping,
                metadataExtra = meta.extraProperties
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
                    val json = JSONObject(sequencePoolFile.readText())
                    if (json.has("provisionedPools")) {
                        val array = json.getJSONArray("provisionedPools")
                        val pools = mutableListOf<com.yemen.watersurvey.data.entity.DeviceSequencePoolEntity>()
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            pools.add(
                                com.yemen.watersurvey.data.entity.DeviceSequencePoolEntity(
                                    adminBucketKey = obj.getString("adminBucketKey"),
                                    facilityType = obj.getString("facilityType"),
                                    rangeStart = obj.getInt("rangeStart"),
                                    rangeEnd = obj.getInt("rangeEnd"),
                                    currentNext = obj.optInt("currentNext", obj.getInt("rangeStart"))
                                )
                            )
                        }
                        if (pools.isNotEmpty()) {
                            deviceSequenceDao.insertOrUpdatePools(pools)
                        }
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
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val checkedFiles = mutableListOf<String>()

        if (!packageDir.exists() || !packageDir.isDirectory) {
            return PackageValidationResult(
                isValid = false,
                errors = listOf("المجلد المحدد للحزمة غير موجود أو غير صالح: ${packageDir.absolutePath}")
            )
        }

        // 1. Validate Required: metadata.json
        val metadataFile = File(packageDir, REQUIRED_METADATA_FILE)
        var parsedMetadata: PackageMetadata? = null

        if (!metadataFile.exists()) {
            errors.add("الملف الإلزامي مفقود: $REQUIRED_METADATA_FILE (بيانات وصف الحزمة)")
        } else {
            checkedFiles.add(REQUIRED_METADATA_FILE)
            try {
                val json = JSONObject(metadataFile.readText())
                val formId = json.optString("formId", "").trim()
                val version = json.optString("version", "").trim()
                val name = json.optString("name", "").trim()
                val description = json.optString("description", "").trim()
                val publisher = json.optString("publisher", "").trim()

                if (formId.isEmpty()) errors.add("حقل 'formId' فارغ أو غير موجود في $REQUIRED_METADATA_FILE")
                if (version.isEmpty()) errors.add("حقل 'version' فارغ أو غير موجود في $REQUIRED_METADATA_FILE")
                if (name.isEmpty()) errors.add("حقل 'name' فارغ أو غير موجود في $REQUIRED_METADATA_FILE")

                val extra = mutableMapOf<String, String>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k !in setOf("formId", "version", "name", "description", "publisher")) {
                        extra[k] = json.optString(k, "")
                    }
                }

                if (errors.isEmpty()) {
                    parsedMetadata = PackageMetadata(
                        formId = formId,
                        version = version,
                        name = name,
                        description = description,
                        publisher = publisher.ifBlank { "وزارة المياه والبيئة - اليمن" },
                        surveyType = json.optString("surveyType", "WELL"),
                        minAppVersion = json.optString("minAppVersion", "1.0.0"),
                        targetMinistry = json.optString("targetMinistry", "وزارة المياه والبيئة - الجمهورية اليمنية"),
                        extraProperties = extra
                    )
                }
            } catch (e: Exception) {
                errors.add("تنسيق JSON غير صالح في $REQUIRED_METADATA_FILE: ${e.message}")
            }
        }

        // 2. Validate Required: form_definition.json
        val formDefFile = File(packageDir, REQUIRED_FORM_DEF_FILE)
        if (!formDefFile.exists()) {
            errors.add("الملف الإلزامي مفقود: $REQUIRED_FORM_DEF_FILE (تعريف أسئلة وحقول الاستمارة)")
        } else {
            checkedFiles.add(REQUIRED_FORM_DEF_FILE)
            try {
                val content = formDefFile.readText().trim()
                if (content.startsWith("{")) {
                    val json = JSONObject(content)
                    if (!json.has("questions") && !json.has("fields") && !json.has("elements")) {
                        warnings.add("ملف $REQUIRED_FORM_DEF_FILE لا يحتوي على مصفوفة أسئلة قياسية ('questions')")
                    }
                } else if (content.startsWith("[")) {
                    JSONArray(content)
                } else {
                    errors.add("ملف $REQUIRED_FORM_DEF_FILE لا يحتوي على كائن أو مصفوفة JSON صالحة")
                }
            } catch (e: Exception) {
                errors.add("تنسيق JSON غير صالح في $REQUIRED_FORM_DEF_FILE: ${e.message}")
            }
        }

        // 3. Validate Required: choices.json
        val choicesFile = File(packageDir, REQUIRED_CHOICES_FILE)
        if (!choicesFile.exists()) {
            errors.add("الملف الإلزامي مفقود: $REQUIRED_CHOICES_FILE (خيارات القوائم المنسدلة والتصنيفات)")
        } else {
            checkedFiles.add(REQUIRED_CHOICES_FILE)
            try {
                val content = choicesFile.readText().trim()
                if (content.startsWith("{")) {
                    JSONObject(content)
                } else if (content.startsWith("[")) {
                    JSONArray(content)
                } else {
                    errors.add("ملف $REQUIRED_CHOICES_FILE لا يحتوي على كائن أو مصفوفة JSON صالحة")
                }
            } catch (e: Exception) {
                errors.add("تنسيق JSON غير صالح في $REQUIRED_CHOICES_FILE: ${e.message}")
            }
        }

        // 4. Validate Optional: official_template.pdf
        val templatePdf = File(packageDir, OPTIONAL_TEMPLATE_PDF)
        if (templatePdf.exists()) {
            checkedFiles.add(OPTIONAL_TEMPLATE_PDF)
            if (templatePdf.length() == 0L) {
                warnings.add("ملف $OPTIONAL_TEMPLATE_PDF فارغ (0 بايت)")
            }
        }

        // 5. Validate Optional: pdf_mapping.json
        val pdfMappingFile = File(packageDir, OPTIONAL_PDF_MAPPING)
        if (pdfMappingFile.exists()) {
            checkedFiles.add(OPTIONAL_PDF_MAPPING)
            try {
                val json = JSONObject(pdfMappingFile.readText())
                if (!json.has("fields")) {
                    warnings.add("ملف $OPTIONAL_PDF_MAPPING لا يحتوي على مصفوفة حقول الإحداثيات 'fields'")
                }
            } catch (e: Exception) {
                warnings.add("تنبيه: تنسيق JSON غير قياسي في $OPTIONAL_PDF_MAPPING: ${e.message}")
            }
        }

        val checksum = if (errors.isEmpty()) calculateDirectoryChecksum(packageDir) else ""

        return PackageValidationResult(
            isValid = errors.isEmpty() && parsedMetadata != null,
            metadata = parsedMetadata,
            errors = errors,
            warnings = warnings,
            checkedFiles = checkedFiles,
            calculatedChecksum = checksum
        )
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
     * Retrieves active package for a given formId.
     */
    suspend fun getActivePackage(formId: String): FormPackage? {
        return formPackageDao.getActivePackageForForm(formId)?.toDomainModel()
    }

    /**
     * Deletes a package version from disk and Room database.
     */
    suspend fun deletePackage(formId: String, version: String): Boolean {
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
        if (!directory.exists() || !directory.isDirectory) return ""
        val digest = MessageDigest.getInstance("SHA-256")

        val files = directory.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.relativeTo(directory).path }
            .toList()

        for (file in files) {
            val relativePath = file.relativeTo(directory).path.toByteArray(Charsets.UTF_8)
            digest.update(relativePath)
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
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

            // 2. form_definition.json
            val formDefJson = JSONObject().apply {
                put("formId", formId)
                put("title", nameAr)
                val questions = JSONArray().apply {
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
                put("questions", questions)
            }
            File(tempFolder, REQUIRED_FORM_DEF_FILE).writeText(formDefJson.toString(2))

            // 3. choices.json
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
                        put("adminBucketKey", "YE221501")
                        put("facilityType", "WL")
                        put("rangeStart", 1001)
                        put("rangeEnd", 1100)
                        put("currentNext", 1001)
                    })
                    put(JSONObject().apply {
                        put("adminBucketKey", "YE221501")
                        put("facilityType", "SP")
                        put("rangeStart", 1001)
                        put("rangeEnd", 1100)
                        put("currentNext", 1001)
                    })
                    put(JSONObject().apply {
                        put("adminBucketKey", "YE221501")
                        put("facilityType", "WH")
                        put("rangeStart", 1001)
                        put("rangeEnd", 1100)
                        put("currentNext", 1001)
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
