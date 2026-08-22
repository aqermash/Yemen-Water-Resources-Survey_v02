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
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

class SurveyViewModel(application: Application) : AndroidViewModel(application) {

    private val database = SurveyAppDatabase.getInstance(application)
    private val surveyDao = database.surveyRecordDao()
    
    val selector = AdminCascadingSelector(database)
    val resolver = GpsAdministrativeResolver(database)

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

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val now = dateFormat.format(Date())
                val uuid = UUID.randomUUID().toString()
                val recordId = "REC-${System.currentTimeMillis()}"
                
                val entity = SurveyRecordEntity(
                    surveyUUID = uuid,
                    recordId = recordId,
                    enumeratorId = "usr-001", // Placeholder
                    enumeratorUsername = "enumerator_1", // Placeholder
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
                    gpsNearestVillageNameAr = state.resolvedLocation?.nearestVillageNameAr
                )
                
                surveyDao.insertSurvey(entity)
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "فشل حفظ الاستمارة: ${e.message}") }
            }
        }
    }
}
