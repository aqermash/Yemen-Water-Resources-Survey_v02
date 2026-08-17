package com.yemen.watersurvey.core.pdf

import com.yemen.watersurvey.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Unit Tests for Native Android Official PDF Stamping Engine (Phase 7).
 */
class PdfStampingEngineTest {

    private fun createSampleWellRecord(): SurveyRecord {
        return SurveyRecord(
            recordId = "rec-301",
            surveyType = SurveyType.WELL,
            governorateCode = "YEM-30",
            districtCode = "YEM-30-02",
            uzlahCode = "YEM-30-02-01",
            villageCode = "YEM-30-02-01-001",
            enumeratorId = "usr-001",
            enumeratorUsername = "ahmed_enum",
            workflowStatus = "APPROVED",
            revisionCount = 2,
            createdAt = "2026-08-13 09:30:00",
            updatedAt = "2026-08-13 10:15:00",
            gpsPoint = GpsLocationResult(
                latitude = 16.9421,
                longitude = 43.7612,
                altitudeM = 1820.0,
                accuracyM = 4.2f,
                quality = GpsAccuracyQuality.EXCELLENT,
                capturedAt = "2026-08-13 09:31:00"
            ),
            wellDetails = WellDetails(
                wellNameAr = "بئر المقاش الارتوازي",
                wellType = "ارتوازي حفر عميق",
                wellDepthM = 240.0,
                pumpingMechanism = "مضخة غاطسة بالكهرباء",
                operationalStatus = "شغال بنشاط"
            ),
            attachments = listOf(
                AttachmentInfo(
                    attachmentId = "att-101",
                    surveyId = "rec-301",
                    attachmentType = "PHOTO",
                    filePath = "attachments/IMG_20260813_093100_a1b2c3.jpg",
                    fileName = "IMG_20260813_093100_a1b2c3.jpg",
                    fileSize = 285000L,
                    timestamp = "2026-08-13 09:31:05"
                )
            )
        )
    }

    private fun createSampleSpringRecord(): SurveyRecord {
        return SurveyRecord(
            recordId = "rec-302",
            surveyType = SurveyType.SPRING,
            governorateCode = "YEM-30",
            districtCode = "YEM-30-01",
            uzlahCode = "YEM-30-01-01",
            villageCode = "YEM-30-01-01-001",
            enumeratorId = "usr-001",
            enumeratorUsername = "ahmed_enum",
            workflowStatus = "COMPLETED",
            revisionCount = 1,
            createdAt = "2026-08-13 11:00:00",
            updatedAt = "2026-08-13 11:00:00",
            gpsPoint = GpsLocationResult(
                latitude = 16.8123,
                longitude = 43.2411,
                altitudeM = 2100.0,
                accuracyM = 8.5f,
                quality = GpsAccuracyQuality.GOOD,
                capturedAt = "2026-08-13 11:01:00"
            ),
            springDetails = SpringDetails(
                springNameAr = "عين النظير الجارية",
                flowRateLps = 12.5,
                waterClarity = "عذبة ونقية جداً",
                dischargeSeasonality = "دائم التدفق طوال العام"
            )
        )
    }

    private fun createSampleDamRecord(): SurveyRecord {
        return SurveyRecord(
            recordId = "rec-303",
            surveyType = SurveyType.DAM,
            governorateCode = "YEM-13",
            districtCode = "YEM-13-05",
            uzlahCode = "YEM-13-05-01",
            villageCode = "YEM-13-05-01-001",
            enumeratorId = "usr-002",
            enumeratorUsername = "super_district",
            workflowStatus = "UNDER_REVIEW",
            revisionCount = 3,
            createdAt = "2026-08-13 12:00:00",
            updatedAt = "2026-08-13 12:30:00",
            gpsPoint = GpsLocationResult(
                latitude = 15.3421,
                longitude = 44.1822,
                altitudeM = 2300.0,
                accuracyM = 12.0f,
                quality = GpsAccuracyQuality.ACCEPTABLE_WITH_WARNING,
                capturedAt = "2026-08-13 12:01:00"
            ),
            damDetails = DamDetails(
                damNameAr = "سد متنة التحويلي",
                structureType = "سد تحويلي خرساني",
                storageCapacityM3 = 450000.0,
                damHeightM = 18.5,
                structuralCondition = "جيدة جداً"
            )
        )
    }

