package com.yemen.watersurvey.domain.model

/**
 * Data provenance source for administrative records.
 */
enum class AdminSource(val code: String, val titleAr: String) {
    OFFICIAL_OCHA("OFFICIAL_OCHA", "مكتب تنسيق الشؤون الإنسانية (OCHA) - رسمي"),
    YEMEN_INFO("YEMEN_INFO", "دليل معلومات اليمن (Yemen-Info)"),
    OCHA_GEOMETRY("OCHA_GEOMETRY", "الحدود الجغرافية الرسمية (TopoJSON)"),
    LOCAL_OVERRIDE("LOCAL_OVERRIDE", "تعديل محلي معتمد"),
    CROSS_REFERENCE("CROSS_REFERENCE", "مطابقة جدولية مرجعية"),
    UNMAPPED("UNMAPPED", "غير مطابق - قيد المراجعة")
}

/**
 * Mapping status for villages and enriched divisions.
 */
enum class MappingStatus(val code: String, val titleAr: String) {
    MAPPED_CONFIRMED("MAPPED_CONFIRMED", "مطابق ومؤكد رسمياً"),
    NEEDS_REVIEW("NEEDS_REVIEW", "يحتاج إلى مراجعة تدقيق"),
    UNMAPPED("UNMAPPED", "غير مطابق لرمز رسمي"),
    AMBIGUOUS("AMBIGUOUS", "متعدد المطابقات المحتملة")
}

/**
 * Administrative division level hierarchy.
 */
enum class AdminLevel(val levelNumber: Int, val titleAr: String, val titleEn: String) {
    GOVERNORATE(1, "محافظة", "Governorate"),
    DISTRICT(2, "مديرية", "District"),
    UZLAH(3, "عزلة", "Uzlah"),
    VILLAGE(4, "قرية / محلة", "Village")
}

/**
 * Type of administrative name or boundary change.
 */
enum class OverrideChangeType(val code: String, val titleAr: String) {
    NAME_CHANGE("NAME_CHANGE", "تغيير مسمى رسمي أو محلي"),
    SPELLING_CORRECTION("SPELLING_CORRECTION", "تصحيح إملائي أو تشكيل"),
    NEW_COMMUNITY("NEW_COMMUNITY", "تجمع سكاني أو قرية مستحدثة"),
    BOUNDARY_DISPUTE("BOUNDARY_DISPUTE", "تداخل حدود إدارية"),
    ELEVATED_STATUS("ELEVATED_STATUS", "ترقية مستوى إداري (عزلة إلى مديرية)")
}

/**
 * Admin1: Governorate Domain Model (22 Governorates in OCHA standard).
 */
data class Admin1Governorate(
    val admin1Pcode: String, // e.g., "YE11"
    val nameAr: String,
    val nameEn: String,
    val nameArTashkeel: String = nameAr,
    val source: AdminSource = AdminSource.OFFICIAL_OCHA,
    val isActive: Boolean = true,
    val versionTag: String = "OCHA_2024_V1"
)

/**
 * Admin2: District Domain Model (333 Districts in OCHA standard).
 */
data class Admin2District(
    val admin2Pcode: String, // e.g., "YE1101"
    val admin1Pcode: String, // e.g., "YE11"
    val nameAr: String,
    val nameEn: String,
    val nameArTashkeel: String = nameAr,
    val source: AdminSource = AdminSource.OFFICIAL_OCHA,
    val isActive: Boolean = true,
    val versionTag: String = "OCHA_2024_V1"
)

/**
 * Admin3: Uzlah Domain Model (2,146 Uzlahs in OCHA standard).
 */
data class Admin3Uzlah(
    val admin3Pcode: String, // e.g., "YE110121"
    val admin2Pcode: String, // e.g., "YE1101"
    val admin1Pcode: String, // e.g., "YE11"
    val nameAr: String,
    val nameEn: String,
    val nameArTashkeel: String = nameAr,
    val source: AdminSource = AdminSource.OFFICIAL_OCHA,
    val isActive: Boolean = true,
    val versionTag: String = "OCHA_2024_V1"
)

