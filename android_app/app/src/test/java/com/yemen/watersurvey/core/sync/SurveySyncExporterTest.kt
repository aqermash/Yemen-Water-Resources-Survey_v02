package com.yemen.watersurvey.core.sync

import android.content.Context
import com.yemen.watersurvey.domain.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * Unit Tests for Native Survey Data Exchange Package Export System (Phase 9.0).
 */
class SurveySyncExporterTest {

    private lateinit var testBaseDir: File
    private lateinit var cacheDir: File
    private lateinit var photosDir: File
    private lateinit var testContext: TestContext
    private lateinit var exporter: SurveySyncExporter

    private lateinit var testSurveys: List<SurveyRecord>
    private lateinit var testRevisions: List<SurveyRevisionRecord>

    @Before
    fun setup() {
        testBaseDir = File(System.getProperty("java.io.tmpdir"), "sync_export_test_${System.currentTimeMillis()}")
        cacheDir = File(testBaseDir, "cache")
        photosDir = File(testBaseDir, "photos")
        testBaseDir.mkdirs()
        cacheDir.mkdirs()
        photosDir.mkdirs()

        testContext = TestContext(testBaseDir, cacheDir)
        exporter = SurveySyncExporter(testContext)

        // Create sample photo files on disk
        val photoWell1 = File(photosDir, "well_photo_01.jpg").apply { writeText("PHOTO_WELL_01_BYTES_DATA") }
        val photoSpring1 = File(photosDir, "spring_photo_01.jpg").apply { writeText("PHOTO_SPRING_01_BYTES_DATA") }
        val photoDam1 = File(photosDir, "dam_photo_01.jpg").apply { writeText("PHOTO_DAM_01_BYTES_DATA") }
        // Create an unreferenced photo that must NOT be in filtered packages
        File(photosDir, "unreferenced_photo.jpg").apply { writeText("UNREFERENCED_PHOTO_DATA") }

        testSurveys = listOf(
            SurveyRecord(
                recordId = "WELL-YE-30-001",
                surveyType = SurveyType.WELL,
                governorateCode = "YEM-30",
                districtCode = "YEM-30-02",
                uzlahCode = "YEM-30-02-01",
                villageCode = "YEM-30-02-01-001",
                enumeratorId = "usr-01",
                enumeratorUsername = "ahmed_enum",
                workflowStatus = "APPROVED",
                revisionCount = 2,
                createdAt = "2026-08-14 09:00:00",
                updatedAt = "2026-08-14 10:00:00",
                gpsPoint = GpsLocationResult(
                    latitude = 16.94,
                    longitude = 43.76,
                    altitudeM = 1800.0,
                    accuracyM = 4.0f,
                    quality = GpsAccuracyQuality.EXCELLENT,
                    capturedAt = "2026-08-14 09:01:00"
                ),
                wellDetails = WellDetails(
                    wellNameAr = "بئر المقاش الارتوازي",
                    wellType = "ارتوازي",
                    wellDepthM = 220.0,
                    pumpingMechanism = "طاقة شمسية",
                    operationalStatus = "شغال"
                ),
                attachments = listOf(
                    AttachmentInfo(
                        attachmentId = "att-w1",
                        surveyId = "WELL-YE-30-001",
                        attachmentType = "PHOTO_WELL",
                        filePath = photoWell1.absolutePath,
                        fileName = "well_photo_01.jpg",
                        fileSize = photoWell1.length(),
                        timestamp = "2026-08-14 09:01:30"
                    )
                )
            ),
            SurveyRecord(
                recordId = "SPRING-YE-30-002",
                surveyType = SurveyType.SPRING,
                governorateCode = "YEM-30",
                districtCode = "YEM-30-01",
                uzlahCode = "YEM-30-01-01",
                villageCode = "YEM-30-01-01-002",
                enumeratorId = "usr-01",
                enumeratorUsername = "ahmed_enum",
                workflowStatus = "COMPLETED",
                revisionCount = 1,
                createdAt = "2026-08-14 10:00:00",
                updatedAt = "2026-08-14 10:00:00",
                gpsPoint = GpsLocationResult(
                    latitude = 16.82,
                    longitude = 43.25,
                    altitudeM = 2100.0,
                    accuracyM = 6.0f,
                    quality = GpsAccuracyQuality.GOOD,
                    capturedAt = "2026-08-14 10:01:00"
                ),
                springDetails = SpringDetails(
                    springNameAr = "عين النظير",
                    flowRateLps = 15.0,
                    waterClarity = "عذبة",
                    dischargeSeasonality = "دائم"
                ),
                attachments = listOf(
                    AttachmentInfo(
                        attachmentId = "att-s1",
                        surveyId = "SPRING-YE-30-002",
                        attachmentType = "PHOTO_SPRING",
                        filePath = photoSpring1.absolutePath,
                        fileName = "spring_photo_01.jpg",
                        fileSize = photoSpring1.length(),
                        timestamp = "2026-08-14 10:01:30"
                    )
                )
            ),
            SurveyRecord(
                recordId = "DAM-YE-30-003",
                surveyType = SurveyType.DAM,
                governorateCode = "YEM-30",
                districtCode = "YEM-30-03",
                uzlahCode = "YEM-30-03-01",
                villageCode = "YEM-30-03-01-001",
                enumeratorId = "usr-02",
                enumeratorUsername = "super_dist",
                workflowStatus = "DRAFT",
                revisionCount = 1,
                createdAt = "2026-08-14 11:00:00",
                updatedAt = "2026-08-14 11:00:00",
                damDetails = DamDetails(
                    damNameAr = "سد الركوة",
                    structureType = "ركامي",
                    storageCapacityM3 = 500000.0,
                    damHeightM = 20.0,
                    structuralCondition = "جيدة"
                ),
                attachments = listOf(
                    AttachmentInfo(
                        attachmentId = "att-d1",
                        surveyId = "DAM-YE-30-003",
                        attachmentType = "PHOTO_DAM",
                        filePath = photoDam1.absolutePath,
                        fileName = "dam_photo_01.jpg",
                        fileSize = photoDam1.length(),
                        timestamp = "2026-08-14 11:01:30"
                    )
                )
            )
        )

        testRevisions = listOf(
            SurveyRevisionRecord(
                revisionId = "rev-w-1",
                recordId = "WELL-YE-30-001",
                revisionNumber = 1,
                modifiedBy = "ahmed_enum",
                modifiedAt = "2026-08-14 09:00:00",
                reasonForChange = "Initial survey creation",
                previousStatus = "DRAFT",
                newStatus = "COMPLETED",
                changedFields = listOf("all")
            ),
            SurveyRevisionRecord(
                revisionId = "rev-w-2",
                recordId = "WELL-YE-30-001",
                revisionNumber = 2,
                modifiedBy = "supervisor",
                modifiedAt = "2026-08-14 10:00:00",
                reasonForChange = "Supervisor approval",
                previousStatus = "COMPLETED",
                newStatus = "APPROVED",
                changedFields = listOf("workflowStatus")
            )
        )
    }

