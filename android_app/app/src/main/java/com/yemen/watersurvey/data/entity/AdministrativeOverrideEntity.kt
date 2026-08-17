package com.yemen.watersurvey.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for Controlled Local Administrative Name Overrides.
 *
 * CRITICAL RULE:
 * The application MUST NOT modify or overwrite the official OCHA source data.
 * Overrides allow displaying local / common names alongside official names,
 * while ensuring the official OCHA P-codes and names remain permanently recoverable.
 */
@Entity(
    tableName = "admin_overrides",
    indices = [
        Index(value = ["overrideId"], unique = true),
        Index(value = ["officialPcodeOrId"]),
        Index(value = ["administrativeLevel"]),
        Index(value = ["isApproved"]),
        Index(value = ["isActive"])
    ]
)
data class AdministrativeOverrideEntity(
    @PrimaryKey
    val overrideId: String,
    val administrativeLevel: String, // GOVERNORATE, DISTRICT, UZLAH, VILLAGE
    val officialPcodeOrId: String, // OCHA P-code or villageId
    val officialNameAr: String,
    val officialNameEn: String,
    val localNameAr: String,
    val localNameEn: String,
    val changeType: String, // NAME_CHANGE, SPELLING_CORRECTION, NEW_COMMUNITY, BOUNDARY_DISPUTE, ELEVATED_STATUS
    val reason: String,
    val sourceReference: String = "FIELD_OBSERVATION",
    val createdBy: String,
    val createdAt: String,
    val isApproved: Boolean = true,
    val approvedBy: String? = null,
    val approvedAt: String? = null,
    val isActive: Boolean = true
)