/**
 * Village Domain Model (~41,494 Villages from Yemen-Info enrichment).
 * A Village NEVER receives a fabricated OCHA P-code.
 */
data class AdminVillage(
    val villageId: String, // e.g., "VIL-YE110121-001" or Yemen-Info ID
    val admin3Pcode: String?, // Nullable if unmapped
    val admin2Pcode: String? = null,
    val admin1Pcode: String? = null,
    val nameAr: String,
    val nameEn: String,
    val nameArTashkeel: String = nameAr,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val source: AdminSource = AdminSource.YEMEN_INFO,
    val mappingStatus: MappingStatus = MappingStatus.MAPPED_CONFIRMED,
    val versionTag: String = "YEMEN_INFO_ENRICH_V1"
)

/**
 * Controlled Local Administrative Name Override.
 * Preserves immutable official reference while supporting local renamings.
 */
data class AdministrativeOverride(
    val overrideId: String,
    val administrativeLevel: AdminLevel,
    val officialPcodeOrId: String,
    val officialNameAr: String,
    val officialNameEn: String,
    val localNameAr: String,
    val localNameEn: String,
    val changeType: OverrideChangeType,
    val reason: String,
    val sourceReference: String = "FIELD_OBSERVATION",
    val createdBy: String,
    val createdAt: String,
    val isApproved: Boolean = true,
    val approvedBy: String? = null,
    val approvedAt: String? = null,
    val isActive: Boolean = true
)

/**
 * Bounding Box for rapid spatial indexing.
 */
data class AdminBoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
) {
    fun contains(lat: Double, lon: Double): Boolean {
        return lat in minLat..maxLat && lon in minLon..maxLon
    }
}

/**
 * 2D Geographic Point.
 */
data class GeoPoint2D(
    val lat: Double,
    val lon: Double
)

/**
 * Compact Polygon representation supporting Ray-Casting Point-in-Polygon (PIP) testing.
 */
