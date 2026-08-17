package com.yemen.watersurvey.core.admin

import com.yemen.watersurvey.data.entity.AdminGeometryEntity
import com.yemen.watersurvey.domain.model.AdminBoundingBox
import com.yemen.watersurvey.domain.model.AdminLevel
import com.yemen.watersurvey.domain.model.GeoPoint2D
import org.json.JSONArray
import org.json.JSONObject

/**
 * Engine for parsing, optimizing, and validating OCHA TopoJSON and GeoJSON boundary datasets.
 *
 * Responsibilities:
 * 1. Parses TopoJSON/GeoJSON structures into high-performance spatial entities.
 * 2. Matches geometries strictly to canonical OCHA P-codes without altering administrative keys.
 * 3. Pre-computes 2D bounding boxes and centroids for O(1) candidate pre-filtering.
 * 4. Evaluates storage footprint vs decoding latency for mobile offline execution.
 */
class TopoJsonIngestionEngine {

    data class ParsedGeometryResult(
        val pcode: String,
        val adminLevel: String,
        val nameAr: String,
        val nameEn: String,
        val boundingBox: AdminBoundingBox,
        val centroidLat: Double,
        val centroidLon: Double,
        val ringCount: Int,
        val totalPoints: Int,
        val compactGeometryJson: String,
        val isValid: Boolean,
        val validationErrors: List<String>
    )

