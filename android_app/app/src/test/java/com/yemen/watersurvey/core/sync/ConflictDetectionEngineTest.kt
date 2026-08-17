package com.yemen.watersurvey.core.sync

import com.yemen.watersurvey.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Unit Tests for Phase 9.2 Conflict Detection Engine.
 *
 * Verifies:
 * 1. Global identity reconciliation strictly via `surveyUUID`.
 * 2. Accurate classification into NEW_RECORD, UPDATE_AVAILABLE, DUPLICATE, and CONFLICT.
 * 3. Atomic field-level difference generation.
 * 4. Summary metric calculation.
 * 5. Deterministic, offline behavior without side-effects.
 */
class ConflictDetectionEngineTest {

    private lateinit var engine: ConflictDetectionEngine

    @Before
    fun setup() {
        engine = ConflictDetectionEngine()
    }

    @Test
    fun `test new record classification when surveyUUID does not exist locally`() {
        val globalUuid = UUID.randomUUID().toString()
        val incoming = SurveyRecord(
            recordId = "WELL-001",
            surveyUUID = globalUuid,
            surveyType = SurveyType.WELL,
            governorateCode = "صعدة",
            districtCode = "سحار",
            uzlahCode = "الطلح",
            villageCode = "المقاش",
            workflowStatus = "COMPLETED",
            revisionCount = 1,
            createdAt = "2026-08-14 10:00:00",
            updatedAt = "2026-08-14 10:00:00"
        )

        val existingSurveys = emptyList<SurveyRecord>()

        val (items, summary) = engine.analyzeConflicts(
            incomingSurveys = listOf(incoming),
            existingSurveys = existingSurveys
        )

        assertEquals(1, items.size)
        val item = items.first()
        assertEquals(globalUuid, item.surveyUUID)
        assertEquals(RecordConflictType.NEW_RECORD, item.conflictType)
        assertEquals(SupervisorMergeDecision.ACCEPT_INCOMING, item.recommendedAction)
        assertEquals(1, summary.newRecordsCount)
        assertEquals(0, summary.conflictsCount)
        assertEquals(0, summary.updatesCount)
        assertEquals(0, summary.duplicatesCount)
    }

    @Test
    fun `test duplicate record classification when surveyUUID matches and content is identical`() {
        val globalUuid = "UUID-GLOBAL-WELL-100"
        val existing = SurveyRecord(
            recordId = "WELL-LOC-01",
            surveyUUID = globalUuid,
            surveyType = SurveyType.WELL,
            governorateCode = "صنعاء",
            districtCode = "بني حشيش",
            uzlahCode = "رجام",
            villageCode = "بيت السيد",
            workflowStatus = "APPROVED",
            revisionCount = 1,
            createdAt = "2026-08-10 10:00:00",
            updatedAt = "2026-08-10 10:00:00",
            wellDetails = WellDetails(
                wellNameAr = "بئر الغدير",
                wellType = "ARTESIAN",
                wellDepthM = 120.0,
                pumpingMechanism = "SOLAR",
                operationalStatus = "FUNCTIONAL"
            )
        )

        // Incoming has same surveyUUID, same revisionCount, same data (different local recordId)
        val incoming = existing.copy(
            recordId = "WELL-INCOMING-ENUM-88"
        )

        val (items, summary) = engine.analyzeConflicts(
            incomingSurveys = listOf(incoming),
            existingSurveys = listOf(existing)
        )

        assertEquals(1, items.size)
        val item = items.first()
        assertEquals(RecordConflictType.DUPLICATE, item.conflictType)
        assertEquals(SupervisorMergeDecision.KEEP_EXISTING, item.recommendedAction)
        assertEquals(1, summary.duplicatesCount)
        assertEquals(0, summary.conflictsCount)
    }

