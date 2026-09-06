package com.yemen.watersurvey.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yemen.watersurvey.core.admin.AdminCascadingSelector
import com.yemen.watersurvey.core.admin.GpsAdministrativeResolver
import com.yemen.watersurvey.core.expression.DefaultFormExpressionEvaluator
import com.yemen.watersurvey.core.expression.ExpressionEvaluatorEngine
import com.yemen.watersurvey.core.expression.FormExpressionEvaluator
import com.yemen.watersurvey.core.expression.FormExpressionEvaluatorImpl
import com.yemen.watersurvey.core.form.FormPackageManager
import com.yemen.watersurvey.core.sync.FieldPackageManager
import com.yemen.watersurvey.data.database.SurveyAppDatabase
import com.yemen.watersurvey.data.entity.SurveyRecordEntity
import com.yemen.watersurvey.domain.model.*
import com.yemen.watersurvey.presentation.form.AdminLookupProvider
import com.yemen.watersurvey.presentation.form.DynamicFormState
import com.yemen.watersurvey.presentation.form.RoomAdminLookupProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class SurveyFormState(
    val surveyUUID: String = UUID.randomUUID().toString(),
    val recordId: String = "REC-${System.currentTimeMillis()}",
    val surveyType: SurveyType = SurveyType.WELL,
    val admin1Pcode: String = "",
    val admin2Pcode: String = "",
    val admin3Pcode: String = "",
    val villageRefId: String? = null,
    val snapshot: AdministrativeLocationSnapshot? = null,
    val isLocalOverride: Boolean = false,
    val overrideReason: String? = null,
    val resolutionStatus: AdminResolutionStatus = AdminResolutionStatus.NOT_EVALUATED,
    val resolvedLocation: ResolvedAdministrativeLocation? = null,
    val gpsLocation: GpsLocationResult? = null,
    // Well Details
    val wellNameAr: String = "",
    val wellType: String = "",
    val wellDepthM: String = "",
    val pumpingMechanism: String = "",
    val operationalStatus: String = "",
    // Spring Details
    val springNameAr: String = "",
    val flowRateLps: String = "",
    val waterClarity: String = "",
    val dischargeSeasonality: String = "",
    // Dam / Water Harvesting Details
    val damNameAr: String = "",
    val structureType: String = "",
    val storageCapacityM3: String = "",
    val damHeightM: String = "",
    val structuralCondition: String = "",
    // Existing registry/status info (preserved when editing)
    val existingRegistryCode: String = "",
    val existingRevisionCount: Int = 0,
    val existingCreatedAt: String = "",
    val existingFormId: String = "",
    val existingFormVersion: String = "",
    val workflowStatus: String = "NEW",

    // Dynamic Form Engine integration (Phase 14)
    val activeFormPackage: FormPackage? = null,
    val dynamicFormState: DynamicFormState = DynamicFormState(),
    val formId: String = "",
    val formVersion: String = "",
    val schemaVersion: String = "1.0",
    val packageVersion: Int = 1,

    val isSaving: Boolean = false,
    val isLoadingRecord: Boolean = false,
    val saveSuccess: Boolean = false,
    val draftSaveSuccess: Boolean = false,
    val error: String? = null
)

class SurveyViewModel(application: Application) : AndroidViewModel(application) {

    private val database = SurveyAppDatabase.getInstance(application)
    private val surveyDao = database.surveyRecordDao()
    private val sequenceDao = database.deviceSequenceDao()

    val selector = AdminCascadingSelector(database)
    val resolver = GpsAdministrativeResolver(database)
    private val registryCodeGenerator = com.yemen.watersurvey.core.admin.RegistryCodeGenerator(sequenceDao)
    private val profileManager = com.yemen.watersurvey.core.identity.EnumeratorProfileManager(application)
    val packageManager = FieldPackageManager(application)

    // Phase 14: Dynamic Form Runtime Integration
    val formPackageManager = FormPackageManager(application)
    val adminLookupProvider: AdminLookupProvider = RoomAdminLookupProvider(database.adminReferenceDao())
    var expressionEvaluator: FormExpressionEvaluator = DefaultFormExpressionEvaluator

    private val _uiState = MutableStateFlow(SurveyFormState())
    val uiState: StateFlow<SurveyFormState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    // ---- Dynamic Survey Lifecycle (Phase 14) ----

    /**
     * Initializes a new dynamic survey session.
     * Resolves the active Form Package immediately so subsequent save operations see the correct
     * package identity or the legacy fallback state without waiting on a background coroutine.
     */
    fun initializeDynamicSurvey(surveyType: SurveyType) {
        val activePkg = runBlocking { formPackageManager.getActivePackageForSurveyType(surveyType) }

        val uuid = UUID.randomUUID().toString()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        expressionEvaluator = FormExpressionEvaluatorImpl(
            engine = ExpressionEvaluatorEngine(),
            surveyUUID = uuid,
            todayDate = today
        )

        _uiState.update {
            it.copy(
                surveyUUID = uuid,
                recordId = "REC-${System.currentTimeMillis()}",
                surveyType = surveyType,
                formId = activePkg?.formId ?: "",
                formVersion = activePkg?.version ?: "",
                schemaVersion = activePkg?.schemaVersion ?: "1.0",
                packageVersion = activePkg?.packageVersion ?: 1,
                activeFormPackage = activePkg,
                dynamicFormState = DynamicFormState(),
                existingFormId = activePkg?.formId ?: "",
                existingFormVersion = activePkg?.version ?: "",
                isLoadingRecord = false,
                error = null,
                saveSuccess = false,
                draftSaveSuccess = false
            )
        }
    }