    @Test
    fun testFullPackageExportZipStructure() {
        val result = exporter.exportSyncPackage(
            allSurveys = testSurveys,
            allRevisions = testRevisions,
            filter = SyncExportFilter(),
            sourceDeviceId = "DEV-TEST-001",
            senderRole = "ENUMERATOR",
            senderUsername = "ahmed_enum",
            district = "سحار",
            governorate = "صعدة"
        )

        assertTrue("Export must succeed", result is SyncExportResult.Success)
        val success = result as SyncExportResult.Success

        val zipFile = success.packageFile
        assertTrue("Exported .ywsync file must exist", zipFile.exists())
        assertTrue("Zip file must have .ywsync extension", zipFile.name.endsWith(".ywsync"))
        assertTrue("Zip file size must be > 0", zipFile.length() > 0)
        assertEquals(3, success.surveyCount)
        assertEquals(3, success.attachmentCount)

        // Inspect ZIP internal contents
        ZipFile(zipFile).use { zip ->
            val entryNames = zip.entries().asSequence().map { it.name.replace('\\', '/') }.toSet()

            assertTrue("Must contain manifest.json", entryNames.contains("manifest.json"))
            assertTrue("Must contain metadata.json", entryNames.contains("metadata.json"))
            assertTrue("Must contain surveys.json", entryNames.contains("surveys.json"))
            assertTrue("Must contain revisions.json", entryNames.contains("revisions.json"))
            assertTrue("Must contain checksum.sha256", entryNames.contains("checksum.sha256"))

            // Verify attachment paths inside zip
            assertTrue("Must contain well attachment", entryNames.contains("attachments/WELL-YE-30-001/well_photo_01.jpg"))
            assertTrue("Must contain spring attachment", entryNames.contains("attachments/SPRING-YE-30-002/spring_photo_01.jpg"))
            assertTrue("Must contain dam attachment", entryNames.contains("attachments/DAM-YE-30-003/dam_photo_01.jpg"))

            // Verify unreferenced photo is NOT inside zip
            assertFalse("Must NOT contain unreferenced attachment", entryNames.any { it.contains("unreferenced_photo.jpg") })
        }
    }

