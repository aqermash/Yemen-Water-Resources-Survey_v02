package com.yemen.watersurvey.core.sync

import com.yemen.watersurvey.data.database.SurveyAppDatabase
import com.yemen.watersurvey.data.entity.SurveyRecordEntity
import com.yemen.watersurvey.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Unit & Integration Tests for Phase 9.3: Offline Supervisor Synchronization Workspace.
 *
 * Verifies:
 * 1. Multi-package registration & workspace inbox management.
 * 2. Duplicate package prevention by Package ID and SHA-256.
 * 3. Complete lifecycle state transitions (RECEIVED -> VALIDATED -> REVIEW_PENDING -> MERGED / REJECTED / ARCHIVED).
 * 4. Immutable historical transition logging (SyncPackageHistoryEntity).
 * 5. Full audit logging for every state mutation (AuditLogEntity).
 * 6. Multi-package isolation across independent enumerator devices.
 * 7. Offline computation of workspace dashboard metrics.
 * 8. District database sync summary aggregation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SupervisorSyncWorkspaceTest {

    private lateinit var database: SurveyAppDatabase
    private lateinit var workspaceManager: SupervisorSyncWorkspaceManager
    private lateinit var exporter: SurveySyncExporter
    private lateinit var testContext: android.content.Context
    private lateinit var tempDir: File

    @Before
    fun setup() {
        testContext = RuntimeEnvironment.getApplication()
        database = SurveyAppDatabase.createInMemory(testContext)
        workspaceManager = SupervisorSyncWorkspaceManager(testContext, database)
        exporter = SurveySyncExporter(testContext)
        tempDir = File(testContext.cacheDir, "workspace_tests_${System.currentTimeMillis()}").apply { mkdirs() }
    }

    private fun createSampleSurveyRecord(
        recordId: String,
        uuid: String = UUID.randomUUID().toString(),
        type: SurveyType = SurveyType.WELL,
        gov: String = "صعدة",
        dist: String = "سحار",
        enumerator: String = "ahmed_enum"
    ): SurveyRecord {
        return SurveyRecord(
            recordId = recordId,
            surveyUUID = uuid,
            surveyType = type,
            governorateCode = gov,
            districtCode = dist,
            uzlahCode = "الطلح",
            villageCode = "المقاش",
            enumeratorId = "enum-01",
            enumeratorUsername = enumerator,
            workflowStatus = "COMPLETED",
            revisionCount = 1,
            createdAt = "2026-08-14 10:00:00",
            updatedAt = "2026-08-14 10:00:00",
            wellDetails = if (type == SurveyType.WELL) WellDetails(
                wellNameAr = "بئر الهدى",
                wellType = "ARTESIAN",
                wellDepthM = 120.0,
                pumpingMechanism = "SOLAR",
                operationalStatus = "POTABLE"
            ) else null
        )
    }

    private fun generateTestSyncPackage(
        packageId: String,
        surveys: List<SurveyRecord>,
        username: String = "enum_user_1",
        deviceId: String = "DEV-NORTH-01"
    ): File {
        val result = exporter.exportSyncPackage(
            allSurveys = surveys,
            allRevisions = emptyList(),
            sourceDeviceId = deviceId,
            senderRole = "FIELD_ENUMERATOR",
            senderUsername = username,
            district = "سحار",
            governorate = "صعدة",
            packageId = packageId
        )
        check(result is SyncExportResult.Success) {
            "generateTestSyncPackage failed: ${(result as? SyncExportResult.Failure)?.errorMessage}"
        }
        return result.packageFile
    }

    @Test
    fun `test registering multiple enumerator packages independently in workspace inbox`() = runBlocking {
        val pkg1 = generateTestSyncPackage("PKG-ENUM-01", listOf(createSampleSurveyRecord("WELL-001")), username = "ahmed_enum", deviceId = "DEV-01")
        val pkg2 = generateTestSyncPackage("PKG-ENUM-02", listOf(createSampleSurveyRecord("WELL-002")), username = "salem_enum", deviceId = "DEV-02")

        val res1 = workspaceManager.registerPackageFile(pkg1)
        val res2 = workspaceManager.registerPackageFile(pkg2)

        assertTrue("First package should register successfully", res1.isSuccess)
        assertTrue("Second package should register successfully", res2.isSuccess)

        val packages = workspaceManager.getWorkspacePackages(includeArchived = true)
        assertEquals("Both packages must exist in workspace", 2, packages.size)

        val item1 = packages.find { it.packageId == "PKG-ENUM-01" }
        assertNotNull(item1)
        assertEquals("ahmed_enum", item1?.senderUsername)
        assertEquals(SyncPackageState.RECEIVED, item1?.state)

        val item2 = packages.find { it.packageId == "PKG-ENUM-02" }
        assertNotNull(item2)
        assertEquals("salem_enum", item2?.senderUsername)
        assertEquals(SyncPackageState.RECEIVED, item2?.state)
    }

    @Test
    fun `test duplicate package rejection by Package ID and SHA-256`() = runBlocking {
        val pkgFile = generateTestSyncPackage("PKG-DUP-01", listOf(createSampleSurveyRecord("WELL-DUP-1")))

        val firstRegistration = workspaceManager.registerPackageFile(pkgFile)
        assertTrue("Initial registration must succeed", firstRegistration.isSuccess)

        // Attempt second registration with same file (SHA-256 duplicate)
        val duplicateRegistration = workspaceManager.registerPackageFile(pkgFile)
        assertTrue("Duplicate SHA-256 registration must fail", duplicateRegistration.isFailure)
        assertTrue(
            duplicateRegistration.exceptionOrNull()?.message?.contains("البصمة الرقمية") == true ||
            duplicateRegistration.exceptionOrNull()?.message?.contains("مسجلة مسبقاً") == true
        )
    }

    @Test
    fun `test package lifecycle state transitions and immutable history preservation`() = runBlocking {
        val pkgFile = generateTestSyncPackage("PKG-LIFECYCLE", listOf(createSampleSurveyRecord("WELL-LIFE-1")))
        val regRes = workspaceManager.registerPackageFile(pkgFile)
        assertTrue(regRes.isSuccess)

        // 1. Initial State: RECEIVED
        var pkg = database.syncPackageDao().getPackageById("PKG-LIFECYCLE")
        assertNotNull(pkg)
        assertEquals(SyncPackageState.RECEIVED.code, pkg?.state)

        // 2. Validate Integrity: RECEIVED -> VALIDATED
        val valRes = workspaceManager.validatePackageIntegrity("PKG-LIFECYCLE")
        assertTrue("Integrity validation must succeed", valRes.isSuccess)
        pkg = database.syncPackageDao().getPackageById("PKG-LIFECYCLE")
        assertEquals(SyncPackageState.VALIDATED.code, pkg?.state)

        // 3. Analyze Conflicts: VALIDATED -> REVIEW_PENDING
        val confRes = workspaceManager.analyzeConflictsForPackage("PKG-LIFECYCLE")
        assertTrue("Conflict analysis must succeed", confRes.isSuccess)
        pkg = database.syncPackageDao().getPackageById("PKG-LIFECYCLE")
        assertEquals(SyncPackageState.REVIEW_PENDING.code, pkg?.state)
        assertEquals(1, pkg?.newRecordsCount)

        // 4. Mark Merged: REVIEW_PENDING -> MERGED
        workspaceManager.markPackageMerged("PKG-LIFECYCLE", isFullyMerged = true, mergedCount = 1)
        pkg = database.syncPackageDao().getPackageById("PKG-LIFECYCLE")
        assertEquals(SyncPackageState.MERGED.code, pkg?.state)
        assertEquals(1, pkg?.mergedRecordsCount)

        // 5. Archive: MERGED -> ARCHIVED
        workspaceManager.setPackageArchived("PKG-LIFECYCLE", isArchived = true)
        pkg = database.syncPackageDao().getPackageById("PKG-LIFECYCLE")
        assertEquals(SyncPackageState.ARCHIVED.code, pkg?.state)
        assertTrue(pkg?.isArchived == true)

        // Verify Historical Logs
        val history = workspaceManager.getPackageHistory("PKG-LIFECYCLE")
        assertTrue("Must have recorded transition history", history.size >= 5)

        // Verify Audit Logs
        val auditLogs = database.auditLogDao().getAllAuditLogs().first()
        assertTrue("Must have recorded audit logs for package events", auditLogs.isNotEmpty())
    }

    @Test
    fun `test supervisor package rejection preserves reason and updates audit trail`() = runBlocking {
        val pkgFile = generateTestSyncPackage("PKG-REJECT-01", listOf(createSampleSurveyRecord("WELL-REJ-1")))
        workspaceManager.registerPackageFile(pkgFile)

        workspaceManager.rejectPackage(
            packageId = "PKG-REJECT-01",
            supervisorActorId = "super_ali",
            supervisorRole = "DISTRICT_SUPERVISOR",
            reason = "بيانات الموقع الجغرافي غير مكتملة والإحداثيات تقع خارج نطاق المديرية"
        )

        val pkg = database.syncPackageDao().getPackageById("PKG-REJECT-01")
        assertNotNull(pkg)
        assertEquals(SyncPackageState.REJECTED.code, pkg?.state)
        assertEquals("بيانات الموقع الجغرافي غير مكتملة والإحداثيات تقع خارج نطاق المديرية", pkg?.decisionReason)

        val history = workspaceManager.getPackageHistory("PKG-REJECT-01")
        val rejectTransition = history.find { it.toState == SyncPackageState.REJECTED }
        assertNotNull(rejectTransition)
        assertEquals("super_ali", rejectTransition?.actorId)

        val auditLogs = database.auditLogDao().getAllAuditLogs().first()
        val rejectAudit = auditLogs.find { it.actionType == "SYNC_PACKAGE_REJECTED" }
        assertNotNull(rejectAudit)
        assertEquals("PKG-REJECT-01", rejectAudit?.recordId)
    }

    @Test
    fun `test offline dashboard statistics computation`() = runBlocking {
        // Seed local database with existing survey records
        database.surveyRecordDao().insertOrUpdateSurvey(
            SurveyRecordEntity(
                surveyUUID = "UUID-EXISTING-1",
                recordId = "WELL-EXIST-1",
                surveyType = "WELL",
                governorateCode = "صعدة",
                districtCode = "سحار",
                uzlahCode = "الطلح",
                villageCode = "المقاش",
                enumeratorId = "enum-01",
                enumeratorUsername = "ahmed",
                workflowStatus = "APPROVED",
                revisionCount = 1,
                createdAt = "2026-08-10 10:00:00",
                updatedAt = "2026-08-10 10:00:00"
            )
        )
        database.surveyRecordDao().insertOrUpdateSurvey(
            SurveyRecordEntity(
                surveyUUID = "UUID-EXISTING-2",
                recordId = "SPRING-EXIST-1",
                surveyType = "SPRING",
                governorateCode = "صعدة",
                districtCode = "سحار",
                uzlahCode = "الطلح",
                villageCode = "المقاش",
                enumeratorId = "enum-02",
                enumeratorUsername = "salem",
                workflowStatus = "COMPLETED",
                revisionCount = 1,
                createdAt = "2026-08-11 10:00:00",
                updatedAt = "2026-08-11 10:00:00"
            )
        )

        // Register incoming package
        val pkgFile = generateTestSyncPackage("PKG-STATS-1", listOf(createSampleSurveyRecord("WELL-NEW-1")), username = "hasan")
        workspaceManager.registerPackageFile(pkgFile)

        val stats = workspaceManager.computeDashboardStats()

        assertEquals("Total received packages count", 1, stats.totalReceivedPackages)
        assertEquals("Pending review count", 1, stats.pendingReviewsCount)
        assertEquals("Database total surveys", 2, stats.databaseTotalSurveys)
        assertEquals("Database wells count", 1, stats.databaseWellsCount)
        assertEquals("Database springs count", 1, stats.databaseSpringsCount)
        assertEquals("Database dams count", 0, stats.databaseDamsCount)
        assertEquals("Distinct enumerators count", 1, stats.distinctEnumeratorsCount)
    }

    @Test
    fun `test district sync summary aggregation`() = runBlocking {
        // Seed database
        database.surveyRecordDao().insertOrUpdateSurvey(
            SurveyRecordEntity(
                surveyUUID = "UUID-DIST-1",
                recordId = "WELL-DIST-1",
                surveyType = "WELL",
                governorateCode = "صعدة",
                districtCode = "سحار",
                uzlahCode = "الطلح",
                villageCode = "المقاش",
                enumeratorId = "enum-01",
                enumeratorUsername = "ahmed_local",
                workflowStatus = "APPROVED",
                revisionCount = 1,
                createdAt = "2026-08-12 10:00:00",
                updatedAt = "2026-08-12 10:00:00"
            )
        )

        val pkgFile = generateTestSyncPackage("PKG-DIST-1", listOf(createSampleSurveyRecord("WELL-DIST-2")), username = "ahmed_field", deviceId = "DEV-FIELD-9")
        workspaceManager.registerPackageFile(pkgFile)

        val districtSummary = workspaceManager.generateDistrictSyncSummary(
            governorate = "صعدة",
            district = "سحار"
        )

        assertEquals("صعدة", districtSummary.governorate)
        assertEquals("سحار", districtSummary.district)
        assertEquals(1, districtSummary.totalSurveys)
        assertEquals(1, districtSummary.approvedCount)
        assertEquals(1, districtSummary.wellsCount)
        assertEquals(1, districtSummary.packagesProcessedCount)
        assertEquals(1, districtSummary.enumeratorSources.size)
        assertEquals("ahmed_field", districtSummary.enumeratorSources.first().enumeratorUsername)
    }
}