    /**
     * Updates dynamic field value, syncing to administrative and GPS bindings if applicable.
     */
    fun onDynamicFieldValueChanged(fieldName: String, value: RuntimeValue?) {
        _uiState.update { current ->
            val updatedFormState = current.dynamicFormState.withValue(fieldName, value)
            var updated = current.copy(dynamicFormState = updatedFormState)

            // Propagate administrative P-codes if matching known administrative fields
            when (fieldName) {
                "gov_pcode", "governorate_code" -> {
                    val pcode = (value as? RuntimeValue.Choice)?.selectedName
                        ?: (value as? RuntimeValue.AdminSelection)?.pcode
                        ?: (value as? RuntimeValue.Text)?.value ?: ""
                    updated = updated.copy(admin1Pcode = pcode)
                }
                "district_code" -> {
                    val pcode = (value as? RuntimeValue.Choice)?.selectedName
                        ?: (value as? RuntimeValue.AdminSelection)?.pcode
                        ?: (value as? RuntimeValue.Text)?.value ?: ""
                    updated = updated.copy(admin2Pcode = pcode)
                }
                "uzlah_code" -> {
                    val pcode = (value as? RuntimeValue.Choice)?.selectedName
                        ?: (value as? RuntimeValue.AdminSelection)?.pcode
                        ?: (value as? RuntimeValue.Text)?.value ?: ""
                    updated = updated.copy(admin3Pcode = pcode)
                }
            }

            // Propagate GPS if geopoint field
            if (value is RuntimeValue.Geopoint) {
                val gps = GpsLocationResult(
                    latitude = value.latitude,
                    longitude = value.longitude,
                    altitudeM = value.altitudeM,
                    accuracyM = value.accuracyM,
                    quality = GpsAccuracyQuality.classify(value.accuracyM),
                    capturedAt = dateFormat.format(Date())
                )
                updated = updated.copy(gpsLocation = gps)
            }

            updated
        }
    }

    /**
     * Captures current GPS fix into a dynamic geopoint field.
     */
    fun onCaptureGpsForDynamicField(fieldName: String) {
        val currentFix = _uiState.value.gpsLocation
        if (currentFix != null) {
            val geopoint = RuntimeValue.Geopoint(
                latitude = currentFix.latitude,
                longitude = currentFix.longitude,
                altitudeM = currentFix.altitudeM ?: 0.0,
                accuracyM = currentFix.accuracyM
            )
            onDynamicFieldValueChanged(fieldName, geopoint)
        }
    }

    /**
     * Attaches captured photo attachment ID into dynamic image field.
     */
    fun onCaptureImageForDynamicField(fieldName: String, attachmentId: String) {
        onDynamicFieldValueChanged(fieldName, RuntimeValue.ImageRef(attachmentId = attachmentId, sha256 = ""))
    }

    // ---- Legacy Field setters ----
    fun updateAdminLocation(
        admin1Pcode: String, admin2Pcode: String, admin3Pcode: String,
        villageRefId: String?, snapshot: AdministrativeLocationSnapshot,
        isOverride: Boolean, overrideReason: String?,
        resolutionStatus: AdminResolutionStatus,
        resolvedLocation: ResolvedAdministrativeLocation?,
        gpsLocation: GpsLocationResult?
    ) {
        _uiState.update {
            it.copy(
                admin1Pcode = admin1Pcode, admin2Pcode = admin2Pcode, admin3Pcode = admin3Pcode,
                villageRefId = villageRefId, snapshot = snapshot,
                isLocalOverride = isOverride, overrideReason = overrideReason,
                resolutionStatus = resolutionStatus, resolvedLocation = resolvedLocation,
                gpsLocation = gpsLocation ?: it.gpsLocation
            )
        }
    }

    fun updateSurveyType(type: SurveyType) {
        _uiState.update { it.copy(surveyType = type) }
        initializeDynamicSurvey(type)
    }

