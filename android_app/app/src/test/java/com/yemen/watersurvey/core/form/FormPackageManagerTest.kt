package com.yemen.watersurvey.core.form

import android.content.Context
import com.yemen.watersurvey.data.dao.DeviceSequenceDao
import com.yemen.watersurvey.data.dao.FormPackageDao
import com.yemen.watersurvey.data.entity.DeviceSequencePoolEntity
import com.yemen.watersurvey.data.entity.FormPackageEntity
import com.yemen.watersurvey.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit Tests for Native Android Form Package Management System (Phase 8).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class FormPackageManagerTest {

    private lateinit var testBaseDir: File
    private lateinit var cacheDir: File
    private lateinit var mockDao: InMemoryFormPackageDao
    private lateinit var packageManager: FormPackageManager

    @Before
    fun setup() {
        testBaseDir = File(System.getProperty("java.io.tmpdir"), "form_pkg_test_${System.currentTimeMillis()}")
        cacheDir = File(testBaseDir, "cache")
        testBaseDir.mkdirs()
        cacheDir.mkdirs()

        mockDao = InMemoryFormPackageDao()
        val mockSeqDao = InMemoryDeviceSequenceDao()
        val testContext = TestContext(testBaseDir, cacheDir)
        packageManager = FormPackageManager(testContext, mockDao, mockSeqDao)
    }

    @Test
    fun testValidPackageImportAndExtractionFromZip() = runBlocking {
        val zipFile = File(cacheDir, "test_well_package_v1.zip")
        packageManager.createSamplePackageZip(
            outputZipFile = zipFile,
            formId = "form-well-standard",
            version = "1.0",
            nameAr = "استمارة آبار المياه الرسمية",
            descriptionAr = "الاستمارة المعتمدة لتوثيق الآبار في اليمن",
            surveyType = "WELL"
        )

        assertTrue("Generated test zip must exist", zipFile.exists())
        assertTrue("Zip file size must be > 0", zipFile.length() > 0)

        val importResult = packageManager.importPackageFromZip(zipFile)
        assertTrue("Import result must be Success", importResult is PackageImportResult.Success)

        val success = importResult as PackageImportResult.Success
        val formPkg = success.formPackage

        assertEquals("form-well-standard", formPkg.formId)
        assertEquals("1.0", formPkg.version)
        assertEquals("استمارة آبار المياه الرسمية", formPkg.name)
        assertTrue("First imported package must be active", formPkg.isActive)
        assertTrue("Checksum must be non-empty", formPkg.checksum.isNotBlank())
        assertTrue("Package directory must exist", File(formPkg.packagePath).exists())
        assertTrue("definition.json must exist in installed dir", File(formPkg.packagePath, "form_definition.json").exists())
        assertTrue("metadata.json must exist in installed dir", File(formPkg.packagePath, "metadata.json").exists())
        assertTrue("choices.json must exist in installed dir", File(formPkg.packagePath, "choices.json").exists())

        // Check DAO state
        val active = mockDao.getActivePackageForForm("form-well-standard")
        assertNotNull(active)
        assertEquals("1.0", active?.version)
        assertTrue(active?.isActive == true)
    }

    @Test
    fun testValidationRejectsMissingRequiredFiles() {
        val invalidPkgDir = File(testBaseDir, "invalid_pkg_missing_metadata")
        invalidPkgDir.mkdirs()

        // Only create choices.json, missing metadata.json and form_definition.json
        File(invalidPkgDir, "choices.json").writeText("{ \"choices\": [] }")

        val validation = packageManager.validatePackage(invalidPkgDir)
        assertFalse("Package must fail validation when metadata.json is missing", validation.isValid)
        assertTrue("Errors must mention metadata.json", validation.errors.any { it.contains("metadata.json") })
        assertTrue("Errors must mention form_definition.json", validation.errors.any { it.contains("form_definition.json") })
    }

    @Test
    fun testValidationRejectsInvalidJsonFormat() {
        val corruptDir = File(testBaseDir, "corrupt_pkg")
        corruptDir.mkdirs()

        File(corruptDir, "metadata.json").writeText("{ unclosed json: ")
        File(corruptDir, "form_definition.json").writeText("invalid content")
        File(corruptDir, "choices.json").writeText("{ }")

        val validation = packageManager.validatePackage(corruptDir)
        assertFalse("Package must fail validation for malformed JSON", validation.isValid)
        assertTrue(validation.errors.isNotEmpty())
    }

    @Test
    fun testVersionHandlingAndActivation() = runBlocking {
        // 1. Import Version 1.0
        val zipV1 = File(cacheDir, "well_v1.zip")
        packageManager.createSamplePackageZip(zipV1, "form-well-standard", "1.0", "استمارة الآبار", "v1.0", "WELL")
        packageManager.importPackageFromZip(zipV1)

        // 2. Import Version 2.0
        val zipV2 = File(cacheDir, "well_v2.zip")
        packageManager.createSamplePackageZip(zipV2, "form-well-standard", "2.0", "استمارة الآبار المحدثة", "v2.0", "WELL")
        packageManager.importPackageFromZip(zipV2)

        val allPackages = packageManager.getInstalledPackages()
        assertEquals("Should have 2 versions installed", 2, allPackages.size)

        // Verify version 1.0 is currently active, 2.0 is inactive
        val activeInitial = packageManager.getActivePackage("form-well-standard")
        assertEquals("1.0", activeInitial?.version)

        // Activate version 2.0
        val activationSuccess = packageManager.activateVersion("form-well-standard", "2.0")
        assertTrue("Activation should succeed", activationSuccess)

        // Verify version 2.0 is now active and 1.0 is inactive
        val activeAfter = packageManager.getActivePackage("form-well-standard")
        assertEquals("2.0", activeAfter?.version)
        assertTrue(activeAfter?.isActive == true)

        val v1After = mockDao.getPackageByVersion("form-well-standard", "1.0")
        assertFalse("Version 1.0 must be deactivated", v1After?.isActive == true)
    }

    @Test
    fun testDeterministicChecksumCalculation() {
        val testDir = File(testBaseDir, "checksum_dir")
        testDir.mkdirs()

        File(testDir, "metadata.json").writeText("{\"formId\":\"test\"}")
        File(testDir, "form_definition.json").writeText("{\"questions\":[]}")

        val checksum1 = packageManager.calculateDirectoryChecksum(testDir)
        val checksum2 = packageManager.calculateDirectoryChecksum(testDir)

        assertNotNull(checksum1)
        assertEquals(64, checksum1.length) // SHA-256 length in hex is 64 characters
        assertEquals("Checksum must be deterministic across calls", checksum1, checksum2)

        // Modify file and confirm checksum changes
        File(testDir, "form_definition.json").writeText("{\"questions\":[{\"name\":\"q1\"}]}")
        val checksumModified = packageManager.calculateDirectoryChecksum(testDir)
        assertNotEquals("Modified file must produce different checksum", checksum1, checksumModified)
    }

    @Test
    fun testSurveyRecordCompatibility() {
        // Ensure survey data models remain completely independent of form package changes
        val record = SurveyRecord(
            recordId = "rec-pkg-001",
            surveyType = SurveyType.WELL,
            governorateCode = "YEM-30",
            districtCode = "YEM-30-01",
            uzlahCode = "YEM-30-01-01",
            villageCode = "YEM-30-01-01-001",
            createdAt = "2026-08-14 10:00:00",
            wellDetails = WellDetails(
                wellNameAr = "بئر السلام الارتوازي",
                wellType = "ارتوازي",
                wellDepthM = 180.0,
                pumpingMechanism = "طاقة شمسية",
                operationalStatus = "شغال"
            )
        )

        assertEquals("rec-pkg-001", record.recordId)
        assertEquals(SurveyType.WELL, record.surveyType)
        assertEquals("بئر السلام الارتوازي", record.wellDetails?.wellNameAr)
    }

    /**
     * In-Memory Room DAO implementation for rapid local offline testing.
     */
    private class InMemoryFormPackageDao : FormPackageDao {
        private val storage = mutableMapOf<String, FormPackageEntity>()

        override fun getAllPackagesFlow(): Flow<List<FormPackageEntity>> {
            return flowOf(storage.values.toList())
        }

        override suspend fun getAllPackages(): List<FormPackageEntity> {
            return storage.values.toList().sortedWith(compareBy({ it.formId }, { it.version }))
        }

        override suspend fun getActivePackages(): List<FormPackageEntity> {
            return storage.values.filter { it.isActive }
        }

        override suspend fun getActivePackageForForm(formId: String): FormPackageEntity? {
            return storage.values.find { it.formId == formId && it.isActive }
        }

        override suspend fun getPackageByVersion(formId: String, version: String): FormPackageEntity? {
            return storage["${formId}@${version}"]
        }

        override suspend fun insertPackage(entity: FormPackageEntity) {
            storage[entity.packageUid] = entity
        }

        override suspend fun updatePackage(entity: FormPackageEntity) {
            storage[entity.packageUid] = entity
        }

        override suspend fun deactivateAllVersionsForForm(formId: String) {
            val toUpdate = storage.values.filter { it.formId == formId }
            for (item in toUpdate) {
                storage[item.packageUid] = item.copy(isActive = false)
            }
        }

        override suspend fun activatePackageVersion(formId: String, version: String, updatedAt: String) {
            val key = "${formId}@${version}"
            storage[key]?.let {
                storage[key] = it.copy(isActive = true, updatedAt = updatedAt)
            }
        }

        override suspend fun deletePackage(formId: String, version: String) {
            storage.remove("${formId}@${version}")
        }

        override suspend fun getVersionCount(formId: String): Int {
            return storage.values.count { it.formId == formId }
        }
    }

    private class InMemoryDeviceSequenceDao : DeviceSequenceDao {
        private val pools = mutableListOf<DeviceSequencePoolEntity>()
        override suspend fun getPool(adminBucketKey: String, facilityType: String): DeviceSequencePoolEntity? {
            return pools.find { it.adminBucketKey == adminBucketKey && it.facilityType == facilityType }
        }
        override suspend fun insertOrUpdatePool(pool: DeviceSequencePoolEntity) {
            pools.removeAll { it.adminBucketKey == pool.adminBucketKey && it.facilityType == pool.facilityType }
            pools.add(pool)
        }
        override suspend fun insertOrUpdatePools(pools: List<DeviceSequencePoolEntity>) {
            pools.forEach { insertOrUpdatePool(it) }
        }
        override suspend fun getAllPools(): List<DeviceSequencePoolEntity> = pools.toList()
    }

    private class TestContext(
        private val baseDir: File,
        private val testCacheDir: File,
        appContext: Context = RuntimeEnvironment.getApplication()
    ) : android.content.ContextWrapper(appContext) {
        override fun getFilesDir(): File = baseDir
        override fun getCacheDir(): File = testCacheDir
        override fun getApplicationContext(): Context = this
    }
}
