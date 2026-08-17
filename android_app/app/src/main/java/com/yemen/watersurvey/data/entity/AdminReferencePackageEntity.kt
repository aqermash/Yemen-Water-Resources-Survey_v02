package com.yemen.watersurvey.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity tracking installed Administrative Reference Packages.
 */
@Entity(
    tableName = "admin_reference_packages",
    indices = [
        Index(value = ["packageId"], unique = true),
        Index(value = ["versionTag"]),
        Index(value = ["isActive"])
    ]
)
data class AdminReferencePackageEntity(
    @PrimaryKey
    val packageId: String,
    val versionTag: String,
    val releaseDate: String,
    val authoritativeSource: String,
    val enrichmentSource: String,
    val geometrySource: String,
    val admin1Count: Int,
    val admin2Count: Int,
    val admin3Count: Int,
    val villageCount: Int,
    val geometryCount: Int,
    val packageSha256: String,
    val isActive: Boolean = true,
    val installedAt: String,
    val installedBy: String = "SYSTEM_BOOTSTRAP"
)