    fun updateWellName(name: String) {
        _uiState.update { it.copy(wellNameAr = name) }
        onDynamicFieldValueChanged("water_facility_name", RuntimeValue.Text(name))
        onDynamicFieldValueChanged("well_name", RuntimeValue.Text(name))
    }
    fun updateWellType(type: String) {
        _uiState.update { it.copy(wellType = type) }
        onDynamicFieldValueChanged("well_type", RuntimeValue.Choice(type))
    }
    fun updateWellDepth(depth: String) {
        _uiState.update { it.copy(wellDepthM = depth) }
        val num = depth.toDoubleOrNull()
        if (num != null) onDynamicFieldValueChanged("well_depth_m", RuntimeValue.Decimal(num))
    }
    fun updatePumpingMechanism(mechanism: String) {
        _uiState.update { it.copy(pumpingMechanism = mechanism) }
        onDynamicFieldValueChanged("pumping_mechanism", RuntimeValue.Choice(mechanism))
    }
    fun updateOperationalStatus(status: String) {
        _uiState.update { it.copy(operationalStatus = status) }
        onDynamicFieldValueChanged("operational_status", RuntimeValue.Choice(status))
    }
    fun updateSpringName(name: String) {
        _uiState.update { it.copy(springNameAr = name) }
        onDynamicFieldValueChanged("water_facility_name", RuntimeValue.Text(name))
        onDynamicFieldValueChanged("spring_name", RuntimeValue.Text(name))
    }
    fun updateFlowRate(rate: String) {
        _uiState.update { it.copy(flowRateLps = rate) }
        val num = rate.toDoubleOrNull()
        if (num != null) onDynamicFieldValueChanged("discharge_lps", RuntimeValue.Decimal(num))
    }
    fun updateWaterClarity(clarity: String) {
        _uiState.update { it.copy(waterClarity = clarity) }
        onDynamicFieldValueChanged("water_clarity", RuntimeValue.Choice(clarity))
    }
    fun updateDischargeSeasonality(seasonality: String) {
        _uiState.update { it.copy(dischargeSeasonality = seasonality) }
        onDynamicFieldValueChanged("discharge_seasonality", RuntimeValue.Choice(seasonality))
    }
    fun updateDamName(name: String) {
        _uiState.update { it.copy(damNameAr = name) }
        onDynamicFieldValueChanged("water_facility_name", RuntimeValue.Text(name))
        onDynamicFieldValueChanged("dam_name", RuntimeValue.Text(name))
    }
    fun updateStructureType(type: String) {
        _uiState.update { it.copy(structureType = type) }
        onDynamicFieldValueChanged("structure_type", RuntimeValue.Choice(type))
    }
    fun updateStorageCapacity(capacity: String) {
        _uiState.update { it.copy(storageCapacityM3 = capacity) }
        val num = capacity.toDoubleOrNull()
        if (num != null) onDynamicFieldValueChanged("storage_capacity_m3", RuntimeValue.Decimal(num))
    }
    fun updateDamHeight(height: String) {
        _uiState.update { it.copy(damHeightM = height) }
        val num = height.toDoubleOrNull()
        if (num != null) onDynamicFieldValueChanged("dam_height_m", RuntimeValue.Decimal(num))
    }
    fun updateStructuralCondition(condition: String) {
        _uiState.update { it.copy(structuralCondition = condition) }
        onDynamicFieldValueChanged("structural_condition", RuntimeValue.Choice(condition))
    }
    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun resetDraftSuccess() { _uiState.update { it.copy(draftSaveSuccess = false) } }

