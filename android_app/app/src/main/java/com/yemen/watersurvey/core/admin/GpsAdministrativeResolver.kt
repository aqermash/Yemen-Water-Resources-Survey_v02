package com.yemen.watersurvey.core.admin

import com.yemen.watersurvey.data.database.SurveyAppDatabase
import com.yemen.watersurvey.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.*

/**
 * Offline GPS Administrative Resolver & GIS Point-in-Polygon Engine.
 *
 * Responsibilities:
 * 1. Takes WGS84 GPS Point (latitude, longitude).
 * 2. Uses O(1) Bounding-Box filtering against local SQLite Room geometry index.
 * 3. Performs Ray-Casting Point-in-Polygon (PIP) testing against Admin1, Admin2, and Admin3 polygons.
 * 4. Computes geodesic distance (Haversine formula) to nearest mapped village.
 * 5. Returns structured GpsAdminResolutionResult with validation messages.
 * 6. Operates 100% OFFLINE without any network or external GIS dependencies.
 */
class GpsAdministrativeResolver(
    private val database: SurveyAppDatabase
) {
    private val geometryDao = database.adminGeometryDao()
    private val adminDao = database.adminReferenceDao()

    /**
     * Resolves a GPS coordinate against the administrative hierarchy with confidence scoring.
     */
    suspend fun resolveAdministrativeLocation(
        latitude: Double,
        longitude: Double,
        accuracyM: Float? = null
    ): ResolvedAdministrativeLocation = withContext(Dispatchers.IO) {
        // 1. Resolve Admin1 (Governorate)
        val admin1Match = resolveAdminLevelPolygon("GOVERNORATE", latitude, longitude)

        // 2. Resolve Admin2 (District)
        val admin2Match = resolveAdminLevelPolygon("DISTRICT", latitude, longitude)

        // 3. Resolve Admin3 (Sub-district / Uzlah)
        val admin3Match = resolveAdminLevelPolygon("UZLAH", latitude, longitude)

        // 4. Resolve nearest village if Sub-district or District is found
        var nearestVillageId: String? = null
        var nearestVillageNameAr: String? = null
        var nearestDistanceMeters: Double? = null

        val targetSubDistrictPcode = admin3Match?.first
        if (targetSubDistrictPcode != null) {
            val villages = adminDao.getVillagesByUzlah(targetSubDistrictPcode)
            var minDistance = Double.MAX_VALUE

            for (village in villages) {
                if (village.latitude != null && village.longitude != null) {
                    val dist = calculateHaversineDistanceMeters(
                        latitude, longitude,
                        village.latitude, village.longitude
                    )
                    if (dist < minDistance) {
                        minDistance = dist
                        nearestVillageId = village.villageId
                        nearestVillageNameAr = village.nameAr
                        nearestDistanceMeters = dist
                    }
                }
            }
        }

        val isExactMatch = admin1Match != null && admin2Match != null && admin3Match != null

        // 5. Evaluate Confidence based on GPS Accuracy and geometric resolution
        val accuracy = accuracyM ?: 15.0f
        val confidence = when {
            admin1Match == null -> ResolutionConfidence.UNRELIABLE
            accuracy > 30.0f -> ResolutionConfidence.UNRELIABLE
            accuracy > 15.0f -> ResolutionConfidence.LOW
            isExactMatch && accuracy <= 10.0f -> ResolutionConfidence.HIGH
            admin1Match != null && admin2Match != null && accuracy <= 15.0f -> ResolutionConfidence.MEDIUM
            else -> ResolutionConfidence.LOW
        }

        val baseStatus = when {
            admin1Match == null -> AdminResolutionStatus.OUTSIDE_BOUNDARIES
            isExactMatch -> AdminResolutionStatus.CONFIRMED
            else -> AdminResolutionStatus.UNRESOLVED
        }

        val validationMessage = when {
            isExactMatch -> "تم التحقق المكاني بنجاح: الإحداثيات تقع ضمن حدود ${admin1Match?.second} - ${admin2Match?.second} - ${admin3Match?.second}"
            admin1Match != null && admin2Match != null -> "تم تحديد المحافظة (${admin1Match.second}) والمديرية (${admin2Match.second})، لم يتم حصر حدود العزلة بدقة"
            admin1Match != null -> "تم تحديد المحافظة (${admin1Match.second}) فقط؛ الإحداثيات قرب الحدود الإدارية"
            else -> "تحذير: الإحداثيات خارج الحدود الإدارية الرسمية للجمهورية اليمنية"
        }

        ResolvedAdministrativeLocation(
            latitude = latitude,
            longitude = longitude,
            admin1Pcode = admin1Match?.first,
            admin1NameAr = admin1Match?.second,
            admin2Pcode = admin2Match?.first,
            admin2NameAr = admin2Match?.second,
            admin3Pcode = admin3Match?.first,
            admin3NameAr = admin3Match?.second,
            nearestVillageReferenceId = nearestVillageId,
            nearestVillageNameAr = nearestVillageNameAr,
            distanceToNearestVillageM = nearestDistanceMeters,
            gpsAccuracyM = accuracyM,
            resolutionStatus = baseStatus,
            resolutionConfidence = confidence,
            validationMessageAr = validationMessage,
            isExactMatch = isExactMatch
        )
    }

    /**
     * Evaluates consistency between the surveyor's manual administrative selection and GPS spatial resolution.
     * Enforces the rule: Never silently overwrite manual selection; report LOCATION_MISMATCH when differing.
     */
    fun evaluateManualSelectionConsistency(
        selectedAdmin1Pcode: String,
        selectedAdmin2Pcode: String,
        selectedAdmin3Pcode: String,
        resolvedLocation: ResolvedAdministrativeLocation?
    ): Pair<AdminResolutionStatus, String> {
        if (resolvedLocation == null) {
            return Pair(AdminResolutionStatus.NO_GPS, "لم يتم التقاط إحداثيات GPS للتحقق المكاني.")
        }

        if (resolvedLocation.admin1Pcode == null) {
            return Pair(
                AdminResolutionStatus.OUTSIDE_BOUNDARIES,
                "تحذير: الإحداثيات المسجلة تقع خارج الحدود الرسمية لليمن."
            )
        }

        val govMatches = resolvedLocation.admin1Pcode == selectedAdmin1Pcode
        val distMatches = resolvedLocation.admin2Pcode == selectedAdmin2Pcode
        val subDistMatches = resolvedLocation.admin3Pcode == null || resolvedLocation.admin3Pcode == selectedAdmin3Pcode

        return if (govMatches && distMatches && subDistMatches) {
            Pair(
                AdminResolutionStatus.CONFIRMED,
                "الموقع الإداري المحدد يدوياً متطابق ومؤكد جغرافياً مع إحداثيات GPS."
            )
        } else {
            val mismatchDetails = buildString {
                append("تنبيه: الموقع الإداري المحدد يدوياً لا يتوافق مع الموقع الجغرافي الحالي. ")
                if (!govMatches) {
                    append("المحافظة المكتشفة (${resolvedLocation.admin1NameAr ?: resolvedLocation.admin1Pcode}) تختلف عن المحددة. ")
                } else if (!distMatches) {
                    append("المديرية المكتشفة (${resolvedLocation.admin2NameAr ?: resolvedLocation.admin2Pcode}) تختلف عن المحددة. ")
                } else if (!subDistMatches && resolvedLocation.admin3NameAr != null) {
                    append("العزلة المكتشفة (${resolvedLocation.admin3NameAr}) تختلف عن المحددة. ")
                }
            }
            Pair(AdminResolutionStatus.LOCATION_MISMATCH, mismatchDetails.trim())
        }
    }

    /**
     * Resolves a GPS coordinate against the administrative hierarchy (Legacy/Standard compatibility).
     */
    suspend fun resolveLocation(
        latitude: Double,
        longitude: Double
    ): GpsAdminResolutionResult = withContext(Dispatchers.IO) {
        val res = resolveAdministrativeLocation(latitude, longitude, null)
        GpsAdminResolutionResult(
            latitude = latitude,
            longitude = longitude,
            admin1Pcode = res.admin1Pcode,
            admin1NameAr = res.admin1NameAr,
            admin2Pcode = res.admin2Pcode,
            admin2NameAr = res.admin2NameAr,
            admin3Pcode = res.admin3Pcode,
            admin3NameAr = res.admin3NameAr,
            nearestVillageId = res.nearestVillageReferenceId,
            nearestVillageNameAr = res.nearestVillageNameAr,
            nearestVillageDistanceMeters = res.distanceToNearestVillageM,
            isExactMatch = res.isExactMatch,
            validationMessageAr = res.validationMessageAr
        )
    }

    /**
     * Validates if a GPS point is consistent with the surveyor's manually selected administrative codes.
     */
    suspend fun validateGpsConsistency(
        latitude: Double,
        longitude: Double,
        selectedAdmin1Pcode: String,
        selectedAdmin2Pcode: String,
        selectedAdmin3Pcode: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val resolution = resolveLocation(latitude, longitude)
        val govMatches = resolution.admin1Pcode == selectedAdmin1Pcode
        val distMatches = resolution.admin2Pcode == selectedAdmin2Pcode
        val uzlahMatches = selectedAdmin3Pcode == null || resolution.admin3Pcode == selectedAdmin3Pcode

        govMatches && distMatches && uzlahMatches
    }

    private suspend fun resolveAdminLevelPolygon(
        adminLevel: String,
        lat: Double,
        lon: Double
    ): Pair<String, String>? {
        val candidates = geometryDao.findCandidateGeometries(adminLevel, lat, lon)
        for (candidate in candidates) {
            val polygon = parsePolygonFromJson(candidate.geometryJson, candidate.minLat, candidate.maxLat, candidate.minLon, candidate.maxLon)
            if (polygon != null && polygon.containsPoint(lat, lon)) {
                return Pair(candidate.pcode, candidate.nameAr)
            }
        }
        return null
    }

    private fun parsePolygonFromJson(
        jsonString: String,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): AdminPolygon? {
        return try {
            val root = JSONObject(jsonString)
            val coordinates = root.getJSONArray("coordinates")
            val exteriorArray = coordinates.getJSONArray(0)
            val ring = mutableListOf<GeoPoint2D>()

            for (i in 0 until exteriorArray.length()) {
                val pointArr = exteriorArray.getJSONArray(i)
                val lon = pointArr.getDouble(0)
                val lat = pointArr.getDouble(1)
                ring.add(GeoPoint2D(lat, lon))
            }

            AdminPolygon(
                exteriorRing = ring,
                boundingBox = AdminBoundingBox(minLat, maxLat, minLon, maxLon)
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Geodesic distance calculation in meters using the Haversine formula on WGS84 ellipsoid.
     */
    fun calculateHaversineDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadiusMeters = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }
}
