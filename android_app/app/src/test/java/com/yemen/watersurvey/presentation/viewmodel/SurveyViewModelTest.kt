package com.yemen.watersurvey.presentation.viewmodel

import android.app.Application
import androidx.room.Room
import org.robolectric.RuntimeEnvironment
import com.yemen.watersurvey.core.form.FormPackageManager
import com.yemen.watersurvey.data.database.SurveyAppDatabase
import com.yemen.watersurvey.data.entity.FormPackageEntity
import com.yemen.watersurvey.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SurveyViewModelTest {

    private lateinit var viewModel: SurveyViewModel
    private lateinit var database: SurveyAppDatabase
    private lateinit var testDispatcher: TestDispatcher

    @Before
    fun setup() {
        testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        val context = RuntimeEnvironment.getApplication()
        
        // Clear any lingering singleton INSTANCE before test execution
        try {
            val instanceField = SurveyAppDatabase::class.java.getDeclaredField("INSTANCE")
            instanceField.isAccessible = true
            instanceField.set(null, null)
        } catch (e: Exception) {
            // Ignore
        }

        // Use in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(context, SurveyAppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        // Inject the in-memory database into the singleton INSTANCE.
        // This ensures SurveyViewModel and all its internal dependencies (registryCodeGenerator, selector, etc.)
        // use the same mocked database.
        val instanceField = SurveyAppDatabase::class.java.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true
        instanceField.set(null, database)
        
        viewModel = SurveyViewModel(context)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
        
        try {
            val instanceField = SurveyAppDatabase::class.java.getDeclaredField("INSTANCE")
            instanceField.isAccessible = true
            instanceField.set(null, null)
        } catch (e: Exception) {
            // Ignore
        }
    }

    @Test
    fun `test well survey save flow`() = runTest {
        // 1. Setup initial state
        viewModel.updateSurveyType(SurveyType.WELL)
        viewModel.updateWellName("بئر اختبار")
        viewModel.updateWellType("ارتوازي")
        viewModel.updateWellDepth("150")
        viewModel.updatePumpingMechanism("مضخة كهربائية")
        viewModel.updateOperationalStatus("يعمل")
        
        // Mock GPS location (within gate)
        val mockGps = GpsLocationResult(
            latitude = 15.35,
            longitude = 44.20,
            altitudeM = 2000.0,
            accuracyM = 5.0f,
            quality = GpsAccuracyQuality.EXCELLENT,
            capturedAt = "2026-08-22 10:00:00"
        )
        
        viewModel.updateAdminLocation(
            "YE11", "YE1101", "YE110101", null,
            AdministrativeLocationSnapshot(
                admin1Pcode = "YE11", admin2Pcode = "YE1101", admin3Pcode = "YE110101",
                governorateNameAr = "صنعاء", districtNameAr = "صنعاء", subDistrictNameAr = "صنعاء"
            ),
            false, null, AdminResolutionStatus.CONFIRMED, null, mockGps
        )
        
        // 2. Trigger Save
        viewModel.saveSurvey()
        
        // Since RegistryCodeGenerator uses Dispatchers.IO, it escapes the TestCoroutineScheduler.
        // We use StateFlow.first() to deterministically suspend the test until the background
        // coroutine completes and updates isSaving back to false.
        viewModel.uiState.first { !it.isSaving }
        
        // 3. Verify
        val uiState = viewModel.uiState.value
        val recordsAfter = database.surveyRecordDao().getAllSurveysSync()
        
        // Debug: Print comprehensive diagnostics before assertion
        println("==============================================")
        println("DEBUG UI STATE:")
        println("  saveSuccess = ${uiState.saveSuccess}")
        println("  error = ${uiState.error}")
        println("  isSaving = ${uiState.isSaving}")
        println("  Total records in DB: ${recordsAfter.size}")
        
        recordsAfter.forEachIndexed { index, record ->
            println("  Record $index:")
            println("    UUID: ${record.surveyUUID}")
            println("    RegistryCode: ${record.registryCode}")
            println("    Type: ${record.surveyType}")
            println("    WellDetails: ${record.wellDetailsJson}")
            println("    GPS: Lat=${record.latitude}, Lon=${record.longitude}, Acc=${record.accuracyM}")
        }
        println("==============================================")
        
        assertTrue("Save should be successful", uiState.saveSuccess)
        assertNull("Error should be null", uiState.error)
        
        // 4. Verify Database Record
        val records = database.surveyRecordDao().getAllSurveysSync()
        assertEquals(1, records.size)
        val record = records[0]
        
        assertEquals("WELL", record.surveyType)
        val details = JSONObject(record.wellDetailsJson!!)
        assertEquals("بئر اختبار", details.getString("wellNameAr"))
        assertEquals(150.0, details.getDouble("wellDepthM"), 0.1)
        assertNotNull(record.registryCode)
        assertTrue(record.registryCode.contains("WL"))
    }

    @Test
    fun `test accuracy gate blocks save`() = runTest {
        viewModel.updateSurveyType(SurveyType.WELL)
        viewModel.updateWellName("بئر اختبار")
        
        // Mock POOR GPS location (outside gate >= 15m)
        val mockGps = GpsLocationResult(
            latitude = 15.35,
            longitude = 44.20,
            altitudeM = 2000.0,
            accuracyM = 20.0f,
            quality = GpsAccuracyQuality.POOR,
            capturedAt = "2026-08-22 10:00:00"
        )
        
        viewModel.updateAdminLocation(
            "YE11", "YE1101", "YE110101", null,
            AdministrativeLocationSnapshot(
                admin1Pcode = "YE11", admin2Pcode = "YE1101", admin3Pcode = "YE110101",
                governorateNameAr = "صنعاء", districtNameAr = "صنعاء", subDistrictNameAr = "صنعاء"
            ),
            false, null, AdminResolutionStatus.CONFIRMED, null, mockGps
        )
        
        viewModel.saveSurvey()
        advanceUntilIdle()
        
        val uiState = viewModel.uiState.value
        assertFalse("Save should NOT be successful", uiState.saveSuccess)
        assertNotNull("Should have accuracy error", uiState.error)
        assertTrue(uiState.error!!.contains("دقة GPS"))
    }

    @Test
    fun `test draft save without GPS and incomplete fields`() = runTest {
        viewModel.updateSurveyType(SurveyType.SPRING)
        viewModel.updateSpringName("عين تجريبية")
        // No GPS, flow rate incomplete
        val initialUUID = viewModel.uiState.value.surveyUUID
        val initialRecordId = viewModel.uiState.value.recordId

        viewModel.saveAsDraft()
        viewModel.uiState.first { !it.isSaving }

        val uiState = viewModel.uiState.value
        assertTrue("Draft save should be successful", uiState.draftSaveSuccess)
        assertNull("Error should be null", uiState.error)

        val records = database.surveyRecordDao().getAllSurveysSync()
        assertEquals(1, records.size)
        val record = records[0]
        assertEquals(initialUUID, record.surveyUUID)
        assertEquals(initialRecordId, record.recordId)
        assertEquals("DRAFT", record.workflowStatus)
        assertEquals("SPRING", record.surveyType)
    }

    @Test
    fun `test edit draft and complete without duplicates`() = runTest {
        // 1. Save initial draft
        viewModel.updateSurveyType(SurveyType.DAM)
        viewModel.updateDamName("سد تجريبي")
        viewModel.updateStructureType("ترابي")
        val originalUUID = viewModel.uiState.value.surveyUUID
        val originalRecordId = viewModel.uiState.value.recordId

        viewModel.saveAsDraft()
        viewModel.uiState.first { !it.isSaving }
        assertEquals(1, database.surveyRecordDao().getAllSurveysSync().size)

        // 2. Load draft for edit
        val context = RuntimeEnvironment.getApplication()
        val editViewModel = SurveyViewModel(context)
        editViewModel.loadRecordForEdit(originalUUID)
        editViewModel.uiState.first { it.surveyUUID == originalUUID }

        assertEquals(originalUUID, editViewModel.uiState.value.surveyUUID)
        assertEquals(originalRecordId, editViewModel.uiState.value.recordId)
        assertEquals("سد تجريبي", editViewModel.uiState.value.damNameAr)

        // 3. Add GPS and complete survey
        val mockGps = GpsLocationResult(
            latitude = 15.35, longitude = 44.20, altitudeM = 2000.0,
            accuracyM = 4.5f, quality = GpsAccuracyQuality.EXCELLENT,
            capturedAt = "2026-08-22 10:00:00"
        )
        editViewModel.updateAdminLocation(
            "YE11", "YE1101", "YE110101", null,
            AdministrativeLocationSnapshot(
                admin1Pcode = "YE11", admin2Pcode = "YE1101", admin3Pcode = "YE110101",
                governorateNameAr = "صنعاء", districtNameAr = "صنعاء", subDistrictNameAr = "صنعاء"
            ),
            false, null, AdminResolutionStatus.CONFIRMED, null, mockGps
        )

        editViewModel.saveSurvey()
        editViewModel.uiState.first { !it.isSaving }

        assertTrue("Save completed should succeed", editViewModel.uiState.value.saveSuccess)

        // Verify NO DUPLICATE record created
        val allSurveys = database.surveyRecordDao().getAllSurveysSync()
        assertEquals("Should NOT create duplicate record in DB", 1, allSurveys.size)
        val updated = allSurveys[0]
        assertEquals(originalUUID, updated.surveyUUID)
        assertEquals(originalRecordId, updated.recordId)
        assertEquals("COMPLETED", updated.workflowStatus)
    }

    @Test
    fun `test package record lifecycle`() = runTest {
        // 1. Create and save completed well survey
        viewModel.updateSurveyType(SurveyType.WELL)
        viewModel.updateWellName("بئر الحزمة")
        viewModel.updateWellType("يدوي")
        viewModel.updateWellDepth("80")
        viewModel.updatePumpingMechanism("يدوي")
        viewModel.updateOperationalStatus("يعمل")

        val mockGps = GpsLocationResult(
            latitude = 15.35, longitude = 44.20, altitudeM = 2000.0,
            accuracyM = 5.0f, quality = GpsAccuracyQuality.GOOD,
            capturedAt = "2026-08-22 10:00:00"
        )
        viewModel.updateAdminLocation(
            "YE11", "YE1101", "YE110101", null,
            AdministrativeLocationSnapshot(
                admin1Pcode = "YE11", admin2Pcode = "YE1101", admin3Pcode = "YE110101",
                governorateNameAr = "صنعاء", districtNameAr = "صنعاء", subDistrictNameAr = "صنعاء"
            ),
            false, null, AdminResolutionStatus.CONFIRMED, null, mockGps
        )

        viewModel.saveSurvey()
        viewModel.uiState.first { !it.isSaving }

        val uuid = viewModel.uiState.value.surveyUUID

        // 2. Package the record
        val deferred = kotlinx.coroutines.CompletableDeferred<Pair<Boolean, String>>()
        viewModel.packageRecord(uuid) { success, pkgId ->
            deferred.complete(Pair(success, pkgId))
        }
        val (packageSuccess, generatedPkgId) = deferred.await()

        assertTrue("Package operation should succeed", packageSuccess)
        assertTrue("Package ID should start with PKG-", generatedPkgId.startsWith("PKG-"))

        // 3. Verify workflowStatus in database is PACKAGED
        val updatedRecord = database.surveyRecordDao().getSurveyByUUID(uuid)
        assertNotNull(updatedRecord)
        assertEquals("PACKAGED", updatedRecord!!.workflowStatus)

        // 4. Verify package metadata on disk via FieldPackageManager
        val pkg = viewModel.packageManager.loadPackage(generatedPkgId)
        assertNotNull(pkg)
        assertEquals(1, pkg!!.surveyCount)
        assertEquals(1, pkg.wellCount)
        assertTrue(uuid in pkg.surveyUUIDs)
    }

    // ---- FIX 1: Attachment UUID lifecycle ----

    @Test
    fun `test new survey has stable UUID before attachment operations`() = runTest {
        // New survey: UUID is set immediately in SurveyFormState default, no load needed
        val uuid = viewModel.uiState.value.surveyUUID
        assertFalse("New survey UUID must not be blank", uuid.isBlank())
        assertFalse("New survey must not be in loading state", viewModel.uiState.value.isLoadingRecord)
    }

    @Test
    fun `test edit survey sets isLoadingRecord true then false with real UUID`() = runTest {
        // 1. Create a draft to edit
        viewModel.updateSurveyType(SurveyType.WELL)
        viewModel.updateWellName("بئر تحميل")
        val originalUUID = viewModel.uiState.value.surveyUUID
        viewModel.saveAsDraft()
        viewModel.uiState.first { !it.isSaving }

        // 2. Create a new viewModel for editing (simulates fresh screen)
        val context = RuntimeEnvironment.getApplication()
        val editViewModel = SurveyViewModel(context)

        // The initial state must NOT be the loading state yet (load not triggered)
        assertFalse("Before loadRecordForEdit, isLoadingRecord must be false",
            editViewModel.uiState.value.isLoadingRecord)

        // 3. Trigger edit load and await completion
        editViewModel.loadRecordForEdit(originalUUID)
        editViewModel.uiState.first { it.surveyUUID == originalUUID }

        // 4. After load: must have real UUID and loading flag cleared
        val finalState = editViewModel.uiState.value
        assertEquals("Real UUID must be restored after load", originalUUID, finalState.surveyUUID)
        assertFalse("isLoadingRecord must be false after record is loaded", finalState.isLoadingRecord)
    }

    @Test
    fun `test attachment load uses real UUID after edit restore`() = runTest {
        // 1. Save a draft
        viewModel.updateSurveyType(SurveyType.SPRING)
        viewModel.updateSpringName("عين UUID Test")
        val originalUUID = viewModel.uiState.value.surveyUUID
        viewModel.saveAsDraft()
        viewModel.uiState.first { !it.isSaving }

        // 2. Restore in a new view model
        val context = RuntimeEnvironment.getApplication()
        val editViewModel = SurveyViewModel(context)
        editViewModel.loadRecordForEdit(originalUUID)
        // Wait until real UUID is loaded
        editViewModel.uiState.first { it.surveyUUID == originalUUID }

        // 3. The UUID that attachment operations would use matches the original
        assertEquals("Attachment ops must use real UUID", originalUUID, editViewModel.uiState.value.surveyUUID)
        assertFalse("Camera must be unblocked after load", editViewModel.uiState.value.isLoadingRecord)
    }

    // ---- FIX 2: revisionCount ----

    @Test
    fun `test repeated draft saves do not inflate revisionCount`() = runTest {
        viewModel.updateSurveyType(SurveyType.WELL)
        viewModel.updateWellName("بئر مسودة")

        // First draft save
        viewModel.saveAsDraft()
        viewModel.uiState.first { !it.isSaving }
        val afterFirst = database.surveyRecordDao().getAllSurveysSync()[0].revisionCount

        // Second draft save (load and re-save)
        viewModel.resetDraftSuccess()
        viewModel.saveAsDraft()
        viewModel.uiState.first { !it.isSaving }
        val afterSecond = database.surveyRecordDao().getAllSurveysSync()[0].revisionCount

        // Third draft save
        viewModel.resetDraftSuccess()
        viewModel.saveAsDraft()
        viewModel.uiState.first { !it.isSaving }
        val afterThird = database.surveyRecordDao().getAllSurveysSync()[0].revisionCount

        assertEquals("First draft save should set revisionCount to 1", 1, afterFirst)
        assertEquals("Second draft save must NOT increment revision", 1, afterSecond)
        assertEquals("Third draft save must NOT increment revision", 1, afterThird)
    }

    @Test
    fun `test final save increments revisionCount from draft`() = runTest {
        // 1. Save a draft first
        viewModel.updateSurveyType(SurveyType.WELL)
        viewModel.updateWellName("بئر نهائي")
        viewModel.updateWellType("ارتوازي")
        viewModel.updateWellDepth("100")
        viewModel.updatePumpingMechanism("كهربائية")
        viewModel.updateOperationalStatus("يعمل")
        val uuid = viewModel.uiState.value.surveyUUID

        viewModel.saveAsDraft()
        viewModel.uiState.first { !it.isSaving }
        val draftRevision = database.surveyRecordDao().getAllSurveysSync()[0].revisionCount
        assertEquals("Draft revisionCount should be 1", 1, draftRevision)

        // 2. Load and complete
        val context = RuntimeEnvironment.getApplication()
        val editViewModel = SurveyViewModel(context)
        editViewModel.loadRecordForEdit(uuid)
        editViewModel.uiState.first { it.surveyUUID == uuid }

        val mockGps = GpsLocationResult(
            latitude = 15.35, longitude = 44.20, altitudeM = 2000.0,
            accuracyM = 5.0f, quality = GpsAccuracyQuality.EXCELLENT,
            capturedAt = "2026-08-22 10:00:00"
        )
        editViewModel.updateAdminLocation(
            "YE11", "YE1101", "YE110101", null,
            AdministrativeLocationSnapshot(
                admin1Pcode = "YE11", admin2Pcode = "YE1101", admin3Pcode = "YE110101",
                governorateNameAr = "صنعاء", districtNameAr = "صنعاء", subDistrictNameAr = "صنعاء"
            ),
            false, null, AdminResolutionStatus.CONFIRMED, null, mockGps
        )
        editViewModel.saveSurvey()
        editViewModel.uiState.first { !it.isSaving }

        val completedRevision = database.surveyRecordDao().getAllSurveysSync()[0].revisionCount
        assertEquals("Final save should increment revisionCount to 2", 2, completedRevision)
    }

    @Test
    fun `test edit survey restores saved administrative location fields`() = runTest {
        // 1. Create a survey saved with specific non-default administrative values (e.g., Sa'ada/Razih)
        val savedAdmin1 = "YE22"
        val savedAdmin2 = "YE2208"
        val savedAdmin3 = "YE220801"
        val savedVillageId = "VIL-YE220801-005"

        viewModel.updateSurveyType(SurveyType.WELL)
        viewModel.updateWellName("بئر العين - رازح")
        viewModel.updateAdminLocation(
            savedAdmin1, savedAdmin2, savedAdmin3, savedVillageId,
            AdministrativeLocationSnapshot(
                admin1Pcode = savedAdmin1, admin2Pcode = savedAdmin2, admin3Pcode = savedAdmin3,
                governorateNameAr = "صعدة", districtNameAr = "رازح", subDistrictNameAr = "رازح",
                villageReferenceId = savedVillageId, villageNameAr = "العين"
            ),
            false, null, AdminResolutionStatus.CONFIRMED, null, null
        )

        val uuid = viewModel.uiState.value.surveyUUID
        viewModel.saveAsDraft()
        viewModel.uiState.first { !it.isSaving }

        // 2. Load the record into a new ViewModel instance for editing
        val context = RuntimeEnvironment.getApplication()
        val editViewModel = SurveyViewModel(context)
        editViewModel.loadRecordForEdit(uuid)
        editViewModel.uiState.first { it.surveyUUID == uuid }

        // 3. Verify all saved administrative location fields are restored correctly in state
        val state = editViewModel.uiState.value
        assertEquals("Saved Admin1 P-code must be restored", savedAdmin1, state.admin1Pcode)
        assertEquals("Saved Admin2 P-code must be restored", savedAdmin2, state.admin2Pcode)
        assertEquals("Saved Admin3 P-code must be restored", savedAdmin3, state.admin3Pcode)
        assertEquals("Saved Village Ref ID must be restored", savedVillageId, state.villageRefId)
        assertEquals("Saved governorate snapshot must match", "صعدة", state.snapshot?.governorateNameAr)
        assertEquals("Saved district snapshot must match", "رازح", state.snapshot?.districtNameAr)
        assertEquals("Saved village snapshot must match", "العين", state.snapshot?.villageNameAr)
    }

    @Test
    fun `test edit survey preserves existing GPS when not updated`() = runTest {
        viewModel.updateSurveyType(SurveyType.WELL)
        viewModel.updateWellName("بئر GPS Test")

        val originalGps = GpsLocationResult(
            latitude = 15.111, longitude = 44.222, altitudeM = 2000.0,
            accuracyM = 4.0f, quality = GpsAccuracyQuality.EXCELLENT,
            capturedAt = "2026-08-22 10:00:00"
        )
        
        viewModel.updateAdminLocation(
            "YE11", "YE1101", "YE110101", null,
            AdministrativeLocationSnapshot(
                admin1Pcode = "YE11", admin2Pcode = "YE1101", admin3Pcode = "YE110101",
                governorateNameAr = "صنعاء", districtNameAr = "صنعاء", subDistrictNameAr = "صنعاء"
            ),
            false, null, AdminResolutionStatus.CONFIRMED, null, originalGps
        )

        val uuid = viewModel.uiState.value.surveyUUID
        viewModel.saveAsDraft()
        viewModel.uiState.first { !it.isSaving }

        val editViewModel = SurveyViewModel(RuntimeEnvironment.getApplication())
        editViewModel.loadRecordForEdit(uuid)
        editViewModel.uiState.first { it.surveyUUID == uuid }

        // Simulate admin location update WITHOUT a new GPS (null passed in as it happens on UI load)
        editViewModel.updateAdminLocation(
            "YE11", "YE1101", "YE110101", null,
            AdministrativeLocationSnapshot(
                admin1Pcode = "YE11", admin2Pcode = "YE1101", admin3Pcode = "YE110101",
                governorateNameAr = "صنعاء", districtNameAr = "صنعاء", subDistrictNameAr = "صنعاء"
            ),
            false, null, AdminResolutionStatus.CONFIRMED, null, null
        )

        val preservedGps = editViewModel.uiState.value.gpsLocation
        assertNotNull("GPS should be preserved even if update passed null", preservedGps)
        assertEquals("Latitude must be preserved", 15.111, preservedGps?.latitude)
        assertEquals("Longitude must be preserved", 44.222, preservedGps?.longitude)
        assertEquals("Accuracy must be preserved", 4.0f, preservedGps?.accuracyM)
    }

    // ---- PHASE_11: Revision snapshot ----

    @Test
    fun `new survey leaves admin fields blank until selected`() = runTest {
        assertEquals("New survey should start unselected", "", viewModel.uiState.value.admin1Pcode)
        assertEquals("New survey should start unselected", "", viewModel.uiState.value.admin2Pcode)
        assertEquals("New survey should start unselected", "", viewModel.uiState.value.admin3Pcode)

        viewModel.updateSurveyType(SurveyType.WELL)
        viewModel.updateWellName("بئر جديدة")
        viewModel.updateWellType("ارتوازي")
        viewModel.updateWellDepth("120")
        viewModel.updatePumpingMechanism("مضخة")
        viewModel.updateOperationalStatus("يعمل")

        viewModel.saveAsDraft()
        viewModel.uiState.first { !it.isSaving }

        val record = database.surveyRecordDao().getAllSurveysSync().single()
        assertEquals("admin1Pcode must stay blank", "", record.admin1Pcode)
        assertEquals("admin2Pcode must stay blank", "", record.admin2Pcode)
        assertEquals("admin3Pcode must stay blank", "", record.admin3Pcode)
    }

    @Test
    fun `reopened draft preserves selected admin hierarchy and identity`() = runTest {
        val savedAdmin1 = "YE11"
        val savedAdmin2 = "YE1101"
        val savedAdmin3 = "YE110101"
        val savedVillageId = "VIL-YE110101-001"

        viewModel.updateSurveyType(SurveyType.WELL)
        viewModel.updateWellName("بئر مرتجعة")
        viewModel.updateWellType("ارتوازي")
        viewModel.updateWellDepth("150")
        viewModel.updatePumpingMechanism("مضخة")
        viewModel.updateOperationalStatus("يعمل")
        viewModel.updateAdminLocation(
            savedAdmin1, savedAdmin2, savedAdmin3, savedVillageId,
            AdministrativeLocationSnapshot(
                admin1Pcode = savedAdmin1,
                admin2Pcode = savedAdmin2,
                admin3Pcode = savedAdmin3,
                governorateNameAr = "صعدة",
                districtNameAr = "سحار",
                subDistrictNameAr = "الطلح",
                villageReferenceId = savedVillageId,
                villageNameAr = "المقاش"
            ),
            false, null, AdminResolutionStatus.CONFIRMED, null, null
        )

        val uuid = viewModel.uiState.value.surveyUUID
        viewModel.saveAsDraft()
        viewModel.uiState.first { !it.isSaving }

        val editViewModel = SurveyViewModel(RuntimeEnvironment.getApplication())
        editViewModel.loadRecordForEdit(uuid)
        editViewModel.uiState.first { it.surveyUUID == uuid }

        val state = editViewModel.uiState.value
        assertEquals(savedAdmin1, state.admin1Pcode)
        assertEquals(savedAdmin2, state.admin2Pcode)
        assertEquals(savedAdmin3, state.admin3Pcode)
        assertEquals(savedVillageId, state.villageRefId)
        assertEquals("بئر مرتجعة", state.wellNameAr)
        assertEquals(uuid, state.surveyUUID)
    }

    @Test
    fun `completed edit preserves administrative hierarchy and registry identity`() = runTest {
        viewModel.updateSurveyType(SurveyType.WELL)
        viewModel.updateWellName("بئر نهائية")
        viewModel.updateWellType("ارتوازي")
        viewModel.updateWellDepth("200")
        viewModel.updatePumpingMechanism("شمسية")
        viewModel.updateOperationalStatus("يعمل")

        val gps = GpsLocationResult(
            latitude = 16.95,
            longitude = 43.76,
            altitudeM = 1500.0,
            accuracyM = 4.0f,
            quality = GpsAccuracyQuality.EXCELLENT,
            capturedAt = "2026-09-04 08:00:00"
        )
        viewModel.updateAdminLocation(
            "YE11", "YE1101", "YE110101", "VIL-YE110101-001",
            AdministrativeLocationSnapshot(
                admin1Pcode = "YE11",
                admin2Pcode = "YE1101",
                admin3Pcode = "YE110101",
                governorateNameAr = "صعدة",
                districtNameAr = "سحار",
                subDistrictNameAr = "الطلح",
                villageReferenceId = "VIL-YE110101-001",
                villageNameAr = "المقاش"
            ),
            false, null, AdminResolutionStatus.CONFIRMED, null, gps
        )
        viewModel.saveSurvey()
        viewModel.uiState.first { !it.isSaving }

        val uuid = viewModel.uiState.value.surveyUUID
        val originalRegistryCode = database.surveyRecordDao().getSurveyByUUID(uuid)?.registryCode
        assertNotNull(originalRegistryCode)

        val editViewModel = SurveyViewModel(RuntimeEnvironment.getApplication())
        editViewModel.loadRecordForEdit(uuid)
        editViewModel.uiState.first { it.surveyUUID == uuid }

        assertEquals("YE11", editViewModel.uiState.value.admin1Pcode)
        assertEquals("YE1101", editViewModel.uiState.value.admin2Pcode)
        assertEquals("YE110101", editViewModel.uiState.value.admin3Pcode)
        assertEquals("VIL-YE110101-001", editViewModel.uiState.value.villageRefId)
        assertEquals(uuid, editViewModel.uiState.value.surveyUUID)
        assertEquals(originalRegistryCode, editViewModel.uiState.value.existingRegistryCode)
        assertEquals("بئر نهائية", editViewModel.uiState.value.wellNameAr)
    }

    @Test
    fun `test revision snapshot created when editing completed survey`() = runTest {
        // 1. Save a complete survey from scratch (no prior record — no snapshot expected)
        viewModel.updateSurveyType(SurveyType.WELL)
        viewModel.updateWellName("بئر المراجعة")
        viewModel.updateWellType("ارتوازي")
        viewModel.updateWellDepth("200")
        viewModel.updatePumpingMechanism("شمسية")
        viewModel.updateOperationalStatus("يعمل")

        val mockGps = GpsLocationResult(
            latitude = 15.55, longitude = 44.33, altitudeM = 1500.0,
            accuracyM = 6.0f, quality = GpsAccuracyQuality.EXCELLENT,
            capturedAt = "2026-09-03 10:00:00"
        )
        viewModel.updateAdminLocation(
            "YE11", "YE1101", "YE110101", null,
            AdministrativeLocationSnapshot(
                admin1Pcode = "YE11", admin2Pcode = "YE1101", admin3Pcode = "YE110101",
                governorateNameAr = "صنعاء", districtNameAr = "صنعاء", subDistrictNameAr = "صنعاء"
            ),
            false, null, AdminResolutionStatus.CONFIRMED, null, mockGps
        )
        viewModel.saveSurvey()
        viewModel.uiState.first { !it.isSaving }
        assertTrue("Initial save must succeed", viewModel.uiState.value.saveSuccess)

        val uuid = viewModel.uiState.value.surveyUUID
        val revisionsAfterFirst = database.surveyRevisionDao().getRevisionsForSurveySync(uuid)
        assertEquals("No revision snapshot expected for first-time save", 0, revisionsAfterFirst.size)

        // 2. Reopen and re-save (this is an edit of a COMPLETED record)
        val editViewModel = SurveyViewModel(RuntimeEnvironment.getApplication())
        editViewModel.loadRecordForEdit(uuid)
        editViewModel.uiState.first { it.surveyUUID == uuid }

        val restoredRegistryCode = editViewModel.uiState.value.existingRegistryCode
        val restoredRevisionCount = editViewModel.uiState.value.existingRevisionCount
        assertEquals("revisionCount before re-edit must be 1", 1, restoredRevisionCount)

        // Update a field and re-save with GPS still present
        editViewModel.updateWellName("بئر المراجعة (معدّل)")
        editViewModel.updateAdminLocation(
            "YE11", "YE1101", "YE110101", null,
            AdministrativeLocationSnapshot(
                admin1Pcode = "YE11", admin2Pcode = "YE1101", admin3Pcode = "YE110101",
                governorateNameAr = "صنعاء", districtNameAr = "صنعاء", subDistrictNameAr = "صنعاء"
            ),
            false, null, AdminResolutionStatus.CONFIRMED, null, mockGps
        )
        editViewModel.saveSurvey()
        editViewModel.uiState.first { !it.isSaving }
        assertTrue("Re-save after edit must succeed", editViewModel.uiState.value.saveSuccess)

        // 3. Verify identity preserved
        val allSurveys = database.surveyRecordDao().getAllSurveysSync()
        assertEquals("Must not create a duplicate record", 1, allSurveys.size)
        val updated = allSurveys[0]
        assertEquals("surveyUUID must be unchanged", uuid, updated.surveyUUID)
        assertEquals("registryCode must be unchanged", restoredRegistryCode, updated.registryCode)
        assertEquals("COMPLETED", updated.workflowStatus)
        assertEquals("revisionCount must increment to 2", 2, updated.revisionCount)

        // 4. Verify revision snapshot row was written
        val revisionsAfterEdit = database.surveyRevisionDao().getRevisionsForSurveySync(uuid)
        assertEquals("Exactly one revision snapshot must exist after re-edit", 1, revisionsAfterEdit.size)
        val snapshot = revisionsAfterEdit[0]
        assertEquals("Snapshot surveyUUID must match", uuid, snapshot.surveyUUID)
        assertEquals("Snapshot revisionNumber must be 2", 2, snapshot.revisionNumber)
        assertEquals("Previous status must be COMPLETED", "COMPLETED", snapshot.previousStatus)
        assertEquals("New status must be COMPLETED", "COMPLETED", snapshot.newStatus)
        assertTrue("Snapshot snapshotDataJson must contain registryCode",
            snapshot.snapshotDataJson.contains("registryCode"))
    }

    // =========================================================================
    // C3: Form Package Identity Binding Tests
    // =========================================================================

    @Test
    fun `test well survey stamps active form package identity`() = runTest {
        // Insert active package for form-well-standard version 2.0
        val wellPackage = FormPackageEntity(
            packageUid = "form-well-standard@2.0",
            formId = "form-well-standard",
            version = "2.0",
            name = "استمارة حصر وتوثيق آبار المياه المحدثة",
            description = "الاستمارة المحدثة",
            publisher = "NWRA",
            packagePath = "/data/packages/form-well-standard/2.0",
            checksum = "abc123hash",
            isActive = true,
            installationDate = "2026-09-04 10:00:00",
            updatedAt = "2026-09-04 10:00:00"
        )
        database.formPackageDao().insertPackage(wellPackage)

        viewModel.updateSurveyType(SurveyType.WELL)
        viewModel.updateWellName("بئر المعاينة 1")
        viewModel.updateWellType("ارتوازي")
        viewModel.updateWellDepth("180")
        viewModel.updatePumpingMechanism("طاقة شمسية")
        viewModel.updateOperationalStatus("يعمل")

        val mockGps = GpsLocationResult(
            latitude = 15.35, longitude = 44.20, altitudeM = 2000.0,
            accuracyM = 5.0f, quality = GpsAccuracyQuality.EXCELLENT,
            capturedAt = "2026-09-04 12:00:00"
        )
        viewModel.updateAdminLocation(
            "YE11", "YE1101", "YE110101", null,
            AdministrativeLocationSnapshot("YE11", "YE1101", "YE110101", "صنعاء", "صنعاء", "صنعاء"),
            false, null, AdminResolutionStatus.CONFIRMED, null, mockGps
        )

        viewModel.saveSurvey()
        viewModel.uiState.first { !it.isSaving }
        assertTrue(viewModel.uiState.value.saveSuccess)

        val record = database.surveyRecordDao().getSurveyByUUID(viewModel.uiState.value.surveyUUID)
        assertNotNull(record)
        assertEquals("form-well-standard", record!!.formId)
        assertEquals("2.0", record.formVersion)
    }

    @Test
    fun `test spring survey stamps active form package identity`() = runTest {
        // Insert active package for form-spring-standard version 1.0
        val springPackage = FormPackageEntity(
            packageUid = "form-spring-standard@1.0",
            formId = "form-spring-standard",
            version = "1.0",
            name = "استمارة حصر وتوثيق العيون والينابيع",
            description = "استمارة العيون",
            publisher = "NWRA",
            packagePath = "/data/packages/form-spring-standard/1.0",
            checksum = "def456hash",
            isActive = true,
            installationDate = "2026-09-04 10:00:00",
            updatedAt = "2026-09-04 10:00:00"
        )
        database.formPackageDao().insertPackage(springPackage)

        viewModel.updateSurveyType(SurveyType.SPRING)
        viewModel.updateSpringName("عين الغيل")
        viewModel.updateFlowRate("12.5")
        viewModel.updateWaterClarity("صافية")
        viewModel.updateDischargeSeasonality("دائمة")

        val mockGps = GpsLocationResult(
            latitude = 15.40, longitude = 44.25, altitudeM = 1800.0,
            accuracyM = 4.0f, quality = GpsAccuracyQuality.EXCELLENT,
            capturedAt = "2026-09-04 12:00:00"
        )
        viewModel.updateAdminLocation(
            "YE11", "YE1101", "YE110101", null,
            AdministrativeLocationSnapshot("YE11", "YE1101", "YE110101", "صنعاء", "صنعاء", "صنعاء"),
            false, null, AdminResolutionStatus.CONFIRMED, null, mockGps
        )

        viewModel.saveSurvey()
        viewModel.uiState.first { !it.isSaving }
        assertTrue(viewModel.uiState.value.saveSuccess)

        val record = database.surveyRecordDao().getSurveyByUUID(viewModel.uiState.value.surveyUUID)
        assertNotNull(record)
        assertEquals("form-spring-standard", record!!.formId)
        assertEquals("1.0", record.formVersion)
    }

    @Test
    fun `test draft save stamps active form package identity`() = runTest {
        val wellPackage = FormPackageEntity(
            packageUid = "form-well-standard@2.0",
            formId = "form-well-standard",
            version = "2.0",
            name = "استمارة حصر وتوثيق آبار المياه المحدثة",
            description = "الاستمارة المحدثة",
            publisher = "NWRA",
            packagePath = "/data/packages/form-well-standard/2.0",
            checksum = "abc123hash",
            isActive = true,
            installationDate = "2026-09-04 10:00:00",
            updatedAt = "2026-09-04 10:00:00"
        )
        database.formPackageDao().insertPackage(wellPackage)

        viewModel.updateSurveyType(SurveyType.WELL)
        viewModel.updateWellName("مسودة بئر")
        viewModel.saveAsDraft()
        viewModel.uiState.first { !it.isSaving }
        assertTrue(viewModel.uiState.value.draftSaveSuccess)

        val record = database.surveyRecordDao().getSurveyByUUID(viewModel.uiState.value.surveyUUID)
        assertNotNull(record)
        assertEquals("form-well-standard", record!!.formId)
        assertEquals("2.0", record.formVersion)
        assertEquals("DRAFT", record.workflowStatus)
    }

    @Test
    fun `test survey falls back safely when no package is active`() = runTest {
        // No packages in database
        viewModel.updateSurveyType(SurveyType.DAM)
        viewModel.updateDamName("سد تجريبي")
        viewModel.updateStructureType("خرساني")
        viewModel.updateStorageCapacity("50000")
        viewModel.updateDamHeight("15")

        val mockGps = GpsLocationResult(
            latitude = 15.30, longitude = 44.10, altitudeM = 2200.0,
            accuracyM = 8.0f, quality = GpsAccuracyQuality.GOOD,
            capturedAt = "2026-09-04 12:00:00"
        )
        viewModel.updateAdminLocation(
            "YE11", "YE1101", "YE110101", null,
            AdministrativeLocationSnapshot("YE11", "YE1101", "YE110101", "صنعاء", "صنعاء", "صنعاء"),
            false, null, AdminResolutionStatus.CONFIRMED, null, mockGps
        )

        viewModel.saveSurvey()
        viewModel.uiState.first { !it.isSaving }
        assertTrue(viewModel.uiState.value.saveSuccess)

        val record = database.surveyRecordDao().getSurveyByUUID(viewModel.uiState.value.surveyUUID)
        assertNotNull(record)
        assertEquals("WATER_SURVEY_V1", record!!.formId)
        assertEquals("1.0", record.formVersion)
    }

    @Test
    fun `test legacy survey edit preserves existing form identity`() = runTest {
        // Insert a legacy record into DB
        val legacyEntity = com.yemen.watersurvey.data.entity.SurveyRecordEntity(
            surveyUUID = "legacy-uuid-12345",
            recordId = "REC-LEGACY",
            formId = "CUSTOM_LEGACY_FORM",
            formVersion = "0.9-beta",
            surveyType = "DAM",
            admin1Pcode = "YE11",
            admin2Pcode = "YE1101",
            admin3Pcode = "YE110101",
            enumeratorId = "ENUM-001",
            enumeratorUsername = "ENUM-001",
            workflowStatus = "COMPLETED",
            revisionCount = 1,
            createdAt = "2026-08-01 10:00:00",
            updatedAt = "2026-08-01 10:00:00"
        )
        database.surveyRecordDao().insertOrUpdateSurvey(legacyEntity)

        // Load for editing
        val editViewModel = SurveyViewModel(RuntimeEnvironment.getApplication())
        editViewModel.loadRecordForEdit("legacy-uuid-12345")
        editViewModel.uiState.first { it.surveyUUID == "legacy-uuid-12345" }

        assertEquals("CUSTOM_LEGACY_FORM", editViewModel.uiState.value.existingFormId)
        assertEquals("0.9-beta", editViewModel.uiState.value.existingFormVersion)

        // Save as draft to test preservation
        editViewModel.saveAsDraft()
        editViewModel.uiState.first { !it.isSaving }
        assertTrue(editViewModel.uiState.value.draftSaveSuccess)

        val updatedRecord = database.surveyRecordDao().getSurveyByUUID("legacy-uuid-12345")
        assertNotNull(updatedRecord)
        assertEquals("CUSTOM_LEGACY_FORM", updatedRecord!!.formId)
        assertEquals("0.9-beta", updatedRecord.formVersion)
    }

    // =========================================================================
    // C4: Form Package Lifecycle Protection Tests
    // =========================================================================

    @Test
    fun `test package deletion is rejected when referenced by an existing survey`() = runTest {
        val formPkgManager = FormPackageManager(RuntimeEnvironment.getApplication())

        // 1. Insert a package
        val wellPackageV1 = FormPackageEntity(
            packageUid = "form-well-standard@1.0",
            formId = "form-well-standard",
            version = "1.0",
            name = "استمارة آبار v1",
            description = "الإصدار الأول",
            publisher = "NWRA",
            packagePath = "/data/packages/form-well-standard/1.0",
            checksum = "hash1",
            isActive = false,
            installationDate = "2026-09-01 10:00:00",
            updatedAt = "2026-09-01 10:00:00"
        )
        database.formPackageDao().insertPackage(wellPackageV1)

        // 2. Insert a survey record referencing this package
        val survey = com.yemen.watersurvey.data.entity.SurveyRecordEntity(
            surveyUUID = "survey-ref-v1",
            recordId = "REC-V1",
            formId = "form-well-standard",
            formVersion = "1.0",
            surveyType = "WELL",
            admin1Pcode = "YE11",
            admin2Pcode = "YE1101",
            admin3Pcode = "YE110101",
            enumeratorId = "ENUM-001",
            enumeratorUsername = "ENUM-001",
            workflowStatus = "COMPLETED",
            revisionCount = 1,
            createdAt = "2026-09-01 11:00:00",
            updatedAt = "2026-09-01 11:00:00"
        )
        database.surveyRecordDao().insertOrUpdateSurvey(survey)

        // 3. Attempt deletion — must be REJECTED (returns false)
        val deleteResult = formPkgManager.deletePackage("form-well-standard", "1.0")
        assertFalse("Package deletion must be rejected when referenced by a survey", deleteResult)

        // 4. Verify package still exists in database
        val pkgInDb = database.formPackageDao().getPackageByVersion("form-well-standard", "1.0")
        assertNotNull("Package must remain in database after rejected deletion", pkgInDb)
    }

    @Test
    fun `test unreferenced package can still be removed`() = runTest {
        val formPkgManager = FormPackageManager(RuntimeEnvironment.getApplication())

        // 1. Insert an unreferenced package
        val damPackage = FormPackageEntity(
            packageUid = "form-dam-standard@1.0",
            formId = "form-dam-standard",
            version = "1.0",
            name = "استمارة سدود v1",
            description = "سدود",
            publisher = "NWRA",
            packagePath = "/data/packages/form-dam-standard/1.0",
            checksum = "hash-dam",
            isActive = false,
            installationDate = "2026-09-01 10:00:00",
            updatedAt = "2026-09-01 10:00:00"
        )
        database.formPackageDao().insertPackage(damPackage)

        // 2. Verify deletion succeeds
        val deleteResult = formPkgManager.deletePackage("form-dam-standard", "1.0")
        assertTrue("Unreferenced package must be removable", deleteResult)

        // 3. Verify package no longer exists in DB
        val pkgInDb = database.formPackageDao().getPackageByVersion("form-dam-standard", "1.0")
        assertNull("Package must be removed from DB", pkgInDb)
    }

    @Test
    fun `test existing survey retains original formId and formVersion after another version becomes ACTIVE`() = runTest {
        // 1. Insert v1 (inactive) and v2 (active)
        val wellPackageV1 = FormPackageEntity(
            packageUid = "form-well-standard@1.0",
            formId = "form-well-standard",
            version = "1.0",
            name = "استمارة آبار v1",
            description = "الإصدار 1",
            publisher = "NWRA",
            packagePath = "/data/packages/form-well-standard/1.0",
            checksum = "hash1",
            isActive = false,
            installationDate = "2026-09-01 10:00:00",
            updatedAt = "2026-09-01 10:00:00"
        )
        val wellPackageV2 = FormPackageEntity(
            packageUid = "form-well-standard@2.0",
            formId = "form-well-standard",
            version = "2.0",
            name = "استمارة آبار v2",
            description = "الإصدار 2 المعتمد حديثاً",
            publisher = "NWRA",
            packagePath = "/data/packages/form-well-standard/2.0",
            checksum = "hash2",
            isActive = true, // v2 is currently ACTIVE
            installationDate = "2026-09-04 10:00:00",
            updatedAt = "2026-09-04 10:00:00"
        )
        database.formPackageDao().insertPackage(wellPackageV1)
        database.formPackageDao().insertPackage(wellPackageV2)

        // 2. Existing survey created under v1
        val surveyV1 = com.yemen.watersurvey.data.entity.SurveyRecordEntity(
            surveyUUID = "survey-historical-v1",
            recordId = "REC-HIST-V1",
            formId = "form-well-standard",
            formVersion = "1.0",
            surveyType = "WELL",
            admin1Pcode = "YE11",
            admin2Pcode = "YE1101",
            admin3Pcode = "YE110101",
            enumeratorId = "ENUM-001",
            enumeratorUsername = "ENUM-001",
            workflowStatus = "COMPLETED",
            revisionCount = 1,
            createdAt = "2026-09-01 12:00:00",
            updatedAt = "2026-09-01 12:00:00"
        )
        database.surveyRecordDao().insertOrUpdateSurvey(surveyV1)

        // 3. Load record for edit in ViewModel
        val editViewModel = SurveyViewModel(RuntimeEnvironment.getApplication())
        editViewModel.loadRecordForEdit("survey-historical-v1")
        editViewModel.uiState.first { it.surveyUUID == "survey-historical-v1" }

        assertEquals("form-well-standard", editViewModel.uiState.value.existingFormId)
        assertEquals("1.0", editViewModel.uiState.value.existingFormVersion)

        // Re-save the edited survey with valid GPS
        val mockGps = GpsLocationResult(
            latitude = 15.35, longitude = 44.20, altitudeM = 2000.0,
            accuracyM = 5.0f, quality = GpsAccuracyQuality.EXCELLENT,
            capturedAt = "2026-09-04 12:00:00"
        )
        editViewModel.updateWellName("بئر تاريخي تم تحديثه")
        editViewModel.updateWellType("ارتوازي")
        editViewModel.updateWellDepth("220")
        editViewModel.updateAdminLocation(
            "YE11", "YE1101", "YE110101", null,
            AdministrativeLocationSnapshot("YE11", "YE1101", "YE110101", "صنعاء", "صنعاء", "صنعاء"),
            false, null, AdminResolutionStatus.CONFIRMED, null, mockGps
        )
        editViewModel.saveSurvey()
        editViewModel.uiState.first { !it.isSaving }
        assertTrue(editViewModel.uiState.value.saveSuccess)

        // 4. Verify survey still retains formVersion 1.0 (NOT rewritten to 2.0)
        val updatedRecord = database.surveyRecordDao().getSurveyByUUID("survey-historical-v1")
        assertNotNull(updatedRecord)
        assertEquals("form-well-standard", updatedRecord!!.formId)
        assertEquals("1.0", updatedRecord.formVersion)
    }

    @Test
    fun `test retiring or deactivating a package does not invalidate existing surveys`() = runTest {
        // 1. Insert package and survey
        val wellPackageV1 = FormPackageEntity(
            packageUid = "form-well-standard@1.0",
            formId = "form-well-standard",
            version = "1.0",
            name = "استمارة آبار v1",
            description = "الإصدار 1",
            publisher = "NWRA",
            packagePath = "/data/packages/form-well-standard/1.0",
            checksum = "hash1",
            isActive = true,
            installationDate = "2026-09-01 10:00:00",
            updatedAt = "2026-09-01 10:00:00"
        )
        database.formPackageDao().insertPackage(wellPackageV1)

        val surveyV1 = com.yemen.watersurvey.data.entity.SurveyRecordEntity(
            surveyUUID = "survey-deactivate-test",
            recordId = "REC-DEACT-01",
            formId = "form-well-standard",
            formVersion = "1.0",
            surveyType = "WELL",
            admin1Pcode = "YE11",
            admin2Pcode = "YE1101",
            admin3Pcode = "YE110101",
            enumeratorId = "ENUM-001",
            enumeratorUsername = "ENUM-001",
            workflowStatus = "COMPLETED",
            revisionCount = 1,
            createdAt = "2026-09-01 12:00:00",
            updatedAt = "2026-09-01 12:00:00"
        )
        database.surveyRecordDao().insertOrUpdateSurvey(surveyV1)

        // 2. Deactivate all versions of this form
        database.formPackageDao().deactivateAllVersionsForForm("form-well-standard")

        // 3. Confirm package is deactivated
        val activePkg = database.formPackageDao().getActivePackageForForm("form-well-standard")
        assertNull("No package should be active", activePkg)

        // 4. Verify survey record is still fully intact, readable, and retains its identity
        val record = database.surveyRecordDao().getSurveyByUUID("survey-deactivate-test")
        assertNotNull(record)
        assertEquals("form-well-standard", record!!.formId)
        assertEquals("1.0", record.formVersion)
        assertEquals("COMPLETED", record.workflowStatus)

        // 5. Verify record can still be loaded for editing without loss of form identity
        val editViewModel = SurveyViewModel(RuntimeEnvironment.getApplication())
        editViewModel.loadRecordForEdit("survey-deactivate-test")
        editViewModel.uiState.first { it.surveyUUID == "survey-deactivate-test" }
        assertEquals("form-well-standard", editViewModel.uiState.value.existingFormId)
        assertEquals("1.0", editViewModel.uiState.value.existingFormVersion)
    }

    @Test
    fun `test opening and editing an old survey continues to use stored form package identity`() = runTest {
        // 1. Active package is v2.0
        val wellPackageV2 = FormPackageEntity(
            packageUid = "form-well-standard@2.0",
            formId = "form-well-standard",
            version = "2.0",
            name = "استمارة آبار v2",
            description = "الإصدار 2",
            publisher = "NWRA",
            packagePath = "/data/packages/form-well-standard/2.0",
            checksum = "hash2",
            isActive = true,
            installationDate = "2026-09-04 10:00:00",
            updatedAt = "2026-09-04 10:00:00"
        )
        database.formPackageDao().insertPackage(wellPackageV2)

        // 2. Old survey has legacy WATER_SURVEY_V1 / 1.0 identity
        val legacySurvey = com.yemen.watersurvey.data.entity.SurveyRecordEntity(
            surveyUUID = "legacy-survey-999",
            recordId = "REC-LEG-999",
            formId = "WATER_SURVEY_V1",
            formVersion = "1.0",
            surveyType = "WELL",
            admin1Pcode = "YE11",
            admin2Pcode = "YE1101",
            admin3Pcode = "YE110101",
            enumeratorId = "ENUM-001",
            enumeratorUsername = "ENUM-001",
            workflowStatus = "DRAFT",
            revisionCount = 1,
            createdAt = "2026-08-15 10:00:00",
            updatedAt = "2026-08-15 10:00:00"
        )
        database.surveyRecordDao().insertOrUpdateSurvey(legacySurvey)

        // 3. Open draft in ViewModel and save again as draft
        val editViewModel = SurveyViewModel(RuntimeEnvironment.getApplication())
        editViewModel.loadRecordForEdit("legacy-survey-999")
        editViewModel.uiState.first { it.surveyUUID == "legacy-survey-999" }

        assertEquals("WATER_SURVEY_V1", editViewModel.uiState.value.existingFormId)
        assertEquals("1.0", editViewModel.uiState.value.existingFormVersion)

        editViewModel.updateWellName("مسودة قديمة معدلة")
        editViewModel.saveAsDraft()
        editViewModel.uiState.first { !it.isSaving }
        assertTrue(editViewModel.uiState.value.draftSaveSuccess)

        // 4. Verify saved entity preserves WATER_SURVEY_V1 (not overwritten with active form-well-standard@2.0)
        val updated = database.surveyRecordDao().getSurveyByUUID("legacy-survey-999")
        assertNotNull(updated)
        assertEquals("WATER_SURVEY_V1", updated!!.formId)
        assertEquals("1.0", updated.formVersion)
    }

    @Test
    fun `test new surveys resolve currently ACTIVE package according to survey type`() = runTest {
        // 1. Insert v1 (inactive) and v2 (active) for WELL
        val wellPackageV1 = FormPackageEntity(
            packageUid = "form-well-standard@1.0",
            formId = "form-well-standard",
            version = "1.0",
            name = "استمارة آبار v1",
            description = "الإصدار 1",
            publisher = "NWRA",
            packagePath = "/data/packages/form-well-standard/1.0",
            checksum = "hash1",
            isActive = false,
            installationDate = "2026-09-01 10:00:00",
            updatedAt = "2026-09-01 10:00:00"
        )
        val wellPackageV2 = FormPackageEntity(
            packageUid = "form-well-standard@2.0",
            formId = "form-well-standard",
            version = "2.0",
            name = "استمارة آبار v2",
            description = "الإصدار 2 النشط",
            publisher = "NWRA",
            packagePath = "/data/packages/form-well-standard/2.0",
            checksum = "hash2",
            isActive = true,
            installationDate = "2026-09-04 10:00:00",
            updatedAt = "2026-09-04 10:00:00"
        )
        database.formPackageDao().insertPackage(wellPackageV1)
        database.formPackageDao().insertPackage(wellPackageV2)

        // 2. Create brand new WELL survey
        viewModel.updateSurveyType(SurveyType.WELL)
        viewModel.updateWellName("بئر جديد كلياً")
        viewModel.updateWellType("يدوي")
        viewModel.updateWellDepth("35")
        viewModel.updatePumpingMechanism("دلو")
        viewModel.updateOperationalStatus("يعمل")

        val mockGps = GpsLocationResult(
            latitude = 15.35, longitude = 44.20, altitudeM = 2000.0,
            accuracyM = 5.0f, quality = GpsAccuracyQuality.EXCELLENT,
            capturedAt = "2026-09-04 12:00:00"
        )
        viewModel.updateAdminLocation(
            "YE11", "YE1101", "YE110101", null,
            AdministrativeLocationSnapshot("YE11", "YE1101", "YE110101", "صنعاء", "صنعاء", "صنعاء"),
            false, null, AdminResolutionStatus.CONFIRMED, null, mockGps
        )

        viewModel.saveSurvey()
        viewModel.uiState.first { !it.isSaving }
        assertTrue(viewModel.uiState.value.saveSuccess)

        // 3. Verify new survey receives the ACTIVE package version 2.0
        val record = database.surveyRecordDao().getSurveyByUUID(viewModel.uiState.value.surveyUUID)
        assertNotNull(record)
        assertEquals("form-well-standard", record!!.formId)
        assertEquals("2.0", record.formVersion)
    }
}