    // ---- Load existing record for editing (restores all fields and exact form identity) ----
    fun loadRecordForEdit(surveyUUID: String) {
        _uiState.update { it.copy(isLoadingRecord = true) }
        viewModelScope.launch {
            val entity = surveyDao.getSurveyByUUID(surveyUUID) ?: run {
                _uiState.update { it.copy(isLoadingRecord = false) }
                return@launch
            }
            val gps = if (entity.latitude != null && entity.longitude != null) {
                GpsLocationResult(
                    latitude = entity.latitude,
                    longitude = entity.longitude,
                    altitudeM = entity.altitudeM ?: 0.0,
                    accuracyM = entity.accuracyM ?: 0.0f,
                    quality = try { GpsAccuracyQuality.valueOf(entity.gpsQuality ?: "GOOD") } catch (_: Exception) { GpsAccuracyQuality.GOOD },
                    provider = entity.gpsProvider ?: "gps",
                    capturedAt = entity.gpsCapturedAt ?: entity.createdAt
                )
            } else null

            val snapshot = AdministrativeLocationSnapshot(
                admin1Pcode = entity.admin1Pcode,
                admin2Pcode = entity.admin2Pcode,
                admin3Pcode = entity.admin3Pcode,
                governorateNameAr = entity.governorateNameSnapshotAr,
                districtNameAr = entity.districtNameSnapshotAr,
                subDistrictNameAr = entity.subDistrictNameSnapshotAr,
                villageNameAr = entity.villageNameSnapshotAr,
                localOverrideId = entity.localOverrideId
            )

            // Resolve exact historical FormPackage
            val pkg = formPackageManager.getPackage(entity.formId, entity.formVersion)
                ?: formPackageManager.getActivePackage(entity.formId)

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            expressionEvaluator = FormExpressionEvaluatorImpl(
                engine = ExpressionEvaluatorEngine(),
                surveyUUID = entity.surveyUUID,
                todayDate = today
            )

            // Parse raw JSON details
            val rawJson = when (entity.surveyType) {
                "WELL" -> entity.wellDetailsJson
                "SPRING" -> entity.springDetailsJson
                "DAM" -> entity.damDetailsJson
                else -> entity.wellDetailsJson ?: entity.springDetailsJson ?: entity.damDetailsJson
            }

            val restoredDynamicValues = if (!rawJson.isNullOrBlank()) {
                deserializeAnswers(rawJson, pkg?.formDefinition)
            } else {
                emptyMap()
            }

            var wellNameAr = ""; var wellType = ""; var wellDepthM = ""; var pumpingMechanism = ""; var operationalStatus = ""
            entity.wellDetailsJson?.let { j ->
                try {
                    val o = JSONObject(j)
                    wellNameAr = o.optString("wellNameAr", o.optString("water_facility_name", o.optString("well_name", "")))
                    wellType = o.optString("wellType", o.optString("well_type", ""))
                    wellDepthM = o.optDouble("wellDepthM", o.optDouble("well_depth_m", 0.0)).let { if (it == 0.0) "" else it.toString() }
                    pumpingMechanism = o.optString("pumpingMechanism", o.optString("pumping_mechanism", ""))
                    operationalStatus = o.optString("operationalStatus", o.optString("operational_status", ""))
                } catch (_: Exception) {}
            }
            var springNameAr = ""; var flowRateLps = ""; var waterClarity = ""; var dischargeSeasonality = ""
            entity.springDetailsJson?.let { j ->
                try {
                    val o = JSONObject(j)
                    springNameAr = o.optString("springNameAr", o.optString("water_facility_name", o.optString("spring_name", "")))
                    flowRateLps = o.optDouble("flowRateLps", o.optDouble("discharge_lps", 0.0)).let { if (it == 0.0) "" else it.toString() }
                    waterClarity = o.optString("waterClarity", o.optString("water_clarity", ""))
                    dischargeSeasonality = o.optString("dischargeSeasonality", o.optString("discharge_seasonality", ""))
                } catch (_: Exception) {}
            }
            var damNameAr = ""; var structureType = ""; var storageCapacityM3 = ""; var damHeightM = ""; var structuralCondition = ""
            entity.damDetailsJson?.let { j ->
                try {
                    val o = JSONObject(j)
                    damNameAr = o.optString("damNameAr", o.optString("water_facility_name", o.optString("dam_name", "")))
                    structureType = o.optString("structureType", o.optString("structure_type", ""))
                    storageCapacityM3 = o.optDouble("storageCapacityM3", o.optDouble("storage_capacity_m3", 0.0)).let { if (it == 0.0) "" else it.toString() }
                    damHeightM = o.optDouble("damHeightM", o.optDouble("dam_height_m", 0.0)).let { if (it == 0.0) "" else it.toString() }
                    structuralCondition = o.optString("structuralCondition", o.optString("structural_condition", ""))
                } catch (_: Exception) {}
            }

            val surveyType = try { SurveyType.valueOf(entity.surveyType) } catch (_: Exception) { SurveyType.WELL }

            _uiState.update {
                SurveyFormState(
                    surveyUUID = entity.surveyUUID,
                    recordId = entity.recordId,
                    surveyType = surveyType,
                    admin1Pcode = entity.admin1Pcode,
                    admin2Pcode = entity.admin2Pcode,
                    admin3Pcode = entity.admin3Pcode,
                    villageRefId = entity.villageReferenceId,
                    snapshot = snapshot,
                    isLocalOverride = entity.isLocalNameOverride,
                    resolutionStatus = try { AdminResolutionStatus.valueOf(entity.gpsResolutionStatus) } catch (_: Exception) { AdminResolutionStatus.NOT_EVALUATED },
                    gpsLocation = gps,
                    wellNameAr = wellNameAr, wellType = wellType, wellDepthM = wellDepthM,
                    pumpingMechanism = pumpingMechanism, operationalStatus = operationalStatus,
                    springNameAr = springNameAr, flowRateLps = flowRateLps,
                    waterClarity = waterClarity, dischargeSeasonality = dischargeSeasonality,
                    damNameAr = damNameAr, structureType = structureType,
                    storageCapacityM3 = storageCapacityM3, damHeightM = damHeightM,
                    structuralCondition = structuralCondition,
                    existingRegistryCode = entity.registryCode,
                    existingRevisionCount = entity.revisionCount,
                    existingCreatedAt = entity.createdAt,
                    existingFormId = entity.formId,
                    existingFormVersion = entity.formVersion,
                    workflowStatus = entity.workflowStatus,
                    activeFormPackage = pkg,
                    dynamicFormState = DynamicFormState(values = restoredDynamicValues),
                    formId = entity.formId,
                    formVersion = entity.formVersion,
                    schemaVersion = pkg?.schemaVersion ?: "1.0",
                    packageVersion = pkg?.packageVersion ?: 1,
                    isLoadingRecord = false
                )
            }
        }
    }

    // ---- Resolve authoritative form package identity ----
    private suspend fun resolveFormPackageIdentity(
        surveyType: SurveyType,
        existingFormId: String,
        existingFormVersion: String,
        activePackage: FormPackage? = null
    ): Pair<String, String> {
        // C4 Lifecycle Protection: For an existing survey, always preserve stored historical form identity
        if (existingFormId.isNotBlank()) {
            return Pair(existingFormId, existingFormVersion.ifBlank { "1.0" })
        }
        if (activePackage != null) {
            return Pair(activePackage.formId, activePackage.version)
        }

        val activePkg = formPackageManager.getActivePackageForSurveyType(surveyType)
        if (activePkg != null) {
            return Pair(activePkg.formId, activePkg.version)
        }

        // Legacy compatibility fallback for older MVP tests:
        val targetFormId = when (surveyType) {
            SurveyType.WELL -> "form-well-standard"
            SurveyType.SPRING -> "form-spring-standard"
            SurveyType.DAM -> "form-dam-standard"
        }
        val legacyPkg = database.formPackageDao().getActivePackageForForm(targetFormId)
        if (legacyPkg != null) {
            return Pair(legacyPkg.formId, legacyPkg.version)
        }

        return Pair("WATER_SURVEY_V1", "1.0")
    }

    // ---- Save as Draft (NO GPS gate, NO required-field validation, in-memory calculations evaluated) ----
    fun saveAsDraft() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val now = dateFormat.format(Date())
                val enumeratorCode = profileManager.getOrCreateEnumeratorCode()
                val (formId, formVersion) = resolveFormPackageIdentity(
                    state.surveyType, state.existingFormId, state.existingFormVersion, state.activeFormPackage
                )

                // Evaluate calculations and prune irrelevant answers for draft
                val (relevantAnswers, _) = prepareDynamicAnswers(
                    pkg = state.activeFormPackage,
                    state = state,
                    isFinalSave = false
                )