    @Test
    fun testPdfA4DimensionsAndCoordinateBounds() {
        val dummyDir = File(System.getProperty("java.io.tmpdir"), "pdf_test_${System.currentTimeMillis()}")
        dummyDir.mkdirs()

        val engine = PdfStampingEngine(TestContext(dummyDir))

        assertEquals(595, PdfStampingEngine.PAGE_WIDTH_PT)
        assertEquals(842, PdfStampingEngine.PAGE_HEIGHT_PT)

        // Verify coordinate bounds for all survey types
        val wellMapping = engine.loadMappingForType(SurveyType.WELL)
        assertNotNull("Well mapping should load", wellMapping)
        assertTrue("Well mapping fields must not be empty", wellMapping.fields.isNotEmpty())

        for (field in wellMapping.fields) {
            assertTrue("Field X coordinate ${field.x} must be within A4 page width (0..595)", field.x in 0f..595f)
            assertTrue("Field Y coordinate ${field.y} must be within A4 page height (0..842)", field.y in 0f..842f)
            assertTrue("Field label '${field.labelAr}' must not be blank", field.labelAr.isNotBlank())
        }

        val springMapping = engine.loadMappingForType(SurveyType.SPRING)
        assertNotNull("Spring mapping should load", springMapping)
        for (field in springMapping.fields) {
            assertTrue("Spring field X coordinate within bounds", field.x in 0f..595f)
            assertTrue("Spring field Y coordinate within bounds", field.y in 0f..842f)
        }

        val damMapping = engine.loadMappingForType(SurveyType.DAM)
        assertNotNull("Dam mapping should load", damMapping)
        for (field in damMapping.fields) {
            assertTrue("Dam field X coordinate within bounds", field.x in 0f..595f)
            assertTrue("Dam field Y coordinate within bounds", field.y in 0f..842f)
        }
    }

    @Test
    fun testOfficialPdfStampingExecution() {
        val dummyDir = File(System.getProperty("java.io.tmpdir"), "pdf_test_${System.currentTimeMillis()}")
        dummyDir.mkdirs()

        val engine = PdfStampingEngine(TestContext(dummyDir))
        val wellRecord = createSampleWellRecord()

        val result = engine.stampSurveyToPdf(wellRecord)

        assertNotNull(result)
        assertTrue("Output file must exist", result.file.exists())
        assertTrue("File name must contain recordId", result.fileName.contains("rec-301"))
        assertTrue("File name must end with .pdf", result.fileName.endsWith(".pdf"))
        assertTrue("File path must point to exports/pdfs", result.relativePath.startsWith("exports/pdfs/"))
        assertEquals("rec-301", result.recordId)
        assertEquals("WELL", result.surveyType)
        assertEquals(1, result.pageCount)
        assertTrue("Generation is strictly offline", result.isOfflineGenerated)
    }

    @Test
    fun testPackageBasedPdfTemplateSupport() {
        val dummyDir = File(System.getProperty("java.io.tmpdir"), "pdf_test_pkg_${System.currentTimeMillis()}")
        dummyDir.mkdirs()

        val packageDir = File(dummyDir, "packages/well_v2")
        packageDir.mkdirs()

        val customMappingFile = File(packageDir, "pdf_mapping.json")
        customMappingFile.writeText(
            """
            {
              "surveyType": "WELL",
              "templateTitleAr": "استمارة الباقة الديناميكية لحصر الآبار - الإصدار الثاني",
              "pageWidthPt": 595.28,
              "pageHeightPt": 841.89,
              "totalPages": 1,
              "fields": [
                { "key": "recordId", "labelAr": "رقم السجل", "x": 500.0, "y": 100.0, "fontSize": 10.0, "isBold": true, "align": "RIGHT" },
                { "key": "wellNameAr", "labelAr": "اسم البئر", "x": 500.0, "y": 200.0, "fontSize": 12.0, "isBold": true, "align": "RIGHT" }
              ]
            }
            """.trimIndent()
        )

        val engine = PdfStampingEngine(TestContext(dummyDir))
        val pkg = engine.loadPackageFromDirectory(packageDir, "form-well-v2", "2.0")

        assertNotNull(pkg)
        assertEquals("form-well-v2", pkg.formId)
        assertEquals("2.0", pkg.formVersion)
        assertNotNull(pkg.mapping)
        assertEquals("استمارة الباقة الديناميكية لحصر الآبار - الإصدار الثاني", pkg.mapping?.templateTitleAr)

        val wellRecord = createSampleWellRecord()
        val result = engine.stampSurveyToPdf(wellRecord, templatePackage = pkg)
        assertTrue(result.file.exists())
        assertEquals("rec-301", result.recordId)
    }

    @Test
    fun testAttachmentSeparationPolicy() {
        val wellRecord = createSampleWellRecord()
        // Confirm photos and attachments are separate referenced file paths linked by Survey ID, not inline binary dumps
        assertEquals(1, wellRecord.attachments.size)
        assertEquals("rec-301", wellRecord.attachments[0].surveyId)
        assertEquals("attachments/IMG_20260813_093100_a1b2c3.jpg", wellRecord.attachments[0].filePath)
    }

    @Test
    fun testSpringAndDamPdfStamping() {
        val dummyDir = File(System.getProperty("java.io.tmpdir"), "pdf_test_${System.currentTimeMillis()}")
        dummyDir.mkdirs()

        val engine = PdfStampingEngine(TestContext(dummyDir))

        val springRecord = createSampleSpringRecord()
        val springResult = engine.stampSurveyToPdf(springRecord)
        assertTrue("Spring PDF file must exist", springResult.file.exists())
        assertEquals("SPRING", springResult.surveyType)

        val damRecord = createSampleDamRecord()
        val damResult = engine.stampSurveyToPdf(damRecord)
        assertTrue("Dam PDF file must exist", damResult.file.exists())
        assertEquals("DAM", damResult.surveyType)
    }

    private class TestContext(private val baseDir: File) : android.content.ContextWrapper(null) {
        override fun getFilesDir(): File = baseDir
    }
}
