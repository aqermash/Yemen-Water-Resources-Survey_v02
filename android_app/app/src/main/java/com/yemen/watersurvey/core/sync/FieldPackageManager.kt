package com.yemen.watersurvey.core.sync

import android.content.Context
import com.yemen.watersurvey.data.dao.SurveyRecordDao
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * FieldPackageManager — Option C implementation.
 *
 * Manages local field packages entirely through JSON files stored under:
 *   context.filesDir/field_packages/
 *
 * No Room entity, no new table, no migration, no database version bump.
 * Package membership is tracked via surveyUUID lists inside each package JSON.
 * SurveyRecordEntity.workflowStatus drives the PACKAGED state in the database.
 *
 * Package IDs are sequential: PKG-000001, PKG-000002, PKG-000003, …
 * A counter file (counter.json) under field_packages/ tracks the last-issued ID.
 * Duplicate-UUID protection: addSurveyToPackage() is idempotent.
 */
class FieldPackageManager(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    // ---------------------------------------------------------------------------
    // Directory management
    // ---------------------------------------------------------------------------

    val packagesDir: File
        get() {
            val dir = File(context.filesDir, "field_packages")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    private val counterFile: File
        get() = File(packagesDir, "counter.json")

    // ---------------------------------------------------------------------------
    // Package ID generation (sequential, duplicate-safe)
    // ---------------------------------------------------------------------------

    /**
     * Generates the next sequential package ID.
     * Format: PKG-000001, PKG-000002, …
     * Persists counter atomically via counterFile.
     */
    @Synchronized
    fun generatePackageId(): String {
        val counter = loadCounter()
        val next = counter + 1
        saveCounter(next)
        return formatPackageId(next)
    }

    private fun formatPackageId(n: Int): String = "PKG-%06d".format(n)

    private fun loadCounter(): Int {
        if (!counterFile.exists()) return 0
        return try {
            val json = JSONObject(counterFile.readText(Charsets.UTF_8))
            json.optInt("lastIssuedSequence", 0)
        } catch (e: Exception) {
            0
        }
    }

    private fun saveCounter(value: Int) {
        val json = JSONObject()
        json.put("lastIssuedSequence", value)
        counterFile.writeText(json.toString(2), Charsets.UTF_8)
    }

    // ---------------------------------------------------------------------------
    // Package CRUD
    // ---------------------------------------------------------------------------

    /**
     * Creates a new empty package JSON file and returns the FieldPackageMeta.
     */
    fun createPackage(
        enumeratorCode: String = "",
        deviceId: String = "",
        adminLocationSummary: String = ""
    ): FieldPackageMeta {
        val packageId = generatePackageId()
        val now = dateFormat.format(Date())
        val meta = FieldPackageMeta(
            packageId = packageId,
            createdAt = now,
            updatedAt = now,
            enumeratorCode = enumeratorCode,
            deviceId = deviceId,
            adminLocationSummary = adminLocationSummary,
            exportPath = "",
            surveyUUIDs = emptyList(),
            surveyCount = 0,
            wellCount = 0,
            springCount = 0,
            damCount = 0,
            attachmentCount = 0,
            status = "CREATED"
        )
        writeMeta(meta)
        return meta
    }

    /**
     * Loads a package by ID. Returns null if not found.
     */
    fun loadPackage(packageId: String): FieldPackageMeta? {
        val file = packageFile(packageId)
        if (!file.exists()) return null
        return try {
            parseMeta(JSONObject(file.readText(Charsets.UTF_8)))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Lists all packages from the directory, sorted newest-first.
     */
    fun listPackages(): List<FieldPackageMeta> {
        val dir = packagesDir
        return dir.listFiles { f -> f.name.startsWith("PKG-") && f.name.endsWith(".json") }
            ?.mapNotNull { file ->
                try { parseMeta(JSONObject(file.readText(Charsets.UTF_8))) } catch (e: Exception) { null }
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    /**
     * Adds a survey UUID to a package.
     * Idempotent: will NOT add the same UUID twice.
     * Returns updated meta, or null if package not found.
     *
     * Call this AFTER updating workflowStatus = "PACKAGED" in the database.
     *
     * @param packageId Target package
     * @param surveyUUID UUID of the survey to add
     * @param surveyType "WELL", "SPRING", or "DAM"
     * @param attachmentCount Number of attachments on this survey
     * @param adminLocationSummary Optional location description to aggregate
     */
    @Synchronized
    fun addSurveyToPackage(
        packageId: String,
        surveyUUID: String,
        surveyType: String,
        attachmentCount: Int = 0,
        adminLocationSummary: String = ""
    ): FieldPackageMeta? {
        val meta = loadPackage(packageId) ?: return null
        if (surveyUUID in meta.surveyUUIDs) return meta // already present

        val newUUIDs = meta.surveyUUIDs + surveyUUID
        val wellCount = meta.wellCount + if (surveyType == "WELL") 1 else 0
        val springCount = meta.springCount + if (surveyType == "SPRING") 1 else 0
        val damCount = meta.damCount + if (surveyType == "DAM") 1 else 0
        val updatedLocationSummary = when {
            meta.adminLocationSummary.isBlank() -> adminLocationSummary
            adminLocationSummary.isNotBlank() && !meta.adminLocationSummary.contains(adminLocationSummary) ->
                "${meta.adminLocationSummary}, $adminLocationSummary"
            else -> meta.adminLocationSummary
        }

        val updated = meta.copy(
            surveyUUIDs = newUUIDs,
            surveyCount = newUUIDs.size,
            wellCount = wellCount,
            springCount = springCount,
            damCount = damCount,
            attachmentCount = meta.attachmentCount + attachmentCount,
            adminLocationSummary = updatedLocationSummary,
            updatedAt = dateFormat.format(Date())
        )
        writeMeta(updated)
        return updated
    }

    /**
     * Marks a package as EXPORTED, updates status in JSON.
     * Does NOT change SurveyRecordEntity.workflowStatus.
     */
    @Synchronized
    fun markPackageExported(packageId: String, exportPath: String = ""): FieldPackageMeta? {
        val meta = loadPackage(packageId) ?: return null
        val updated = meta.copy(
            status = "EXPORTED",
            exportPath = if (exportPath.isNotBlank()) exportPath else meta.exportPath,
            updatedAt = dateFormat.format(Date())
        )
        writeMeta(updated)
        return updated
    }

    // ---------------------------------------------------------------------------
    // Export integration
    // ---------------------------------------------------------------------------

    /**
     * Returns the list of surveyUUIDs belonging to this package.
     * This is the authoritative UUID set for export.
     */
    fun getSurveyUUIDsForPackage(packageId: String): List<String> {
        return loadPackage(packageId)?.surveyUUIDs ?: emptyList()
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private fun packageFile(packageId: String): File =
        File(packagesDir, "$packageId.json")

    private fun writeMeta(meta: FieldPackageMeta) {
        val json = JSONObject().apply {
            put("packageId", meta.packageId)
            put("createdAt", meta.createdAt)
            put("updatedAt", meta.updatedAt)
            put("enumeratorCode", meta.enumeratorCode)
            put("deviceId", meta.deviceId)
            put("adminLocationSummary", meta.adminLocationSummary)
            put("exportPath", meta.exportPath)
            put("surveyCount", meta.surveyCount)
            put("wellCount", meta.wellCount)
            put("springCount", meta.springCount)
            put("damCount", meta.damCount)
            put("attachmentCount", meta.attachmentCount)
            put("status", meta.status)
            val uuidsArray = JSONArray()
            meta.surveyUUIDs.forEach { uuidsArray.put(it) }
            put("surveyUUIDs", uuidsArray)
        }
        packageFile(meta.packageId).writeText(json.toString(2), Charsets.UTF_8)
    }

    private fun parseMeta(json: JSONObject): FieldPackageMeta {
        val uuidsArray = json.optJSONArray("surveyUUIDs")
        val uuids = mutableListOf<String>()
        if (uuidsArray != null) {
            for (i in 0 until uuidsArray.length()) {
                uuids.add(uuidsArray.getString(i))
            }
        }
        return FieldPackageMeta(
            packageId = json.optString("packageId", ""),
            createdAt = json.optString("createdAt", ""),
            updatedAt = json.optString("updatedAt", ""),
            enumeratorCode = json.optString("enumeratorCode", ""),
            deviceId = json.optString("deviceId", ""),
            adminLocationSummary = json.optString("adminLocationSummary", ""),
            exportPath = json.optString("exportPath", ""),
            surveyUUIDs = uuids,
            surveyCount = json.optInt("surveyCount", uuids.size),
            wellCount = json.optInt("wellCount", 0),
            springCount = json.optInt("springCount", 0),
            damCount = json.optInt("damCount", 0),
            attachmentCount = json.optInt("attachmentCount", 0),
            status = json.optString("status", "CREATED")
        )
    }
}

/**
 * Immutable data class representing field package metadata.
 * Serialized to/from JSON under filesDir/field_packages/<packageId>.json.
 *
 * status values: CREATED, EXPORTED
 */
data class FieldPackageMeta(
    val packageId: String,
    val createdAt: String,
    val updatedAt: String,
    val enumeratorCode: String,
    val deviceId: String,
    val adminLocationSummary: String = "",
    val exportPath: String = "",
    val surveyUUIDs: List<String>,
    val surveyCount: Int,
    val wellCount: Int,
    val springCount: Int,
    val damCount: Int,
    val attachmentCount: Int,
    val status: String
)