                val entity = buildEntity(
                    state = state,
                    now = now,
                    enumeratorCode = enumeratorCode,
                    workflowStatus = "DRAFT",
                    formId = formId,
                    formVersion = formVersion,
                    incrementRevision = false,
                    dynamicAnswers = relevantAnswers
                )
                surveyDao.insertOrUpdateSurvey(entity)
                android.util.Log.d("SurveyDraft", "Draft saved: UUID=${state.surveyUUID}, type=${state.surveyType}, form=$formId@$formVersion")
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        draftSaveSuccess = true,
                        dynamicFormState = it.dynamicFormState.withValues(relevantAnswers)
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "فشل حفظ المسودة: ${e.message}") }
            }
        }
    }

    // ---- Final Save / Complete (GPS gate + dynamic relevance/calculations/constraints validation) ----
    fun saveSurvey() {
        val state = _uiState.value

        // 1. Mandatory GPS Gate (<15m accuracy)
        if (state.gpsLocation == null) {
            _uiState.update { it.copy(error = "يجب التقاط إحداثيات GPS للمتابعة.") }
            return
        }
        if (state.gpsLocation.accuracyM >= 15.0f) {
            _uiState.update { it.copy(error = "دقة GPS (${state.gpsLocation.accuracyM}م) غير كافية. يجب أن تكون أقل من 15 متر.") }
            return
        }

        // 2. Dynamic questionnaire validation (calculations, relevance pruning, constraints, required fields)
        val (relevantAnswers, validationError) = prepareDynamicAnswers(
            pkg = state.activeFormPackage,
            state = state,
            isFinalSave = true
        )
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }

        // 3. Fallback validation for non-dynamic legacy surveys
        if (state.activeFormPackage == null) {
            when (state.surveyType) {
                SurveyType.WELL -> if (state.wellNameAr.isBlank() || state.wellType.isBlank() || state.wellDepthM.isBlank()) {
                    _uiState.update { it.copy(error = "يرجى إكمال جميع حقول بيانات البئر الأساسية.") }; return
                }
                SurveyType.SPRING -> if (state.springNameAr.isBlank() || state.flowRateLps.isBlank()) {
                    _uiState.update { it.copy(error = "يرجى إكمال اسم العين ومعدل التدفق.") }; return
                }
                SurveyType.DAM -> if (state.damNameAr.isBlank() || state.structureType.isBlank()) {
                    _uiState.update { it.copy(error = "يرجى إكمال اسم السد/الحاجز ونوع المنشأة.") }; return
                }
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val now = dateFormat.format(Date())
                val enumeratorCode = profileManager.getOrCreateEnumeratorCode()
                val registryResult = registryCodeGenerator.generateRegistryCode(
                    admin1Pcode = state.admin1Pcode, admin2Pcode = state.admin2Pcode,
                    admin3Pcode = state.admin3Pcode, surveyType = state.surveyType, surveyUUID = state.surveyUUID
                )
                val finalRegistryCode = if (state.existingRegistryCode.isNotBlank()) state.existingRegistryCode
                    else registryResult.registryCode

                val (formId, formVersion) = resolveFormPackageIdentity(
                    state.surveyType, state.existingFormId, state.existingFormVersion, state.activeFormPackage
                )

                val entity = buildEntity(
                    state = state,
                    now = now,
                    enumeratorCode = enumeratorCode,
                    workflowStatus = "COMPLETED",
                    formId = formId,
                    formVersion = formVersion,
                    registryCode = finalRegistryCode,
                    isRegistryPending = if (state.existingRegistryCode.isNotBlank()) false else registryResult.isPending,
                    dynamicAnswers = relevantAnswers
                )

                if (state.existingRevisionCount > 0) {
                    val revision = com.yemen.watersurvey.data.entity.SurveyRevisionEntity(
                        revisionId = UUID.randomUUID().toString(),
                        surveyUUID = state.surveyUUID,
                        recordId = state.recordId,
                        revisionNumber = entity.revisionCount,
                        modifiedBy = enumeratorCode,
                        modifiedAt = now,
                        reasonForChange = "Survey updated by enumerator",
                        previousStatus = state.workflowStatus,
                        newStatus = "COMPLETED",
                        changedFieldsJson = "[]",
                        snapshotDataJson = JSONObject().apply {
                            put("surveyType", state.surveyType.name)
                            put("registryCode", finalRegistryCode)
                            put("admin1Pcode", state.admin1Pcode)
                            put("admin2Pcode", state.admin2Pcode)
                            put("admin3Pcode", state.admin3Pcode)
                            put("gpsAccuracyM", state.gpsLocation?.accuracyM ?: 0f)
                        }.toString()
                    )
                    database.surveyRevisionDao().insertRevision(revision)
                }

                surveyDao.insertOrUpdateSurvey(entity)
                android.util.Log.d("SurveySave", "Saved: UUID=${state.surveyUUID}, Registry=$finalRegistryCode, Type=${state.surveyType}, Form=$formId@$formVersion")
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveSuccess = true,
                        dynamicFormState = it.dynamicFormState.withValues(relevantAnswers)
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "فشل حفظ الاستمارة: ${e.message}") }
            }
        }
    }

    // ---- In-Memory Calculation, Relevance Pruning, and Constraint Verification ----
    private fun prepareDynamicAnswers(
        pkg: FormPackage?,
        state: SurveyFormState,
        isFinalSave: Boolean
    ): Pair<Map<String, RuntimeValue>, String?> {
        val formDef = pkg?.formDefinition ?: return Pair(emptyMap(), null)
        var currentValues = state.dynamicFormState.values

        // 1. Evaluate calculations in-memory
        val evalContextForCalc = state.dynamicFormState.toExpressionContext(
            surveyUUID = state.surveyUUID,
            todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        )
        for (element in formDef.allElements) {
            if (element is CalculateElement) {
                val calcVal = expressionEvaluator.evaluateCalculation(element, evalContextForCalc)
                if (calcVal != null) {
                    currentValues = currentValues + (element.name to calcVal)
                }
            }
        }

        // 2. Evaluate relevance and prune irrelevant values completely
        val evalContextForRelevance = DynamicFormState(values = currentValues).toExpressionContext(
            surveyUUID = state.surveyUUID,
            todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        )
        val relevantAnswers = mutableMapOf<String, RuntimeValue>()

        for (element in formDef.allElements) {
            val isRelevant = expressionEvaluator.isElementRelevant(element, evalContextForRelevance)
            if (isRelevant) {
                val value = currentValues[element.name]
                if (value != null) {
                    relevantAnswers[element.name] = value
                }
            }
            // Irrelevant values are omitted entirely from relevantAnswers!
        }

        // 3. Final validation gates for relevant questions
        if (isFinalSave) {
            for (element in formDef.allElements) {
                val isRelevant = expressionEvaluator.isElementRelevant(element, evalContextForRelevance)
                if (isRelevant && element is QuestionElement) {
                    val value = relevantAnswers[element.name]
                    if (element.isRequired) {
                        val isEmpty = value == null ||
                                (value is RuntimeValue.Text && value.value.isBlank()) ||
                                (value is RuntimeValue.MultipleChoice && value.selectedNames.isEmpty())
                        if (isEmpty) {
                            val label = element.labelAr.ifBlank { element.name }
                            return Pair(relevantAnswers, "الحقل المطلوب لم يتم إدخاله: $label")
                        }
                    }

                    if (element.constraintExpr != null && value != null) {
                        val constraintError = expressionEvaluator.validateConstraint(element, value, evalContextForRelevance)
                        if (constraintError != null) {
                            return Pair(relevantAnswers, constraintError)
                        }
                    }
                }
            }
        }

        return Pair(relevantAnswers, null)
    }

    // ---- Package a completed record ----
    fun packageRecord(surveyUUID: String, onResult: (success: Boolean, packageId: String) -> Unit) {
        viewModelScope.launch {
            try {
                val entity = surveyDao.getSurveyByUUID(surveyUUID) ?: run {
                    onResult(false, ""); return@launch
                }
                if (entity.workflowStatus !in listOf("COMPLETED", "DRAFT")) {
                    onResult(false, ""); return@launch
                }
                val now = dateFormat.format(Date())
                val enumeratorCode = profileManager.getOrCreateEnumeratorCode()
                val deviceId = try {
                    android.provider.Settings.Secure.getString(
                        getApplication<Application>().contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID
                    ) ?: "UNKNOWN"
                } catch (_: Exception) { "UNKNOWN" }

                val attachments = database.surveyAttachmentDao().getAttachmentsForSurvey(surveyUUID)
                val locationSummary = listOfNotNull(
                    entity.governorateNameSnapshotAr.ifBlank { null },
                    entity.districtNameSnapshotAr.ifBlank { null },
                    entity.subDistrictNameSnapshotAr.ifBlank { null }
                ).joinToString(" - ")

                val pkg = packageManager.createPackage(
                    enumeratorCode = enumeratorCode,
                    deviceId = deviceId,
                    adminLocationSummary = locationSummary
                )
                packageManager.addSurveyToPackage(
                    packageId = pkg.packageId,
                    surveyUUID = surveyUUID,
                    surveyType = entity.surveyType,
                    attachmentCount = attachments.size,
                    adminLocationSummary = locationSummary
                )
                surveyDao.updateWorkflowStatus(surveyUUID, "PACKAGED", now)
                onResult(true, pkg.packageId)
            } catch (e: Exception) {
                onResult(false, "")
            }
        }
    }

    // ---- Serialization and Deserialization Helpers ----

    fun serializeAnswers(values: Map<String, RuntimeValue>): String {
        val json = JSONObject()
        for ((k, v) in values) {
            when (v) {
                is RuntimeValue.Text -> json.put(k, v.value)
                is RuntimeValue.Integer -> json.put(k, v.value)
                is RuntimeValue.Decimal -> json.put(k, v.value)
                is RuntimeValue.Date -> json.put(k, v.isoDate)
                is RuntimeValue.Choice -> json.put(k, v.selectedName)
                is RuntimeValue.MultipleChoice -> {
                    val arr = JSONArray()
                    v.selectedNames.forEach { arr.put(it) }
                    json.put(k, arr)
                }
                is RuntimeValue.AdminSelection -> {
                    json.put(k, JSONObject().apply {
                        put("pcode", v.pcode)
                        put("tier", v.tier.name)
                    })
                }
                is RuntimeValue.Geopoint -> {
                    json.put(k, JSONObject().apply {
                        put("latitude", v.latitude)
                        put("longitude", v.longitude)
                        put("altitudeM", v.altitudeM)
                        put("accuracyM", v.accuracyM.toDouble())
                    })
                }
                is RuntimeValue.ImageRef -> {
                    json.put(k, JSONObject().apply {
                        put("attachmentId", v.attachmentId)
                        put("sha256", v.sha256)
                    })
                }
            }
        }
        return json.toString()
    }

    fun deserializeAnswers(jsonStr: String, formDef: FormDefinition?): Map<String, RuntimeValue> {
        val map = mutableMapOf<String, RuntimeValue>()
        try {
            val json = JSONObject(jsonStr)
            val elementsByName = formDef?.allElements?.associateBy { it.name } ?: emptyMap()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val elem = elementsByName[key]
                val obj = json.get(key)
                if (obj == null || obj == JSONObject.NULL) continue

                val rv: RuntimeValue? = when {
                    elem is QuestionElement -> when (elem.dataType) {
                        QuestionDataType.TEXT -> RuntimeValue.Text(obj.toString())
                        QuestionDataType.INTEGER -> (obj as? Number)?.toLong()?.let { RuntimeValue.Integer(it) } ?: RuntimeValue.Integer(obj.toString().toLongOrNull() ?: 0L)
                        QuestionDataType.DECIMAL -> (obj as? Number)?.toDouble()?.let { RuntimeValue.Decimal(it) } ?: RuntimeValue.Decimal(obj.toString().toDoubleOrNull() ?: 0.0)
                        QuestionDataType.DATE -> RuntimeValue.Date(obj.toString())
                        QuestionDataType.SELECT_ONE -> RuntimeValue.Choice(obj.toString())
                        QuestionDataType.SELECT_MULTIPLE -> {
                            val arr = obj as? JSONArray
                            if (arr != null) {
                                val set = (0 until arr.length()).map { arr.getString(it) }.toSet()
                                RuntimeValue.MultipleChoice(set)
                            } else {
                                RuntimeValue.MultipleChoice(setOf(obj.toString()))
                            }
                        }
                        QuestionDataType.ADMIN_SELECT -> {
                            val subObj = obj as? JSONObject
                            if (subObj != null) {
                                RuntimeValue.AdminSelection(
                                    pcode = subObj.optString("pcode", ""),
                                    tier = elem.adminBinding?.tier ?: AdminTier.GOVERNORATE
                                )
                            } else {
                                RuntimeValue.AdminSelection(pcode = obj.toString(), tier = elem.adminBinding?.tier ?: AdminTier.GOVERNORATE)
                            }
                        }
                        QuestionDataType.GEOPOINT -> {
                            val subObj = obj as? JSONObject
                            if (subObj != null) {
                                RuntimeValue.Geopoint(
                                    latitude = subObj.optDouble("latitude", 0.0),
                                    longitude = subObj.optDouble("longitude", 0.0),
                                    altitudeM = subObj.optDouble("altitudeM", subObj.optDouble("altitude", 0.0)),
                                    accuracyM = subObj.optDouble("accuracyM", subObj.optDouble("accuracy", 0.0)).toFloat()
                                )
                            } else null
                        }
                        QuestionDataType.IMAGE -> {
                            val subObj = obj as? JSONObject
                            if (subObj != null) {
                                RuntimeValue.ImageRef(
                                    attachmentId = subObj.optString("attachmentId", ""),
                                    sha256 = subObj.optString("sha256", "")
                                )
                            } else {
                                RuntimeValue.ImageRef(
                                    attachmentId = obj.toString(),
                                    sha256 = ""
                                )
                            }
                        }
                    }
                    obj is String -> RuntimeValue.Text(obj)
                    obj is Number -> if (obj is Double || obj is Float) RuntimeValue.Decimal(obj.toDouble()) else RuntimeValue.Integer(obj.toLong())
                    obj is Boolean -> RuntimeValue.Choice(obj.toString())
                    obj is JSONArray -> {
                        val set = (0 until obj.length()).map { obj.getString(it) }.toSet()
                        RuntimeValue.MultipleChoice(set)
                    }
                    else -> null
                }
                if (rv != null) {
                    map[key] = rv
                }
            }
        } catch (e: Exception) {
            // ignore malformed JSON
        }
        return map
    }

    // ---- Build SurveyRecordEntity from state ----
    private fun buildEntity(
        state: SurveyFormState,
        now: String,
        enumeratorCode: String,
        workflowStatus: String,
        formId: String = state.existingFormId.ifBlank { "WATER_SURVEY_V1" },
        formVersion: String = state.existingFormVersion.ifBlank { "1.0" },
        registryCode: String = state.existingRegistryCode,
        isRegistryPending: Boolean = registryCode.isBlank(),
        incrementRevision: Boolean = true,
        dynamicAnswers: Map<String, RuntimeValue> = emptyMap()
    ): SurveyRecordEntity {
        // Construct serialized dynamic JSON
        val dynamicAnswersJson = if (dynamicAnswers.isNotEmpty()) {
            serializeAnswers(dynamicAnswers)
        } else null

        // Extract legacy compatibility values from dynamic answers if available
        val wellName = (dynamicAnswers["water_facility_name"] as? RuntimeValue.Text)?.value
            ?: (dynamicAnswers["well_name"] as? RuntimeValue.Text)?.value
            ?: state.wellNameAr
        val wellType = (dynamicAnswers["well_type"] as? RuntimeValue.Choice)?.selectedName ?: state.wellType
        val wellDepth = (dynamicAnswers["well_depth_m"] as? RuntimeValue.Decimal)?.value
            ?: (dynamicAnswers["well_depth"] as? RuntimeValue.Decimal)?.value
            ?: state.wellDepthM.toDoubleOrNull() ?: 0.0
        val pumpingMech = (dynamicAnswers["pumping_mechanism"] as? RuntimeValue.Choice)?.selectedName ?: state.pumpingMechanism
        val opStatus = (dynamicAnswers["operational_status"] as? RuntimeValue.Choice)?.selectedName ?: state.operationalStatus

        val springName = (dynamicAnswers["water_facility_name"] as? RuntimeValue.Text)?.value
            ?: (dynamicAnswers["spring_name"] as? RuntimeValue.Text)?.value
            ?: state.springNameAr
        val flowRate = (dynamicAnswers["discharge_lps"] as? RuntimeValue.Decimal)?.value
            ?: (dynamicAnswers["flowRateLps"] as? RuntimeValue.Decimal)?.value
            ?: state.flowRateLps.toDoubleOrNull() ?: 0.0
        val waterClar = (dynamicAnswers["water_clarity"] as? RuntimeValue.Choice)?.selectedName ?: state.waterClarity
        val seasonality = (dynamicAnswers["discharge_seasonality"] as? RuntimeValue.Choice)?.selectedName ?: state.dischargeSeasonality

        val damName = (dynamicAnswers["water_facility_name"] as? RuntimeValue.Text)?.value
            ?: (dynamicAnswers["dam_name"] as? RuntimeValue.Text)?.value
            ?: state.damNameAr
        val structType = (dynamicAnswers["structure_type"] as? RuntimeValue.Choice)?.selectedName ?: state.structureType
        val storageCap = (dynamicAnswers["storage_capacity_m3"] as? RuntimeValue.Decimal)?.value
            ?: state.storageCapacityM3.toDoubleOrNull() ?: 0.0
        val damHeight = (dynamicAnswers["dam_height_m"] as? RuntimeValue.Decimal)?.value
            ?: state.damHeightM.toDoubleOrNull() ?: 0.0
        val structCond = (dynamicAnswers["structural_condition"] as? RuntimeValue.Choice)?.selectedName ?: state.structuralCondition

        val wellDetailsJson = if (state.surveyType == SurveyType.WELL) {
            dynamicAnswersJson ?: JSONObject().apply {
                put("wellNameAr", wellName); put("wellType", wellType)
                put("wellDepthM", wellDepth)
                put("pumpingMechanism", pumpingMech); put("operationalStatus", opStatus)
            }.toString()
        } else null

        val springDetailsJson = if (state.surveyType == SurveyType.SPRING) {
            dynamicAnswersJson ?: JSONObject().apply {
                put("springNameAr", springName)
                put("flowRateLps", flowRate)
                put("waterClarity", waterClar); put("dischargeSeasonality", seasonality)
            }.toString()
        } else null

        val damDetailsJson = if (state.surveyType == SurveyType.DAM) {
            dynamicAnswersJson ?: JSONObject().apply {
                put("damNameAr", damName); put("structureType", structType)
                put("storageCapacityM3", storageCap)
                put("damHeightM", damHeight)
                put("structuralCondition", structCond)
            }.toString()
        } else null

        return SurveyRecordEntity(
            surveyUUID = state.surveyUUID,
            recordId = state.recordId,
            registryCode = registryCode,
            isRegistryCodePending = isRegistryPending,
            enumeratorCode = enumeratorCode,
            formId = formId,
            formVersion = formVersion,
            enumeratorId = enumeratorCode,
            enumeratorUsername = enumeratorCode,
            surveyType = state.surveyType.name,
            admin1Pcode = state.admin1Pcode,
            admin2Pcode = state.admin2Pcode,
            admin3Pcode = state.admin3Pcode,
            villageReferenceId = state.villageRefId,
            governorateNameSnapshotAr = state.snapshot?.governorateNameAr ?: "",
            districtNameSnapshotAr = state.snapshot?.districtNameAr ?: "",
            subDistrictNameSnapshotAr = state.snapshot?.subDistrictNameAr ?: "",
            villageNameSnapshotAr = state.snapshot?.villageNameAr,
            isLocalNameOverride = state.isLocalOverride,
            localOverrideId = state.snapshot?.localOverrideId,
            workflowStatus = workflowStatus,
            revisionCount = if (incrementRevision && state.existingRevisionCount > 0) state.existingRevisionCount + 1
                            else if (state.existingRevisionCount > 0) state.existingRevisionCount
                            else 1,
            createdAt = state.existingCreatedAt.ifBlank { now },
            updatedAt = now,
            latitude = state.gpsLocation?.latitude,
            longitude = state.gpsLocation?.longitude,
            altitudeM = state.gpsLocation?.altitudeM,
            accuracyM = state.gpsLocation?.accuracyM,
            gpsQuality = state.gpsLocation?.quality?.name,
            gpsCapturedAt = state.gpsLocation?.capturedAt,
            gpsProvider = state.gpsLocation?.provider,
            gpsResolutionStatus = state.resolutionStatus.name,
            gpsResolvedAdmin1Pcode = state.resolvedLocation?.admin1Pcode,
            gpsResolvedAdmin2Pcode = state.resolvedLocation?.admin2Pcode,
            gpsResolvedAdmin3Pcode = state.resolvedLocation?.admin3Pcode,
            gpsDistanceToNearestVillageM = state.resolvedLocation?.distanceToNearestVillageM,
            gpsNearestVillageNameAr = state.resolvedLocation?.nearestVillageNameAr,
            wellDetailsJson = wellDetailsJson,
            springDetailsJson = springDetailsJson,
            damDetailsJson = damDetailsJson
        )
    }
}
