package com.yemen.watersurvey.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room database entity storing user identity and authentication records.
 *
 * Implements defensive security practices:
 * - Passwords and raw PINs are NEVER persisted in plaintext.
 * - PIN credentials are held as salted cryptographic hashes (SHA-256 with 16-byte random salt).
 * - Indexed by username, role, administrative P-codes, and active status for fast lookups.
 * - JSON extension metadata column for non-breaking additions.
 */
@Entity(
    tableName = "users",
    indices = [
        Index(value = ["username"], unique = true),
        Index(value = ["role"]),
        Index(value = ["governorateCode"]),
        Index(value = ["districtCode"]),
        Index(value = ["isActive"]),
        Index(value = ["assignedDeviceId"])
    ]
)
data class UserEntity(
    @PrimaryKey
    val userId: String,
    val username: String,
    val fullNameAr: String,
    val fullNameEn: String = "",
    val role: String,
    val pinSalt: String = "",
    val pinSaltedHash: String = "",
    val governorateCode: String? = null,
    val districtCode: String? = null,
    val assignedDeviceId: String? = null,
    val publicKeyBase64: String? = null,
    val isActive: Boolean = true,
    val provisionedBy: String = "",
    val provisionedAt: String = "",
    val lastLoginAt: String? = null,
    val metadataExtraJson: String = "{}"
)
