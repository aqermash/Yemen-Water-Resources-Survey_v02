package com.yemen.watersurvey.core.excel

import com.yemen.watersurvey.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * Unit Tests for Genuine Multi-Sheet OOXML (.xlsx) Excel Exporter.
 */
class ExcelExporterTest {

    private fun createSampleRecords(): List<SurveyRecord> {
        return listOf(
            SurveyRecord(
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
            ),
            SurveyRecord(
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
            ),
            SurveyRecord(
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
        )
    }

    @Test
    fun testZipOoxmlStructure() {
        val dummyDir = File(System.getProperty("java.io.tmpdir"), "excel_test_${System.currentTimeMillis()}")
        dummyDir.mkdirs()

        val dummyFile = File(dummyDir, "Test_Export.xlsx")

        val exporter = ExcelExporter(TestContext(dummyDir))
        val records = createSampleRecords()

        val result = exporter.exportSurveysToExcel(records, "ahmed_enum")

        assertTrue("Export file must exist", result.file.exists())
        assertTrue("Export file name must end with .xlsx", result.fileName.endsWith(".xlsx"))

        // Verify valid ZIP package
        val zipFile = ZipFile(result.file)

        val requiredEntries = listOf(
            "[Content_Types].xml",
            "_rels/.rels",
            "xl/workbook.xml",
            "xl/_rels/workbook.xml.rels",
            "xl/styles.xml",
            "xl/worksheets/sheet1.xml",
            "xl/worksheets/sheet2.xml",
            "xl/worksheets/sheet3.xml",
            "xl/worksheets/sheet4.xml"
        )

        for (entryName in requiredEntries) {
            val entry = zipFile.getEntry(entryName)
            assertNotNull("ZIP entry '$entryName' must exist in OOXML .xlsx container", entry)
        }

        // Verify 4 worksheets listed in xl/workbook.xml
        val wbEntry = zipFile.getEntry("xl/workbook.xml")
        val wbContent = zipFile.getInputStream(wbEntry).bufferedReader().use { it.readText() }

        assertTrue(wbContent.contains("""name="آبار المياه (Wells)""""))
        assertTrue(wbContent.contains("""name="العيون والينابيع (Springs)""""))
        assertTrue(wbContent.contains("""name="السدود والحواجز (Dams)""""))
        assertTrue(wbContent.contains("""name="سجل العمليات (Survey Log)""""))

        // Verify Survey Log sheet4.xml contains all fields
        val sheet4Entry = zipFile.getEntry("xl/worksheets/sheet4.xml")
        val sheet4Content = zipFile.getInputStream(sheet4Entry).bufferedReader().use { it.readText() }

        // Check Enumerator Username & ID
        assertTrue(sheet4Content.contains("ahmed_enum (usr-001)"))
        // Check GPS Accuracy
        assertTrue(sheet4Content.contains("4.2"))
        // Check GPS Quality
        assertTrue(sheet4Content.contains("ممتازة (أقل من 5م)"))
        // Check Attachments
        assertTrue(sheet4Content.contains("IMG_20260813_093100_a1b2c3.jpg"))
        // Check Workflow Status
        assertTrue(sheet4Content.contains("APPROVED"))
        assertTrue(sheet4Content.contains("COMPLETED"))
        assertTrue(sheet4Content.contains("UNDER_REVIEW"))

        zipFile.close()
    }

    private class TestContext(private val baseDir: File) : android.content.ContextWrapper(null) {
        override fun getFilesDir(): File = baseDir
    }
}
