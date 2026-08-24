package com.yemen.watersurvey.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yemen.watersurvey.core.admin.AdminCascadingSelector
import com.yemen.watersurvey.core.admin.GpsAdministrativeResolver
import com.yemen.watersurvey.data.database.SurveyAppDatabase
import com.yemen.watersurvey.data.entity.SurveyRecordEntity
import com.yemen.watersurvey.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class SurveyFormState(
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
    
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
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

    private val _uiState = MutableStateFlow(SurveyFormState())
    val uiState: StateFlow<SurveyFormState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun updateAdminLocation(
        admin1Pcode: String,
        admin2Pcode: String,
        admin3Pcode: String,
        villageRefId: String?,
        snapshot: AdministrativeLocationSnapshot,
        isOverride: Boolean,
        overrideReason: String?,
        resolutionStatus: AdminResolutionStatus,
        resolvedLocation: ResolvedAdministrativeLocation?,
        gpsLocation: GpsLocationResult?
    ) {
        _uiState.update {
            it.copy(
                admin1Pcode = admin1Pcode,
                admin2Pcode = admin2Pcode,
                admin3Pcode = admin3Pcode,
                villageRefId = villageRefId,
                snapshot = snapshot,
                isLocalOverride = isOverride,
                overrideReason = overrideReason,
                resolutionStatus = resolutionStatus,
                resolvedLocation = resolvedLocation,
                gpsLocation = gpsLocation
            )
        }
    }

    fun updateSurveyType(type: SurveyType) {
        _uiState.update { it.copy(surveyType = type) }
    }

    // Well setters
    fun updateWellName(name: String) {
        _uiState.update { it.copy(wellNameAr = name) }
    }

    fun updateWellType(type: String) {
        _uiState.update { it.copy(wellType = type) }
    }

    fun updateWellDepth(depth: String) {
        _uiState.update { it.copy(wellDepthM = depth) }
    }

    fun updatePumpingMechanism(mechanism: String) {
        _uiState.update { it.copy(pumpingMechanism = mechanism) }
    }

    fun updateOperationalStatus(status: String) {
        _uiState.update { it.copy(operationalStatus = status) }
    }

    // Spring setters
    fun updateSpringName(name: String) {
        _uiState.update { it.copy(springNameAr = name) }
    }

    fun updateFlowRate(rate: String) {
        _uiState.update { it.copy(flowRateLps = rate) }
    }

    fun updateWaterClarity(clarity: String) {
        _uiState.update { it.copy(waterClarity = clarity) }
    }

    fun updateDischargeSeasonality(seasonality: String) {
        _uiState.update { it.copy(dischargeSeasonality = seasonality) }
    }

    // Dam setters
    fun updateDamName(name: String) {
        _uiState.update { it.copy(damNameAr = name) }
    }

    fun updateStructureType(type: String) {
        _uiState.update { it.copy(structureType = type) }
    }

    fun updateStorageCapacity(capacity: String) {
        _uiState.update { it.copy(storageCapacityM3 = capacity) }
    }

    fun updateDamHeight(height: String) {
        _uiState.update { it.copy(damHeightM = height) }
    }

    fun updateStructuralCondition(condition: String) {
        _uiState.update { it.copy(structuralCondition = condition) }
    }

    fun saveSurvey() {
        val state = _uiState.value
        
        // STRICT ACCURACY GATE: accuracy must be < 15m
        if (state.gpsLocation == null) {
            _uiState.update { it.copy(error = "يجب التقاط إحداثيات GPS للمتابعة.") }
            return
        }
        
        if (state.gpsLocation.accuracyM >= 15.0f) {
            _uiState.update { it.copy(error = "دقة GPS (${state.gpsLocation.accuracyM}م) غير كافية. يجب أن تكون أقل من 15 متر.") }
            return
        }

        // Validate Specific Details
        when (state.surveyType) {
            SurveyType.WELL -> {
                if (state.wellNameAr.isBlank() || state.wellType.isBlank() || state.wellDepthM.isBlank()) {
                    _uiState.update { it.copy(error = "يرجى إكمال جميع حقول بيانات البئر الأساسية.") }
                    return
                }
            }
            SurveyType.SPRING -> {
                if (state.springNameAr.isBlank() || state.flowRateLps.isBlank()) {
                    _uiState.update { it.copy(error = "يرجى إكمال اسم العين ومعدل التدفق.") }
                    return
                }
            }
            SurveyType.DAM -> {
                if (state.damNameAr.isBlank() || state.structureType.isBlank()) {
                    _uiState.update { it.copy(error = "يرجى إكمال اسم السد/الحاجز ونوع المنشأة.") }
                    return
                }
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val now = dateFormat.format(Date())
                val uuid = UUID.randomUUID().toString()
                val recordId = "REC-${System.currentTimeMillis()}"
                
                val enumeratorCode = profileManager.getOrCreateEnumeratorCode()

                val registryResult = registryCodeGenerator.generateRegistryCode(
                    admin1Pcode = state.admin1Pcode,
                    admin2Pcode = state.admin2Pcode,
                    admin3Pcode = state.admin3Pcode,
                    surveyType = state.surveyType,
                    surveyUUID = uuid
                )

                val wellDetailsJson = if (state.surveyType == SurveyType.WELL) {
                    val json = org.json.JSONObject()
                    json.put("wellNameAr", state.wellNameAr)
                    json.put("wellType", state.wellType)
                    json.put("wellDepthM", state.wellDepthM.toDoubleOrNull() ?: 0.0)
                    json.put("pumpingMechanism", state.pumpingMechanism)
                    json.put("operationalStatus", state.operationalStatus)
                    json.toString()
                } else null

                val springDetailsJson = if (state.surveyType == SurveyType.SPRING) {
                    val json = org.json.JSONObject()
                    json.put("springNameAr", state.springNameAr)
                    json.put("flowRateLps", state.flowRateLps.toDoubleOrNull() ?: 0.0)
                    json.put("waterClarity", state.waterClarity)
                    json.put("dischargeSeasonality", state.dischargeSeasonality)
                    json.toString()
                } else null

                val damDetailsJson = if (state.surveyType == SurveyType.DAM) {
                    val json = org.json.JSONObject()
                    json.put("damNameAr", state.damNameAr)
                    json.put("structureType", state.structureType)
                    json.put("storageCapacityM3", state.storageCapacityM3.toDoubleOrNull() ?: 0.0)
                    json.put("damHeightM", state.damHeightM.toDoubleOrNull() ?: 0.0)
                    json.put("structuralCondition", state.structuralCondition)
                    json.toString()
                } else null

                val entity = SurveyRecordEntity(
                    surveyUUID = uuid,
                    recordId = recordId,
                    registryCode = registryResult.registryCode,
                    isRegistryCodePending = registryResult.isPending,
                    enumeratorCode = enumeratorCode,
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
                    workflowStatus = "COMPLETED",
                    revisionCount = 1,
                    createdAt = now,
                    updatedAt = now,
                    latitude = state.gpsLocation.latitude,
                    longitude = state.gpsLocation.longitude,
                    altitudeM = state.gpsLocation.altitudeM,
                    accuracyM = state.gpsLocation.accuracyM,
                    gpsQuality = state.gpsLocation.quality.name,
                    gpsCapturedAt = state.gpsLocation.capturedAt,
                    gpsProvider = state.gpsLocation.provider,
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
                
                surveyDao.insertSurvey(entity)
                
                // Debug log as requested for verification evidence
                android.util.Log.d("SurveySave", "Saved Survey: UUID=$uuid, RegistryCode=${registryResult.registryCode}, Type=${state.surveyType}, Lat=${state.gpsLocation.latitude}, Lon=${state.gpsLocation.longitude}, Accuracy=${state.gpsLocation.accuracyM}")

                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "فشل حفظ الاستمارة: ${e.message}") }
            }
        }
    }
}
