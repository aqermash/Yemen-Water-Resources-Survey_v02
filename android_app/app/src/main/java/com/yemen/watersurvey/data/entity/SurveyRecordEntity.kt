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
) {
    fun toDomainModel(): com.yemen.watersurvey.domain.model.SurveyRecord {
        val wellDetails = wellDetailsJson?.let { jsonStr ->
            try {
                val json = org.json.JSONObject(jsonStr)
                com.yemen.watersurvey.domain.model.WellDetails(
                    wellNameAr = json.optString("wellNameAr", ""),
                    wellType = json.optString("wellType", ""),
                    wellDepthM = json.optDouble("wellDepthM", 0.0),
                    pumpingMechanism = json.optString("pumpingMechanism", ""),
                    operationalStatus = json.optString("operationalStatus", "")
                )
            } catch (e: Exception) {
                null
            }
        }

        val gpsLocation = if (latitude != null && longitude != null) {
            com.yemen.watersurvey.domain.model.GpsLocationResult(
                latitude = latitude,
                longitude = longitude,
                altitudeM = altitudeM ?: 0.0,
                accuracyM = accuracyM ?: 0.0f,
                quality = try {
                    com.yemen.watersurvey.domain.model.GpsAccuracyQuality.valueOf(gpsQuality ?: "GOOD")
                } catch (e: Exception) {
                    com.yemen.watersurvey.domain.model.GpsAccuracyQuality.GOOD
                },
                provider = gpsProvider ?: "gps",
                capturedAt = gpsCapturedAt ?: createdAt
            )
        } else null

        val type = try {
            com.yemen.watersurvey.domain.model.SurveyType.valueOf(surveyType)
        } catch (e: Exception) {
            com.yemen.watersurvey.domain.model.SurveyType.WELL
        }

        val resStatus = try {
            com.yemen.watersurvey.domain.model.AdminResolutionStatus.valueOf(gpsResolutionStatus)
        } catch (e: Exception) {
            com.yemen.watersurvey.domain.model.AdminResolutionStatus.NOT_EVALUATED
        }

        return com.yemen.watersurvey.domain.model.SurveyRecord(
            recordId = recordId,
            surveyUUID = surveyUUID,
            formId = formId,
            formVersion = formVersion,
            surveyType = type,
            admin1Pcode = admin1Pcode,
            admin2Pcode = admin2Pcode,
            admin3Pcode = admin3Pcode,
            villageReferenceId = villageReferenceId,
            governorateCode = governorateCode,
            districtCode = districtCode,
            uzlahCode = uzlahCode,
            villageCode = villageCode,
            governorateNameSnapshotAr = governorateNameSnapshotAr,
            districtNameSnapshotAr = districtNameSnapshotAr,
            subDistrictNameSnapshotAr = subDistrictNameSnapshotAr,
            villageNameSnapshotAr = villageNameSnapshotAr,
            isLocalNameOverride = isLocalNameOverride,
            localOverrideId = localOverrideId,
            enumeratorId = enumeratorId,
            enumeratorUsername = enumeratorUsername,
            workflowStatus = workflowStatus,
            revisionCount = revisionCount,
            createdAt = createdAt,
            updatedAt = updatedAt,
            gpsPoint = gpsLocation,
            gpsResolutionStatus = resStatus,
            gpsResolvedAdmin1Pcode = gpsResolvedAdmin1Pcode,
            gpsResolvedAdmin2Pcode = gpsResolvedAdmin2Pcode,
            gpsResolvedAdmin3Pcode = gpsResolvedAdmin3Pcode,
            gpsDistanceToNearestVillageM = gpsDistanceToNearestVillageM,
            gpsNearestVillageNameAr = gpsNearestVillageNameAr,
            wellDetails = wellDetails,
            springDetails = null,
            damDetails = null,
            attachments = emptyList(),
            adminRefVersionTag = adminRefVersionTag
        )
    }
}
