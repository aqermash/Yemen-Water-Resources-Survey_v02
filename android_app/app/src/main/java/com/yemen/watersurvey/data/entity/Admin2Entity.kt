package com.yemen.watersurvey.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for District (Admin2) - 333 Districts in OCHA Standard.
 */
@Entity(
    tableName = "admin2_districts",
    foreignKeys = [
        ForeignKey(
            entity = Admin1Entity::class,
            parentColumns = ["admin1Pcode"],
            childColumns = ["admin1Pcode"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["admin2Pcode"], unique = true),
        Index(value = ["admin1Pcode"]),
        Index(value = ["nameAr"]),
        Index(value = ["nameEn"]),
        Index(value = ["isActive"])
    ]
)
data class Admin2Entity(
    @PrimaryKey
    val admin2Pcode: String, // e.g., "YE1101"
    val admin1Pcode: String, // Parent P-code e.g., "YE11"
    val nameAr: String,
    val nameEn: String,
    val nameArTashkeel: String = nameAr,
    val source: String = "OFFICIAL_OCHA",
    val isActive: Boolean = true,
    val versionTag: String = "OCHA_2024_V1"
)
