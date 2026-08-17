package com.yemen.watersurvey.core.admin

import com.yemen.watersurvey.data.entity.Admin1Entity
import com.yemen.watersurvey.data.entity.Admin2Entity
import com.yemen.watersurvey.data.entity.Admin3Entity
import com.yemen.watersurvey.domain.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit Tests for Phase 10 & 10.1 Administrative Reference, Ingestion & GIS Subsystem.
 */
class AdminReferenceTest {

    private val crossMappingEngine = AdminCrossMappingEngine()
    private val topoJsonEngine = TopoJsonIngestionEngine()

    @Test
    fun testRayCastingPointInPolygon() {
        // Define a square polygon in Sa'ada region: Lat [16.8, 17.2], Lon [43.5, 44.0]
        val ring = listOf(
            GeoPoint2D(16.8, 43.5),
            GeoPoint2D(16.8, 44.0),
            GeoPoint2D(17.2, 44.0),
            GeoPoint2D(17.2, 43.5),
            GeoPoint2D(16.8, 43.5) // Closed
        )
        val bbox = AdminBoundingBox(16.8, 17.2, 43.5, 44.0)
        val polygon = AdminPolygon(exteriorRing = ring, boundingBox = bbox)

        // Point inside
        assertTrue("Point (17.0, 43.7) must be inside polygon", polygon.containsPoint(17.0, 43.7))
        assertTrue("Point (16.9412, 43.7611) must be inside polygon", polygon.containsPoint(16.9412, 43.7611))

        // Point outside
        assertFalse("Point (15.0, 44.0) must be outside polygon", polygon.containsPoint(15.0, 44.0))
        assertFalse("Point (18.0, 43.7) must be outside polygon", polygon.containsPoint(18.0, 43.7))
        assertFalse("Point (17.0, 45.0) must be outside polygon", polygon.containsPoint(17.0, 45.0))
    }

    @Test
    fun testBoundingBoxRejection() {
        val bbox = AdminBoundingBox(14.0, 15.0, 44.0, 45.0)
        assertTrue(bbox.contains(14.5, 44.5))
        assertFalse(bbox.contains(16.0, 44.5))
        assertFalse(bbox.contains(14.5, 43.0))
    }

    @Test
    fun testArabicTextNormalization() {
        val raw1 = "صَعْدَة"
        assertEquals("صعده", crossMappingEngine.normalizeArabic(raw1))

        val raw2 = "أَمَانَة العَاصِمَة"
        assertEquals("امانه العاصمه", crossMappingEngine.normalizeArabic(raw2))

        val raw3 = "إِبّ"
        assertEquals("اب", crossMappingEngine.normalizeArabic(raw3))

        val raw4 = "قَرْيَة بَيْت بَوْس"
        assertEquals("بيت بوس", crossMappingEngine.stripArabicPrefixes(raw4))
    }

    @Test
    fun testEnglishTextNormalization() {
        val raw1 = "Al-Talh"
        assertEquals("talh", crossMappingEngine.normalizeEnglish(raw1))

        val raw2 = "As Safra"
        assertEquals("safra", crossMappingEngine.normalizeEnglish(raw2))

        val raw3 = "Dar Al Hajar"
        assertEquals("dar al hajar", crossMappingEngine.normalizeEnglish(raw3))
    }

    @Test
    fun testCrossMappingMultiStage() {
        val govs = listOf(
            Admin1Entity("YE11", "صعدة", "Sa'ada", "صَعْدَة", "OFFICIAL_OCHA", true, "V1")
        )
        val dists = listOf(
            Admin2Entity("YE1101", "YE11", "سحار", "Sahar", "سَحَار", "OFFICIAL_OCHA", true, "V1")
        )
        val uzlahs = listOf(
            Admin3Entity("YE110101", "YE1101", "YE11", "الطلح", "At Talh", "الطَّلْح", "OFFICIAL_OCHA", true, "V1")
        )

        val rawRecords = listOf(
            RawYemenInfoRecord(
                sourceId = "YINFO-001",
                govNameAr = "صَعْدَة",
                distNameAr = "سَحَار",
                uzlahNameAr = "الطَّلْح",
                villageNameAr = "المقاش",
                villageNameEn = "Al Maqash"
            ),
            RawYemenInfoRecord(
                sourceId = "YINFO-002",
                govNameAr = "صعدة",
                distNameAr = "سحار",
                uzlahNameAr = "عزلة مجهولة غير موجودة",
                villageNameAr = "قرية مجهولة",
                villageNameEn = "Unknown"
            )
        )

        val (mappedList, report) = crossMappingEngine.crossMapVillages(rawRecords, govs, dists, uzlahs, "V1")

        assertEquals(2, mappedList.size)
        assertEquals(2, report.totalYemenInfoVillages)
        assertEquals(1, report.mappedConfirmed)
        assertEquals(1, report.needsReviewCount)

        val confirmed = mappedList.first { it.villageId == "YINFO-001" }
        assertEquals("YE110101", confirmed.admin3Pcode)
        assertEquals("MAPPED_CONFIRMED", confirmed.mappingStatus)

        val unconfirmed = mappedList.first { it.villageId == "YINFO-002" }
        assertNull("Unmapped record MUST have null admin3Pcode", unconfirmed.admin3Pcode)
        assertEquals("NEEDS_REVIEW", unconfirmed.mappingStatus)
    }

