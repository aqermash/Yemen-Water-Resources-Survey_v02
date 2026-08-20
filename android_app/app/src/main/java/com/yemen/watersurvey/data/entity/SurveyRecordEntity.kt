package com.yemen.watersurvey.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room database entity storing survey records locally.
 * Indexed by the primary global identifier `surveyUUID`.
 */
@Entity(
    tableName = "survey_records",
    indices = [
        Index(value = ["surveyUUID"], unique = true),
        Index(value = ["recordId"]),
        Index(value = ["surveyType"]),
        Index(value = ["admin1Pcode", "admin2Pcode"]),
        Index(value = ["governorateCode", "districtCode"]),
        Index(value = ["workflowStatus"]),
        Index(value = ["gpsResolutionStatus"])
    ]
)
data class SurveyRecordEntity(
    @PrimaryKey
    val surveyUUID: String,
    val recordId: String,
    val registryCode: String = "",
    val isRegistryCodePending: Boolean = false,
    val enumeratorCode: String = "",
    val formId: String = "WATER_SURVEY_V1",
    val formVersion: String = "1.0",
    val surveyType: String,
    val admin1Pcode: String = "YE11",
    val admin2Pcode: String = "YE1101",
    val admin3Pcode: String = "YE110101",
    val villageReferenceId: String? = null,
    val governorateCode: String = admin1Pcode,
    val districtCode: String = admin2Pcode,
    val uzlahCode: String = admin3Pcode,
    val villageCode: String = villageReferenceId ?: "",
    val governorateNameSnapshotAr: String = "",
    val districtNameSnapshotAr: String = "",
    val subDistrictNameSnapshotAr: String = "",
    val villageNameSnapshotAr: String? = null,
    val isLocalNameOverride: Boolean = false,
    val localOverrideId: String? = null,
    val enumeratorId: String,
    val enumeratorUsername: String,
    val workflowStatus: String,
    val revisionCount: Int,
    val createdAt: String,
    val updatedAt: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeM: Double? = null,
    val accuracyM: Float? = null,
    val gpsQuality: String? = null,
    val gpsCapturedAt: String? = null,
    val gpsProvider: String? = null,
    val gpsResolutionStatus: String = "NOT_EVALUATED",
    val gpsResolvedAdmin1Pcode: String? = null,
    val gpsResolvedAdmin2Pcode: String? = null,
    val gpsResolvedAdmin3Pcode: String? = null,
    val gpsDistanceToNearestVillageM: Double? = null,
    val gpsNearestVillageNameAr: String? = null,
    val wellDetailsJson: String? = null,
    val springDetailsJson: String? = null,
    val damDetailsJson: String? = null,
    val attachmentsJson: String? = null,
    val lastModifiedBy: String? = null,
    val lastSourcePackageId: String? = null,
    val adminRefVersionTag: String = "OCHA_YEM_2024_V1"
)