    @Test
    fun `test update available classification when incoming has higher revision count`() {
        val globalUuid = "UUID-SPRING-200"
        val existing = SurveyRecord(
            recordId = "SPRING-01",
            surveyUUID = globalUuid,
            surveyType = SurveyType.SPRING,
            governorateCode = "إب",
            districtCode = "جبلة",
            uzlahCode = "الربادي",
            villageCode = "القرية",
            workflowStatus = "DRAFT",
            revisionCount = 1,
            createdAt = "2026-08-01 10:00:00",
            updatedAt = "2026-08-01 10:00:00",
            springDetails = SpringDetails(
                springNameAr = "عين علي القديم",
                flowRateLps = 2.5,
                waterClarity = "CLEAR",
                dischargeSeasonality = "PERENNIAL"
            )
        )

        val incoming = existing.copy(
            workflowStatus = "COMPLETED",
            revisionCount = 2,
            updatedAt = "2026-08-14 12:00:00",
            springDetails = SpringDetails(
                springNameAr = "عين علي المجدد",
                flowRateLps = 4.0,
                waterClarity = "CLEAR",
                dischargeSeasonality = "PERENNIAL"
            )
        )

        val (items, summary) = engine.analyzeConflicts(
            incomingSurveys = listOf(incoming),
            existingSurveys = listOf(existing)
        )

        assertEquals(1, items.size)
        val item = items.first()
        assertEquals(RecordConflictType.UPDATE_AVAILABLE, item.conflictType)
        assertEquals(SupervisorMergeDecision.ACCEPT_INCOMING, item.recommendedAction)
        assertEquals(1, summary.updatesCount)
        assertTrue(item.differences.isNotEmpty())

        val fieldNames = item.differences.map { it.fieldName }
        assertTrue(fieldNames.contains("workflowStatus"))
        assertTrue(fieldNames.contains("revisionCount"))
        assertTrue(fieldNames.contains("springNameAr"))
        assertTrue(fieldNames.contains("flowRateLps"))
    }

    @Test
    fun `test conflict classification when values differ without higher revision`() {
        val globalUuid = "UUID-DAM-300"
        val existing = SurveyRecord(
            recordId = "DAM-01",
            surveyUUID = globalUuid,
            surveyType = SurveyType.DAM,
            governorateCode = "ذمار",
            districtCode = "عنس",
            uzlahCode = "الجمعة",
            villageCode = "بيت مجاهد",
            workflowStatus = "APPROVED",
            revisionCount = 2,
            createdAt = "2026-08-05 10:00:00",
            updatedAt = "2026-08-12 10:00:00",
            damDetails = DamDetails(
                damNameAr = "سد وادي السد",
                structureType = "CONCRETE",
                storageCapacityM3 = 50000.0,
                damHeightM = 15.0,
                structuralCondition = "GOOD"
            )
        )

        // Incoming has conflicting values (different height & capacity) but with older/same timestamp and same revisionCount
        val incoming = existing.copy(
            updatedAt = "2026-08-10 09:00:00",
            damDetails = DamDetails(
                damNameAr = "سد وادي السد المحول",
                structureType = "EARTHEN",
                storageCapacityM3 = 35000.0,
                damHeightM = 12.0,
                structuralCondition = "DAMAGED"
            )
        )

        val (items, summary) = engine.analyzeConflicts(
            incomingSurveys = listOf(incoming),
            existingSurveys = listOf(existing)
        )

        assertEquals(1, items.size)
        val item = items.first()
        assertEquals(RecordConflictType.CONFLICT, item.conflictType)
        assertEquals(SupervisorMergeDecision.PENDING_REVIEW, item.recommendedAction)
        assertEquals(1, summary.conflictsCount)
        assertTrue(item.differences.size >= 4)
    }

    @Test
    fun `test field difference detection for GPS coordinates and administrative boundaries`() {
        val globalUuid = "UUID-GPS-DIFF-01"
        val existing = SurveyRecord(
            recordId = "R1",
            surveyUUID = globalUuid,
            surveyType = SurveyType.WELL,
            governorateCode = "صنعاء",
            districtCode = "سنحان",
            uzlahCode = "سيان",
            villageCode = "حمل",
            gpsPoint = GpsLocationResult(
                latitude = 15.35000,
                longitude = 44.20000,
                accuracyM = 4.5f,
                quality = GpsAccuracyQuality.EXCELLENT
            )
        )

        val incoming = existing.copy(
            districtCode = "بني مطر",
            gpsPoint = GpsLocationResult(
                latitude = 15.36120,
                longitude = 44.21500,
                accuracyM = 8.0f,
                quality = GpsAccuracyQuality.GOOD
            )
        )

        val diffs = engine.generateFieldDifferences(existing, incoming)
        val fieldNames = diffs.map { it.fieldName }

        assertTrue(fieldNames.contains("districtCode"))
        assertTrue(fieldNames.contains("gpsCoordinates"))
        assertTrue(fieldNames.contains("gpsAccuracy"))
    }
}