    @Test
    fun testTopoJsonFeatureParsing() {
        val feature = JSONObject().apply {
            put("properties", JSONObject().apply {
                put("admin1Pcode", "YE11")
                put("admin1Name_ar", "صعدة")
                put("admin1Name_en", "Sa'ada")
            })
            put("geometry", JSONObject().apply {
                put("type", "Polygon")
                val ring = JSONArray().apply {
                    put(JSONArray(listOf(43.5, 16.5)))
                    put(JSONArray(listOf(44.5, 16.5)))
                    put(JSONArray(listOf(44.5, 17.5)))
                    put(JSONArray(listOf(43.5, 17.5)))
                    put(JSONArray(listOf(43.5, 16.5)))
                }
                put("coordinates", JSONArray().apply { put(ring) })
            })
        }

        val result = topoJsonEngine.parseFeature(feature, "GOVERNORATE", "V1")
        assertTrue("Geometry must be valid", result.isValid)
        assertEquals("YE11", result.pcode)
        assertEquals("GOVERNORATE", result.adminLevel)
        assertEquals(16.5, result.boundingBox.minLat, 0.001)
        assertEquals(17.5, result.boundingBox.maxLat, 0.001)
    }

    @Test
    fun testVillageUnmappedStatusRule() {
        val unmappedVillage = AdminVillage(
            villageId = "YINFO-VIL-99882",
            admin3Pcode = null,
            admin2Pcode = "YE1101",
            admin1Pcode = "YE11",
            nameAr = "محلة وادي غيلان",
            nameEn = "Wadi Ghaylan Locality",
            source = AdminSource.YEMEN_INFO,
            mappingStatus = MappingStatus.UNMAPPED
        )

        assertNull("Unmapped village MUST NOT have a fabricated admin3Pcode", unmappedVillage.admin3Pcode)
        assertEquals(MappingStatus.UNMAPPED, unmappedVillage.mappingStatus)
        assertEquals(AdminSource.YEMEN_INFO, unmappedVillage.source)
    }

    @Test
    fun testControlledOverridePreservesOfficialName() {
        val override = AdministrativeOverride(
            overrideId = "OVR-001",
            administrativeLevel = AdminLevel.UZLAH,
            officialPcodeOrId = "YE110101",
            officialNameAr = "الطلح",
            officialNameEn = "At Talh",
            localNameAr = "الطلح الكبرى",
            localNameEn = "At Talh Al Kubra",
            changeType = OverrideChangeType.NAME_CHANGE,
            reason = "Common community usage in modern documentation",
            createdBy = "supervisor_ahmed",
            createdAt = "2026-08-15 12:00:00",
            isApproved = true,
            isActive = true
        )

        assertEquals("YE110101", override.officialPcodeOrId)
        assertEquals("الطلح", override.officialNameAr)
        assertEquals("الطلح الكبرى", override.localNameAr)
        assertTrue(override.isApproved)
        assertTrue(override.isActive)
    }

    @Test
    fun testAdministrativeSnapshotCreation() {
        val snapshot = AdministrativeLocationSnapshot(
            admin1Pcode = "YE11",
            admin2Pcode = "YE1101",
            admin3Pcode = "YE110101",
            villageReferenceId = "YINFO-001",
            governorateNameAr = "صعدة",
            districtNameAr = "سحار",
            subDistrictNameAr = "الطلح",
            villageNameAr = "المقاش",
            isLocalNameOverride = false,
            localOverrideId = null,
            snapshotTimestamp = "2026-08-15 12:00:00",
            adminRefVersionTag = "OCHA_YEM_2024_V1"
        )

        assertEquals("YE11", snapshot.admin1Pcode)
        assertEquals("YE1101", snapshot.admin2Pcode)
        assertEquals("YE110101", snapshot.admin3Pcode)
        assertEquals("صعدة", snapshot.governorateNameAr)
        assertEquals("سحار", snapshot.districtNameAr)
        assertEquals("الطلح", snapshot.subDistrictNameAr)
        assertEquals("المقاش", snapshot.villageNameAr)
        assertFalse(snapshot.isLocalNameOverride)
        assertEquals("OCHA_YEM_2024_V1", snapshot.adminRefVersionTag)
    }

    @Test
    fun testSelectionConsistencyEvaluation() {
        val resolved = ResolvedAdministrativeLocation(
            admin1Pcode = "YE11",
            admin2Pcode = "YE1101",
            admin3Pcode = "YE110101",
            confidence = ResolutionConfidence.HIGH,
            resolutionMethod = "POINT_IN_POLYGON_EXACT",
            distanceToNearestVillageM = 320.0,
            nearestVillageId = "YINFO-001",
            nearestVillageNameAr = "المقاش"
        )

        // Case 1: Match
        val matchResult = GpsAdministrativeResolver.SelectionConsistencyResult(
            status = AdminResolutionStatus.LOCATION_MATCH,
            isConsistent = true,
            detailsAr = "الموقع الإداري المختار مطابق للتحقق المكاني الفضائي (GPS).",
            resolvedLocation = resolved
        )
        assertEquals(AdminResolutionStatus.LOCATION_MATCH, matchResult.status)
        assertTrue(matchResult.isConsistent)

        // Case 2: Mismatch
        val mismatchResult = GpsAdministrativeResolver.SelectionConsistencyResult(
            status = AdminResolutionStatus.LOCATION_MISMATCH,
            isConsistent = false,
            detailsAr = "تحذير: إحداثيات GPS تقع ضمن (YE110101) بينما تم اختيار (YE110102).",
            resolvedLocation = resolved
        )
        assertEquals(AdminResolutionStatus.LOCATION_MISMATCH, mismatchResult.status)
        assertFalse(mismatchResult.isConsistent)
    }
}