data class AdminPolygon(
    val exteriorRing: List<GeoPoint2D>,
    val interiorRings: List<List<GeoPoint2D>> = emptyList(),
    val boundingBox: AdminBoundingBox
) {
    /**
     * Ray-Casting algorithm for Point-in-Polygon (PIP) test.
     * Evaluates whether a point is inside the exterior polygon and not inside any interior holes.
     */
    fun containsPoint(lat: Double, lon: Double): Boolean {
        if (!boundingBox.contains(lat, lon)) {
            return false
        }

        if (!isPointInLinearRing(lat, lon, exteriorRing)) {
            return false
        }

        for (hole in interiorRings) {
            if (isPointInLinearRing(lat, lon, hole)) {
                return false // Inside a hole
            }
        }
        return true
    }

    private fun isPointInLinearRing(lat: Double, lon: Double, ring: List<GeoPoint2D>): Boolean {
        var inside = false
        val n = ring.size
        if (n < 3) return false

        var j = n - 1
        for (i in 0 until n) {
            val xi = ring[i].lon
            val yi = ring[i].lat
            val xj = ring[j].lon
            val yj = ring[j].lat

            val intersect = ((yi > lat) != (yj > lat)) &&
                    (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)
            if (intersect) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}

/**
 * Geographic MultiPolygon holding multiple polygon boundaries.
 */
data class AdminMultiPolygon(
    val pcode: String,
    val adminLevel: AdminLevel,
    val nameAr: String,
    val polygons: List<AdminPolygon>,
    val boundingBox: AdminBoundingBox
) {
    fun containsPoint(lat: Double, lon: Double): Boolean {
        if (!boundingBox.contains(lat, lon)) {
            return false
        }
        for (poly in polygons) {
            if (poly.containsPoint(lat, lon)) {
                return true
            }
        }
        return false
    }
}

/**
 * Status of spatial and administrative consistency evaluation.
 */
enum class AdminResolutionStatus(val code: String, val titleAr: String) {
    CONFIRMED("CONFIRMED", "متطابق ومؤكد جغرافياً"),
    LOCATION_MISMATCH("LOCATION_MISMATCH", "تنبيه: عدم تطابق مكاني مع الإحداثيات"),
    UNRESOLVED("UNRESOLVED", "غير محدد جغرافياً"),
    OUTSIDE_BOUNDARIES("OUTSIDE_BOUNDARIES", "خارج حدود الجمهورية اليمنية"),
    NO_GPS("NO_GPS", "لم يتم تسجيل إحداثيات GPS"),
    NOT_EVALUATED("NOT_EVALUATED", "لم يتم التقييم المكاني")
}

/**
 * Confidence level of GPS spatial resolution based on signal accuracy and boundary geometry.
 */
enum class ResolutionConfidence(val code: String, val titleAr: String) {
    HIGH("HIGH", "موثوقية عالية (دقة GPS ممتازة وتطابق كامل)"),
    MEDIUM("MEDIUM", "موثوقية متوسطة (دقة مقبولة)"),
    LOW("LOW", "موثوقية منخفضة (دقة GPS ضعيفة)"),
    UNRELIABLE("UNRELIABLE", "غير موثوقة (إشارة ضعيفة أو خارج النطاق)")
}

/**
 * Historical snapshot of administrative identity captured with the survey.
 * Ensures reproducibility regardless of subsequent reference data updates or local overrides.
 */
data class AdministrativeLocationSnapshot(
    val admin1Pcode: String,
    val admin2Pcode: String,
    val admin3Pcode: String,
    val governorateNameAr: String,
    val districtNameAr: String,
    val subDistrictNameAr: String,
    val villageReferenceId: String? = null,
    val villageNameAr: String? = null,
    val isLocalNameOverride: Boolean = false,
    val localOverrideId: String? = null,
    val adminRefVersionTag: String = "OCHA_YEM_2024_V1"
)

/**
 * Result of offline GPS spatial resolution against authoritative boundaries and village index.
 */
data class ResolvedAdministrativeLocation(
    val latitude: Double,
    val longitude: Double,
    val admin1Pcode: String?,
    val admin1NameAr: String?,
    val admin2Pcode: String?,
    val admin2NameAr: String?,
    val admin3Pcode: String?,
    val admin3NameAr: String?,
    val nearestVillageReferenceId: String?,
    val nearestVillageNameAr: String?,
    val distanceToNearestVillageM: Double?,
    val gpsAccuracyM: Float?,
    val resolutionStatus: AdminResolutionStatus,
    val resolutionConfidence: ResolutionConfidence,
    val validationMessageAr: String,
    val isExactMatch: Boolean,
    val sourceGeometryVersion: String = "OCHA_TOPOJSON_2019"
)

/**
 * Result of resolving a GPS point against administrative boundaries and nearest village.
 */
data class GpsAdminResolutionResult(
    val latitude: Double,
    val longitude: Double,
    val admin1Pcode: String?,
    val admin1NameAr: String?,
    val admin2Pcode: String?,
    val admin2NameAr: String?,
    val admin3Pcode: String?,
    val admin3NameAr: String?,
    val nearestVillageId: String?,
    val nearestVillageNameAr: String?,
    val nearestVillageDistanceMeters: Double?,
    val isExactMatch: Boolean,
    val validationMessageAr: String,
    val sourceGeometryVersion: String = "OCHA_TOPOJSON_2019"
)

/**
 * Versioned Administrative Reference Package Descriptor.
 */
data class AdminReferencePackageMetadata(
    val packageId: String,
    val versionTag: String,
    val releaseDate: String,
    val authoritativeSource: String = "OCHA/IMMAP (yem_admin_pcodes-02122024.xlsx)",
    val enrichmentSource: String = "Yemen-Info JSON (yemen-info.json)",
    val geometrySource: String = "OCHA TopoJSON (yem_adm_govyem_cso_ochayemen_20191002_topojson)",
    val admin1Count: Int,
    val admin2Count: Int,
    val admin3Count: Int,
    val villageCount: Int,
    val geometryCount: Int,
    val packageSha256: String,
    val isActive: Boolean = true,
    val isVerifiedOffline: Boolean = true
)
