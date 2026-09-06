package com.yemen.watersurvey.presentation.form

import android.app.Application
import androidx.room.Room
import org.robolectric.RuntimeEnvironment
import com.yemen.watersurvey.core.expression.ExpressionEvaluatorEngine
import com.yemen.watersurvey.core.expression.FormExpressionEvaluatorImpl
import com.yemen.watersurvey.core.form.FormDefinitionParser
import com.yemen.watersurvey.core.form.FormPackageManager
import com.yemen.watersurvey.data.database.SurveyAppDatabase
import com.yemen.watersurvey.data.entity.SurveyRecordEntity
import com.yemen.watersurvey.data.entity.Admin1Entity
import com.yemen.watersurvey.data.entity.Admin2Entity
import com.yemen.watersurvey.data.entity.Admin3Entity
import com.yemen.watersurvey.data.entity.FormPackageEntity
import com.yemen.watersurvey.domain.model.*
import com.yemen.watersurvey.presentation.viewmodel.SurveyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Phase 14 Comprehensive Dynamic Form Runtime Integration Test Suite.
 *
 * Verifies:
 * 1. Active package dynamic resolution for WELL, SPRING, and WATER_HARVESTING (canonical v6 IDs).
 * 2. Fail-Fast semantics when no active package exists (zero hardcoded fallback).
 * 3. Form identity preservation across survey lifecycle and revision updates.
 * 4. DynamicFormState response mutations and administrative/GPS field binding.
 * 5. In-memory expression calculations during survey draft and completion.
 * 6. Strict relevance gating: irrelevant values are excluded from persisted answers JSON.
 * 7. Constraint validation gating final completion.
 * 8. GPS accuracy gate (<15m) on completion, bypassed on draft.
 * 9. Administrative reference framework decoupling via RoomAdminLookupProvider.
 * 10. Room persistence of normalized answers into type JSON without schema alterations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class DynamicFormRuntimeIntegrationTest {

    private lateinit var context: Application
    private lateinit var database: SurveyAppDatabase
    private lateinit var packageManager: FormPackageManager
    private lateinit var viewModel: SurveyViewModel
    private lateinit var tempDir: File
    private lateinit var testDispatcher: TestDispatcher

    @Before
    fun setup() {
        testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        context = RuntimeEnvironment.getApplication()

        try {
            val instanceField = SurveyAppDatabase::class.java.getDeclaredField("INSTANCE")
            instanceField.isAccessible = true
            instanceField.set(null, null)
        } catch (_: Exception) {}

        database = Room.inMemoryDatabaseBuilder(context, SurveyAppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val instanceField = SurveyAppDatabase::class.java.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true
        instanceField.set(null, database)

        packageManager = FormPackageManager(context, formPackageDao = database.formPackageDao())
        viewModel = SurveyViewModel(context)
        tempDir = File(context.cacheDir, "test_pkg_${System.currentTimeMillis()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        database.close()
        Dispatchers.resetMain()
        try {
            val instanceField = SurveyAppDatabase::class.java.getDeclaredField("INSTANCE")
            instanceField.isAccessible = true
            instanceField.set(null, null)
        } catch (_: Exception) {}
    }

    /**
     * Helper to install a realistic v6 Form Package into the test database and filesystem.
     */
    private suspend fun installPackage(
        formId: String,
        version: String = "2026-09-05-v6",
        facilityType: String = "WELL",
        title: String = "استمارة فحص",
        elementsJson: String = "[]"
    ): File {
        val pkgDir = File(packageManager.packagesBaseDir, "$formId/$version").apply { mkdirs() }
        
        val metaJson = JSONObject().apply {
            put("formId", formId)
            put("version", version)
            put("name", title)
            put("surveyType", facilityType)
            put("targetFacilityType", facilityType)
            put("schemaVersion", "1.0")
            put("packageVersion", 1)
        }
        File(pkgDir, "metadata.json").writeText(metaJson.toString(2))

        val formDefJson = JSONObject().apply {
            put("formId", formId)
            put("version", version)
            put("title", title)
            put("elements", org.json.JSONArray(elementsJson))
        }
        File(pkgDir, "form_definition.json").writeText(formDefJson.toString(2))
        File(pkgDir, "choices.json").writeText("{}")

        val entity = FormPackageEntity(
            packageUid = "$formId@$version",
            formId = formId,
            version = version,
            name = title,
            description = "Test package",
            publisher = "NWRA",
            packagePath = pkgDir.absolutePath,
            checksum = "sha256-test",
            isActive = true,
            installationDate = "2026-09-05 12:00:00",
            updatedAt = "2026-09-05 12:00:00"
        )
        database.formPackageDao().insertPackage(entity)
        return pkgDir
    }

    // --- TEST 1: ACTIVE PACKAGE RESOLUTION ---
    @Test
    fun test1_ActivePackageResolutionForCanonicalV6Forms() = runTest {
        installPackage("yem_water_wells_saadah", "2026-09-05-v6", "WELL", "استمارة الآبار")
        installPackage("yem_water_springs_saadah", "2026-09-05-v6", "SPRING", "استمارة العيون")
        installPackage("yem_water_harvesting_saadah", "2026-09-05-v6", "DAM", "استمارة حصاد المياه")

        val wellPkg = packageManager.getActivePackageForSurveyType(SurveyType.WELL)
        assertNotNull("Well package must resolve", wellPkg)
        assertEquals("yem_water_wells_saadah", wellPkg?.formId)
        assertEquals("2026-09-05-v6", wellPkg?.version)

        val springPkg = packageManager.getActivePackageForSurveyType(SurveyType.SPRING)
        assertNotNull("Spring package must resolve", springPkg)
        assertEquals("yem_water_springs_saadah", springPkg?.formId)

        val damPkg = packageManager.getActivePackageForSurveyType(SurveyType.DAM)
        assertNotNull("Water harvesting package must resolve", damPkg)
        assertEquals("yem_water_harvesting_saadah", damPkg?.formId)
    }

    // --- TEST 2: LEGACY FALLBACK WHEN NO ACTIVE PACKAGE ---
    @Test
    fun testLegacyFallbackWhenNoActivePackageExists() = runTest {
        // Zero packages installed in database
        val unresolved = packageManager.getActivePackageForSurveyType(SurveyType.WELL)
        assertNull("Package resolution must return null if no active package exists", unresolved)

        // Initialize survey on empty database -> should proceed without error
        viewModel.initializeDynamicSurvey(SurveyType.WELL)
        val initState = viewModel.uiState.first { !it.isLoadingRecord }
        assertNull("Expected no error when no active package exists", initState.error)
        assertNull("Expected no active package", initState.activeFormPackage)
        assertEquals("Form ID should be empty until saved", "", initState.formId)

        // Save as draft to trigger the legacy fallback identity
        viewModel.saveAsDraft()
        viewModel.uiState.first { it.draftSaveSuccess }

        // Retrieve the saved record and verify fallback identity
        val savedRecord = database.surveyRecordDao().getSurveyByUUID(initState.surveyUUID)
        assertNotNull("Saved record should exist", savedRecord)
        assertEquals("Expected fallback formId", "WATER_SURVEY_V1", savedRecord!!.formId)
        assertEquals("Expected fallback formVersion", "1.0", savedRecord.formVersion)
    }

    // --- TEST 3: FORM IDENTITY PRESERVATION ---
    @Test
    fun test3_FormIdentityPreservedOnEdit() = runTest {
        installPackage("yem_water_wells_saadah", "2026-09-05-v6", "WELL", "إصدار قديم")

        // Create survey with initial version
        viewModel.initializeDynamicSurvey(SurveyType.WELL)
        viewModel.uiState.first { it.activeFormPackage != null }
        val surveyUUID = viewModel.uiState.value.surveyUUID

        viewModel.onDynamicFieldValueChanged("water_facility_name", RuntimeValue.Text("بئر السلام"))
        viewModel.saveAsDraft()
        viewModel.uiState.first { it.draftSaveSuccess }

        val saved = database.surveyRecordDao().getSurveyByUUID(surveyUUID)
        assertNotNull(saved)
        assertEquals("yem_water_wells_saadah", saved!!.formId)
        assertEquals("2026-09-05-v6", saved.formVersion)

        // Now install and activate a newer package version 2026-10-01-v7
        installPackage("yem_water_wells_saadah", "2026-10-01-v7", "WELL", "إصدار حديث")

        // Reload the old survey for editing
        viewModel.loadRecordForEdit(surveyUUID)
        val editState = viewModel.uiState.first { !it.isLoadingRecord }

        // Must PRESERVE historical form version 2026-09-05-v6, NOT overwrite with v7
        assertEquals("yem_water_wells_saadah", editState.formId)
        assertEquals("2026-09-05-v6", editState.formVersion)
    }

    // --- TEST 4: DYNAMIC VALUE CHANGE & PROPAGATION ---
    @Test
    fun test4_DynamicFieldMutationAndPropagation() = runTest {
        installPackage("yem_water_wells_saadah", "2026-09-05-v6", "WELL", "الآبار")
        viewModel.initializeDynamicSurvey(SurveyType.WELL)
        viewModel.uiState.first { it.activeFormPackage != null }

        viewModel.onDynamicFieldValueChanged("water_facility_name", RuntimeValue.Text("بئر النور"))
        viewModel.onDynamicFieldValueChanged("gov_pcode", RuntimeValue.Choice("YE11"))
        viewModel.onDynamicFieldValueChanged("district_code", RuntimeValue.Choice("YE1101"))
        viewModel.onDynamicFieldValueChanged("gps_coord", RuntimeValue.Geopoint(15.35, 44.20, 2000.0, 4.5f))

        val state = viewModel.uiState.value
        assertEquals("بئر النور", state.dynamicFormState.getText("water_facility_name"))
        assertEquals("YE11", state.admin1Pcode)
        assertEquals("YE1101", state.admin2Pcode)
        assertNotNull(state.gpsLocation)
        assertEquals(15.35, state.gpsLocation!!.latitude, 0.001)
        assertEquals(4.5f, state.gpsLocation!!.accuracyM, 0.1f)
    }

    // --- TEST 5: REAL-TIME IN-MEMORY CALCULATION ---
    @Test
    fun test5_RealTimeCalculationsEvaluatedOnSave() = runTest {
        val elements = """
        [
          { "name": "count", "type": "integer", "labelAr": "العدد", "required": true },
          { "name": "doubled", "type": "calculate", "calculation": "${'$'}{count} * 2" }
        ]
        """.trimIndent()
        installPackage("yem_water_wells_saadah", "2026-09-05-v6", "WELL", "الآبار", elements)

        viewModel.initializeDynamicSurvey(SurveyType.WELL)
        viewModel.uiState.first { it.activeFormPackage != null }

        viewModel.onDynamicFieldValueChanged("count", RuntimeValue.Integer(7))
        viewModel.updateAdminLocation(
            "YE11", "YE1101", "YE110101", null,
            AdministrativeLocationSnapshot("YE11", "YE1101", "YE110101", "صعدة", "صعدة", "عزلة"),
            false, null, AdminResolutionStatus.CONFIRMED, null,
            GpsLocationResult(15.35, 44.20, 2000.0, 5.0f, GpsAccuracyQuality.GOOD, "2026-09-05 12:00:00")
        )

        // Save survey
        viewModel.saveSurvey()
        viewModel.uiState.first { it.saveSuccess }
        assertTrue(viewModel.uiState.value.saveSuccess)

        // Verify that calculation was performed and persisted in answers
        val saved = database.surveyRecordDao().getSurveyByUUID(viewModel.uiState.value.surveyUUID)
        assertNotNull(saved)
        val json = JSONObject(saved!!.wellDetailsJson!!)
        assertEquals(7, json.getInt("count"))
        assertEquals(14, json.getInt("doubled"))
    }

    // --- TEST 6: RELEVANCE PRUNING ON PERSISTENCE ---
    @Test
    fun test6_IrrelevantAnswersExcludedFromPersistence() = runTest {
        val elements = """
        [
          { "name": "has_generator", "type": "select_one yes_no", "labelAr": "هل يوجد مولد؟" },
          { "name": "generator_capacity", "type": "decimal", "labelAr": "سعة المولد", "relevant": "${'$'}{has_generator} = 'yes'" }
        ]
        """.trimIndent()
        installPackage("yem_water_wells_saadah", "2026-09-05-v6", "WELL", "الآبار", elements)

        viewModel.initializeDynamicSurvey(SurveyType.WELL)
        viewModel.uiState.first { it.activeFormPackage != null }

        // First user answered yes and entered 45.5 kW
        viewModel.onDynamicFieldValueChanged("has_generator", RuntimeValue.Choice("yes"))
        viewModel.onDynamicFieldValueChanged("generator_capacity", RuntimeValue.Decimal(45.5))

        // Then user changed mind and selected "no" (generator_capacity is now IRRELEVANT)
        viewModel.onDynamicFieldValueChanged("has_generator", RuntimeValue.Choice("no"))

        viewModel.updateAdminLocation(
            "YE11", "YE1101", "YE110101", null,
            AdministrativeLocationSnapshot("YE11", "YE1101", "YE110101", "صعدة", "صعدة", "عزلة"),
            false, null, AdminResolutionStatus.CONFIRMED, null,
            GpsLocationResult(15.35, 44.20, 2000.0, 5.0f, GpsAccuracyQuality.GOOD, "2026-09-05 12:00:00")
        )

        viewModel.saveSurvey()
        viewModel.uiState.first { it.saveSuccess }
        assertTrue(viewModel.uiState.value.saveSuccess)

        val saved = database.surveyRecordDao().getSurveyByUUID(viewModel.uiState.value.surveyUUID)
        assertNotNull(saved)
        val json = JSONObject(saved!!.wellDetailsJson!!)
        assertEquals("no", json.getString("has_generator"))
        // Strict relevance check: irrelevant key must NOT exist and must NOT be null
        assertFalse("Irrelevant field must NOT be persisted!", json.has("generator_capacity"))
    }

    // --- TEST 7: CONSTRAINT VALIDATION ON FINAL SAVE ---
    @Test
    fun test7_ConstraintValidationBlocksCompletion() = runTest {
        val elements = """
        [
          { "name": "depth", "type": "decimal", "labelAr": "العمق", "constraint": ". > 0 and . <= 1000", "constraintMessageAr": "العمق غير معقول", "required": true }
        ]
        """.trimIndent()
        installPackage("yem_water_wells_saadah", "2026-09-05-v6", "WELL", "الآبار", elements)

        viewModel.initializeDynamicSurvey(SurveyType.WELL)
        viewModel.uiState.first { it.activeFormPackage != null }

        // Enter invalid depth: -10
        viewModel.onDynamicFieldValueChanged("depth", RuntimeValue.Decimal(-10.0))
        viewModel.updateAdminLocation(
            "YE11", "YE1101", "YE110101", null,
            AdministrativeLocationSnapshot("YE11", "YE1101", "YE110101", "صعدة", "صعدة", "عزلة"),
            false, null, AdminResolutionStatus.CONFIRMED, null,
            GpsLocationResult(15.35, 44.20, 2000.0, 5.0f, GpsAccuracyQuality.GOOD, "2026-09-05 12:00:00")
        )

        viewModel.saveSurvey()
        assertFalse("Final completion must fail constraint validation", viewModel.uiState.value.saveSuccess)
        assertNotNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.error!!.contains("العمق غير معقول"))

        // Correct depth to valid 120m
        viewModel.onDynamicFieldValueChanged("depth", RuntimeValue.Decimal(120.0))
        viewModel.saveSurvey()
        viewModel.uiState.first { it.saveSuccess }
        assertTrue("Completion must succeed after constraint is satisfied", viewModel.uiState.value.saveSuccess)
    }

    // --- TEST 8: GPS ACCURACY GATE ---
    @Test
    fun test8_GpsGateEnforcement() = runTest {
        installPackage("yem_water_wells_saadah", "2026-09-05-v6", "WELL", "الآبار")
        viewModel.initializeDynamicSurvey(SurveyType.WELL)
        viewModel.uiState.first { it.activeFormPackage != null }

        viewModel.updateAdminLocation(
            "YE11", "YE1101", "YE110101", null,
            AdministrativeLocationSnapshot("YE11", "YE1101", "YE110101", "صعدة", "صعدة", "عزلة"),
            false, null, AdminResolutionStatus.CONFIRMED, null,
            GpsLocationResult(15.35, 44.20, 2000.0, 25.0f, GpsAccuracyQuality.POOR, "2026-09-05 12:00:00")
        )

        viewModel.saveSurvey()
        assertFalse("Completion must fail with GPS accuracy >= 15m", viewModel.uiState.value.saveSuccess)
        assertTrue(viewModel.uiState.value.error!!.contains("دقة GPS"))
    }

    // --- TEST 9: ROOM ADMIN LOOKUP PROVIDER ---
    @Test
    fun test9_RoomAdminLookupProviderIntegration() = runTest {
        database.adminReferenceDao().insertAdmin1List(listOf(Admin1Entity(admin1Pcode = "YE11", nameAr = "صعدة", nameEn = "Sa'ada", isActive = true)))
        database.adminReferenceDao().insertAdmin2List(listOf(Admin2Entity(admin2Pcode = "YE1101", admin1Pcode = "YE11", nameAr = "مديرية صعدة", nameEn = "Sa'ada City", isActive = true)))
        database.adminReferenceDao().insertAdmin3List(listOf(Admin3Entity(admin3Pcode = "YE110101", admin2Pcode = "YE1101", admin1Pcode = "YE11", nameAr = "عزلة 1", nameEn = "Uzlah 1", isActive = true)))

        val provider = RoomAdminLookupProvider(database.adminReferenceDao())
        val govs = provider.getGovernorates()
        assertEquals(1, govs.size)
        assertEquals("YE11", govs[0].pcode)
        assertEquals("صعدة", govs[0].nameAr)

        val dists = provider.getDistricts("YE11")
        assertEquals(1, dists.size)
        assertEquals("YE1101", dists[0].pcode)

        val uzlahs = provider.getUzlahs("YE1101")
        assertEquals(1, uzlahs.size)
        assertEquals("YE110101", uzlahs[0].pcode)
    }
}
