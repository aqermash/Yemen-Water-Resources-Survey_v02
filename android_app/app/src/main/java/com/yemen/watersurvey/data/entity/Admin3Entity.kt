package com.yemen.watersurvey.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for Uzlah (Admin3) - 2,146 Uzlahs in OCHA Standard.
 */
@Entity(
    tableName = "admin3_uzlahs",
    foreignKeys = [
        ForeignKey(
            entity = Admin2Entity::class,
            parentColumns = ["admin2Pcode"],
            childColumns = ["admin2Pcode"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["admin3Pcode"], unique = true),
        Index(value = ["admin2Pcode"]),
        Index(value = ["admin1Pcode"]),
        Index(value = ["nameAr"]),
        Index(value = ["nameEn"]),
        Index(value = ["isActive"])
    ]
)
data class Admin3Entity(
    @PrimaryKey
    val admin3Pcode: String, // e.g., "YE110121"
    val admin2Pcode: String, // Parent P-code e.g., "YE1101"
    val admin1Pcode: String, // e.g., "YE11"
    val nameAr: String,
    val nameEn: String,
    val nameArTashkeel: String = nameAr,
    val source: String = "OFFICIAL_OCHA",
    val isActive: Boolean = true,
    val versionTag: String = "OCHA_2024_V1"
)