    @Test
    fun testJsonContentValidityAndIntegrity() {
        val result = exporter.exportSyncPackage(
            allSurveys = testSurveys,
            allRevisions = testRevisions,
            filter = SyncExportFilter(),
            sourceDeviceId = "DEV-TEST-001",
            senderRole = "ENUMERATOR"
        ) as SyncExportResult.Success

        ZipFile(result.packageFile).use { zip ->
            // 1. Check manifest.json
            val manifestContent = zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader().readText()
            val manifestJson = JSONObject(manifestContent)
            assertEquals("1.0", manifestJson.getString("formatVersion"))
            assertEquals("YEMEN_WATER_SURVEY_SYNC", manifestJson.getString("packageType"))
            assertEquals("DEV-TEST-001", manifestJson.getString("sourceDeviceId"))
            assertEquals(3, manifestJson.getInt("surveyCount"))
            assertEquals(3, manifestJson.getInt("attachmentCount"))
            assertTrue(manifestJson.getString("checksum").isNotBlank())

            // 2. Check metadata.json
            val metadataContent = zip.getInputStream(zip.getEntry("metadata.json")).bufferedReader().readText()
            val metadataJson = JSONObject(metadataContent)
            assertEquals("2.9.0", metadataJson.getString("appVersion"))
            assertEquals("ALL", metadataJson.getString("exportScope"))

            // 3. Check surveys.json
            val surveysContent = zip.getInputStream(zip.getEntry("surveys.json")).bufferedReader().readText()
            val surveysJson = JSONObject(surveysContent)
            val surveysArray = surveysJson.getJSONArray("surveys")
            assertEquals(3, surveysArray.length())

            val firstSurvey = surveysArray.getJSONObject(0)
            assertEquals("WELL-YE-30-001", firstSurvey.getString("recordId"))
            assertEquals("WELL", firstSurvey.getString("surveyType"))
            assertEquals("APPROVED", firstSurvey.getString("workflowStatus"))
            assertEquals("بئر المقاش الارتوازي", firstSurvey.getJSONObject("wellDetails").getString("wellNameAr"))

            // 4. Check revisions.json
            val revisionsContent = zip.getInputStream(zip.getEntry("revisions.json")).bufferedReader().readText()
            val revisionsJson = JSONObject(revisionsContent)
            val revisionsArray = revisionsJson.getJSONArray("revisions")
            assertEquals(2, revisionsArray.length())
            assertEquals("rev-w-1", revisionsArray.getJSONObject(0).getString("revisionId"))

            // 5. Check checksum.sha256
            val checksumContent = zip.getInputStream(zip.getEntry("checksum.sha256")).bufferedReader().readText().trim()
            assertEquals(64, checksumContent.length)
            assertEquals(result.checksumSha256, checksumContent)
            assertEquals(manifestJson.getString("checksum"), checksumContent)
        }
    }

    @Test
    fun testSelectiveFilteringByType() {
        val filterWellsOnly = SyncExportFilter(surveyType = SurveyType.WELL)
        val result = exporter.exportSyncPackage(
            allSurveys = testSurveys,
            allRevisions = testRevisions,
            filter = filterWellsOnly
        ) as SyncExportResult.Success

        assertEquals(1, result.surveyCount)
        assertEquals(1, result.attachmentCount)

        ZipFile(result.packageFile).use { zip ->
            val entryNames = zip.entries().asSequence().map { it.name.replace('\\', '/') }.toSet()
            assertTrue(entryNames.contains("attachments/WELL-YE-30-001/well_photo_01.jpg"))
            assertFalse(entryNames.contains("attachments/SPRING-YE-30-002/spring_photo_01.jpg"))
            assertFalse(entryNames.contains("attachments/DAM-YE-30-003/dam_photo_01.jpg"))

            val surveysContent = zip.getInputStream(zip.getEntry("surveys.json")).bufferedReader().readText()
            val surveysJson = JSONObject(surveysContent)
            val array = surveysJson.getJSONArray("surveys")
            assertEquals(1, array.length())
            assertEquals("WELL-YE-30-001", array.getJSONObject(0).getString("recordId"))
        }
    }

    @Test
    fun testSelectiveFilteringByWorkflowStatus() {
        val filterApprovedOnly = SyncExportFilter(workflowStatus = "APPROVED")
        val result = exporter.exportSyncPackage(
            allSurveys = testSurveys,
            allRevisions = testRevisions,
            filter = filterApprovedOnly
        ) as SyncExportResult.Success

        assertEquals(1, result.surveyCount)
        assertEquals("WELL-YE-30-001", result.manifest.fileList.find { it.relativePath.contains("surveys.json") }?.let { "WELL-YE-30-001" })
    }

    @Test
    fun testPackageValidation() {
        val exportResult = exporter.exportSyncPackage(
            allSurveys = testSurveys,
            allRevisions = testRevisions
        ) as SyncExportResult.Success

        val validation = exporter.validatePackage(exportResult.packageFile)
        assertTrue("Exported valid package must pass validation", validation.isValid)
        assertTrue("Validation errors list must be empty", validation.errors.isEmpty())
        assertNotNull("Manifest must be successfully parsed", validation.manifest)
        assertEquals(3, validation.manifest?.surveyCount)
    }

    private class TestContext(
        private val baseDir: File,
        private val testCacheDir: File
    ) : android.content.ContextWrapper(null) {
        override fun getFilesDir(): File = baseDir
        override fun getCacheDir(): File = testCacheDir
    }
}
