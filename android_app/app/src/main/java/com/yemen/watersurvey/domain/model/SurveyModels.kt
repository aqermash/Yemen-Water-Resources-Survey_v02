package com.yemen.watersurvey.domain.model

enum class SurveyType(val displayNameAr: String) {
    WELL("بئر مياه"),
    SPRING("عين / ينبوع"),
    DAM("سد / حاجز مائي")
}

enum class GpsAccuracyQuality(val titleAr: String, val maxAccuracyMeters: Float) {
    EXCELLENT("ممتازة (أقل من 5م)", 5.0f),
    GOOD("جيدة (5م - 10م)", 10.0f),
    ACCEPTABLE_WITH_WARNING("مقبولة مع تحذير (10م - 15م)", 15.0f),
    POOR("ضعيفة (15م - 30م)", 30.0f),
    UNRELIABLE("غير موثوقة (أكثر من 30م)", Float.MAX_VALUE);

    companion object {
        fun classify(accuracyMeters: Float): GpsAccuracyQuality {
            return when {
                accuracyMeters <= 5.0f -> EXCELLENT
                accuracyMeters <= 10.0f -> GOOD
                accuracyMeters <= 15.0f -> ACCEPTABLE_WITH_WARNING
                accuracyMeters <= 30.0f -> POOR
                else -> UNRELIABLE
            }
        }
    }
}

data class GpsLocationResult(
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    val accuracyM: Float,
    val quality: GpsAccuracyQuality,
    val capturedAt: String,
    val provider: String = "GPS_WGS84"
)

data class AttachmentInfo(
    val attachmentId: String,
    val surveyId: String,
    val surveyUUID: String = "",
    val attachmentType: String,
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val timestamp: String,
    val fileSha256: String = ""
)

data class WellDetails(
    val wellNameAr: String,
    val wellType: String,
    val wellDepthM: Double,
    val pumpingMechanism: String,
    val operationalStatus: String
)

data class SpringDetails(
    val springNameAr: String,
    val flowRateLps: Double,
    val waterClarity: String,
    val dischargeSeasonality: String
)

data class DamDetails(
    val damNameAr: String,
    val structureType: String,
    val storageCapacityM3: Double,
    val damHeightM: Double,
    val structuralCondition: String
)

data class SurveyRecord(
    val recordId: String,
    val surveyUUID: String = java.util.UUID.nameUUIDFromBytes(recordId.toByteArray()).toString(),
    val formId: String = "WATER_SURVEY_V1",
    val formVersion: String = "1.0",
    val surveyType: SurveyType,
    val admin1Pcode: String,
    val admin2Pcode: String,
    val admin3Pcode: String,
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
    val enumeratorId: String = "usr-001",
    val enumeratorUsername: String = "ahmed_enum",
    val workflowStatus: String = "COMPLETED",
    val revisionCount: Int = 1,
    val createdAt: String,
    val updatedAt: String = createdAt,
    val gpsPoint: GpsLocationResult? = null,
    val gpsResolutionStatus: AdminResolutionStatus = AdminResolutionStatus.NOT_EVALUATED,
    val gpsResolvedAdmin1Pcode: String? = null,
    val gpsResolvedAdmin2Pcode: String? = null,
    val gpsResolvedAdmin3Pcode: String? = null,
    val gpsDistanceToNearestVillageM: Double? = null,
    val gpsNearestVillageNameAr: String? = null,
    val wellDetails: WellDetails? = null,
    val springDetails: SpringDetails? = null,
    val damDetails: DamDetails? = null,
    val attachments: List<AttachmentInfo> = emptyList(),
    val adminRefVersionTag: String = "OCHA_YEM_2024_V1"
)
