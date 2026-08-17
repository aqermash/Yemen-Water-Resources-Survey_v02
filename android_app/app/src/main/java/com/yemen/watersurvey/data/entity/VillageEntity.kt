package com.yemen.watersurvey.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for Village / Locality (~41,494 Villages from Yemen-Info enrichment).
 *
 * CRITICAL RULE: A Village NEVER receives a fabricated OCHA P-code.
 * It uses a distinct `villageId` and is mapped to `admin3Pcode` where a reliable match exists.
 * Unmapped villages are flagged with mappingStatus = 'UNMAPPED' or 'NEEDS_REVIEW'.
 */
@Entity(
    tableName = "admin_villages",
    indices = [
        Index(value = ["villageId"], unique = true),
        Index(value = ["admin3Pcode"]),
        Index(value = ["admin2Pcode"]),
        Index(value = ["admin1Pcode"]),
        Index(value = ["nameAr"]),
        Index(value = ["nameEn"]),
        Index(value = ["mappingStatus"]),
        Index(value = ["latitude", "longitude"])
    ]
)
data class VillageEntity(
    @PrimaryKey
    val villageId: String, // e.g., "VIL-YE110121-001" or Yemen-Info ID
    val admin3Pcode: String?, // Nullable if unmapped
    val admin2Pcode: String? = null,
    val admin1Pcode: String? = null,
    val nameAr: String,
    val nameEn: String,
    val nameArTashkeel: String = nameAr,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val source: String = "YEMEN_INFO",
    val mappingStatus: String = "MAPPED_CONFIRMED", // MAPPED_CONFIRMED, NEEDS_REVIEW, UNMAPPED, AMBIGUOUS
    val versionTag: String = "YEMEN_INFO_ENRICH_V1"
)
