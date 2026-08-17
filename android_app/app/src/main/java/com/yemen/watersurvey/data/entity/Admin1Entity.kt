package com.yemen.watersurvey.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for Governorate (Admin1) - 22 Governorates in OCHA Standard.
 */
@Entity(
    tableName = "admin1_governorates",
    indices = [
        Index(value = ["admin1Pcode"], unique = true),
        Index(value = ["nameAr"]),
        Index(value = ["nameEn"]),
        Index(value = ["isActive"])
    ]
)
data class Admin1Entity(
    @PrimaryKey
    val admin1Pcode: String, // e.g., "YE11"
    val nameAr: String,
    val nameEn: String,
    val nameArTashkeel: String = nameAr,
    val source: String = "OFFICIAL_OCHA",
    val isActive: Boolean = true,
    val versionTag: String = "OCHA_2024_V1"
)