    /**
     * Parses a GeoJSON or decoded TopoJSON feature into an optimized AdminGeometryEntity.
     */
    fun parseFeature(
        featureJson: JSONObject,
        expectedAdminLevel: String,
        versionTag: String = "OCHA_YEM_2024_V1"
    ): ParsedGeometryResult {
        val properties = featureJson.optJSONObject("properties") ?: JSONObject()
        val geometry = featureJson.optJSONObject("geometry") ?: JSONObject()

        val pcode = when (expectedAdminLevel.uppercase()) {
            "GOVERNORATE", "ADMIN1" -> properties.optString("admin1Pcode", properties.optString("Gov_Pcode", ""))
            "DISTRICT", "ADMIN2" -> properties.optString("admin2Pcode", properties.optString("Dis_Pcode", ""))
            "UZLAH", "ADMIN3" -> properties.optString("admin3Pcode", properties.optString("admin3_Pcode", ""))
            else -> properties.optString("pcode", "")
        }

        val nameAr = when (expectedAdminLevel.uppercase()) {
            "GOVERNORATE", "ADMIN1" -> properties.optString("admin1Name_ar", properties.optString("Gov_Name_A", ""))
            "DISTRICT", "ADMIN2" -> properties.optString("admin2Name_ar", properties.optString("Dis_Name_A", ""))
            "UZLAH", "ADMIN3" -> properties.optString("admin3Name_ar", properties.optString("Uz_Name_A", ""))
            else -> properties.optString("nameAr", "")
        }

        val nameEn = when (expectedAdminLevel.uppercase()) {
            "GOVERNORATE", "ADMIN1" -> properties.optString("admin1Name_en", properties.optString("Gov_Name_E", ""))
            "DISTRICT", "ADMIN2" -> properties.optString("admin2Name_en", properties.optString("Dis_Name_E", ""))
            "UZLAH", "ADMIN3" -> properties.optString("admin3Name_en", properties.optString("Uz_Name_E", ""))
            else -> properties.optString("nameEn", "")
        }

        val geomType = geometry.optString("type", "")
        val coordinates = geometry.optJSONArray("coordinates") ?: JSONArray()

        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        var sumLat = 0.0
        var sumLon = 0.0
        var totalPoints = 0
        var ringCount = 0
        val errors = mutableListOf<String>()

        if (pcode.isBlank()) {
            errors.add("Missing required P-code in geometry properties")
        }

        if (geomType !in listOf("Polygon", "MultiPolygon")) {
            errors.add("Unsupported geometry type: $geomType")
        }

        fun processRing(ringArray: JSONArray) {
            ringCount++
            val ringSize = ringArray.length()
            if (ringSize < 4) {
                errors.add("Polygon ring has fewer than 4 coordinates")
                return
            }

            // Check ring closure (first point equals last point)
            val firstPt = ringArray.optJSONArray(0)
            val lastPt = ringArray.optJSONArray(ringSize - 1)
            if (firstPt != null && lastPt != null) {
                val lonDiff = Math.abs(firstPt.optDouble(0) - lastPt.optDouble(0))
                val latDiff = Math.abs(firstPt.optDouble(1) - lastPt.optDouble(1))
                if (lonDiff > 0.0001 || latDiff > 0.0001) {
                    errors.add("Polygon ring is not closed")
                }
            }

            for (i in 0 until ringSize) {
                val pt = ringArray.optJSONArray(i) ?: continue
                val lon = pt.optDouble(0)
                val lat = pt.optDouble(1)

                if (lat < minLat) minLat = lat
                if (lat > maxLat) maxLat = lat
                if (lon < minLon) minLon = lon
                if (lon > maxLon) maxLon = lon

                sumLat += lat
                sumLon += lon
                totalPoints++
            }
        }

        if (geomType == "Polygon") {
            for (r in 0 until coordinates.length()) {
                val ring = coordinates.optJSONArray(r) ?: continue
                processRing(ring)
            }
        } else if (geomType == "MultiPolygon") {
            for (p in 0 until coordinates.length()) {
                val poly = coordinates.optJSONArray(p) ?: continue
                for (r in 0 until poly.length()) {
                    val ring = poly.optJSONArray(r) ?: continue
                    processRing(ring)
                }
            }
        }

        val centroidLat = if (totalPoints > 0) sumLat / totalPoints else 0.0
        val centroidLon = if (totalPoints > 0) sumLon / totalPoints else 0.0

        // Validate Yemen geographic bounds (roughly 11.5°N - 19.5°N, 41.5°E - 55.0°E)
        if (minLat < 10.0 || maxLat > 20.0 || minLon < 40.0 || maxLon > 56.0) {
            errors.add("Geometry coordinates fall outside plausible Yemen bounds: [$minLat, $maxLat, $minLon, $maxLon]")
        }

        val bbox = AdminBoundingBox(
            minLat = if (minLat != Double.MAX_VALUE) minLat else 0.0,
            maxLat = if (maxLat != -Double.MAX_VALUE) maxLat else 0.0,
            minLon = if (minLon != Double.MAX_VALUE) minLon else 0.0,
            maxLon = if (maxLon != -Double.MAX_VALUE) maxLon else 0.0
        )

        return ParsedGeometryResult(
            pcode = pcode,
            adminLevel = expectedAdminLevel.uppercase(),
            nameAr = nameAr,
            nameEn = nameEn,
            boundingBox = bbox,
            centroidLat = centroidLat,
            centroidLon = centroidLon,
            ringCount = ringCount,
            totalPoints = totalPoints,
            compactGeometryJson = geometry.toString(),
            isValid = errors.isEmpty(),
            validationErrors = errors
        )
    }

    /**
     * Converts a collection of parsed geometry results into ready-to-insert Room entities.
     */
    fun toGeometryEntities(
        parsedResults: List<ParsedGeometryResult>,
        versionTag: String = "OCHA_YEM_2024_V1"
    ): List<AdminGeometryEntity> {
        return parsedResults.map {
            AdminGeometryEntity(
                pcode = it.pcode,
                adminLevel = it.adminLevel,
                nameAr = it.nameAr,
                nameEn = it.nameEn,
                minLat = it.boundingBox.minLat,
                maxLat = it.boundingBox.maxLat,
                minLon = it.boundingBox.minLon,
                maxLon = it.boundingBox.maxLon,
                centroidLat = it.centroidLat,
                centroidLon = it.centroidLon,
                geometryJson = it.compactGeometryJson,
                versionTag = versionTag
            )
        }
    }
}
