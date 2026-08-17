package com.yemen.watersurvey.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity storing compact administrative GIS geometry (Polygons / MultiPolygons).
 * Geometry is associated strictly with official OCHA P-codes.
 */
@Entity(
    tableName = "admin_geometry",
    indices = [
        Index(value = ["pcode"], unique = true),
        Index(value = ["adminLevel"]),
        Index(value = ["minLat", "maxLat", "minLon", "maxLon"])
    ]
)
data class AdminGeometryEntity(
    @PrimaryKey
    val pcode: String, // admin1Pcode, admin2Pcode, or admin3Pcode
    val adminLevel: String, // GOVERNORATE, DISTRICT, UZLAH
    val nameAr: String,
    val nameEn: String,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val centroidLat: Double? = null,
    val centroidLon: Double? = null,
    val geometryJson: String, // Compact GeoJSON / Polygon coordinate array
    val source: String = "OCHA_TOPOJSON_2019",
    val versionTag: String = "TOPOJSON_20191002"
)
