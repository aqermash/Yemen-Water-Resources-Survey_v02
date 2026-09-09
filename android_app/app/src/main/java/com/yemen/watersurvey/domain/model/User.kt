package com.yemen.watersurvey.domain.model

/**
 * Domain model representing an authenticated actor in the Yemen Water Survey System.
 *
 * This is the immutable domain representation used across all application layers.
 * It intentionally excludes sensitive fields such as PIN hashes and cryptographic
 * private keys — those are only handled inside the Data/Core layers and never
 * exposed to Presentation or ViewModel layers.
 *
 * Design principles (aligned with FormPackage and SurveyRecord conventions):
 * - Immutability (data class with val fields only).
 * - Stable identifiers (userId is UUID).
 * - Sync-safe: all fields are serializable into ywsync UserPackage metadata.
 * - Arabic-first: nameAr is authoritative, nameEn is optional.
 *
 * @property userId Stable UUID assigned at provisioning time.
 * @property username Short unique identifier used for login (lowercase, no spaces).
 * @property fullNameAr Full Arabic name displayed in UI and stamped on official records.
 * @property fullNameEn Optional English transliteration for cross-referencing.
 * @property role Hierarchical role (see [UserRole]).
 * @property governorateCode Administrative P-code the user is bound to (nullable for CENTRAL).
 * @property districtCode Administrative P-code the user is bound to (nullable for supervisors above district).
 * @property assignedDeviceId Hardware/installation identifier bound at first login (nullable until bound).
 * @property publicKeyBase64 Ed25519 public key issued by provisioning authority (nullable in Phase 1).
 * @property isActive Whether the account is currently allowed to log in.
 * @property provisionedBy userId of the supervisor who created this account.
 * @property provisionedAt ISO timestamp of account creation.
 * @property lastLoginAt ISO timestamp of last successful authentication (nullable if never logged in).
 * @property metadataExtra Extension bag for future non-breaking additions (aligned with FormPackage pattern).
 */
data class User(
    val userId: String,
    val username: String,
    val fullNameAr: String,
    val fullNameEn: String = "",
    val role: UserRole,
    val governorateCode: String? = null,
    val districtCode: String? = null,
    val assignedDeviceId: String? = null,
    val publicKeyBase64: String? = null,
    val isActive: Boolean = true,
    val provisionedBy: String = "",
    val provisionedAt: String = "",
    val lastLoginAt: String? = null,
    val metadataExtra: Map<String, String> = emptyMap()
) {
    /**
     * Returns a compact display label suitable for headers and audit log entries.
     * Format: "الاسم الكامل (اسم المستخدم) — الدور"
     */
    val displayLabel: String
        get() = "$fullNameAr ($username) — ${role.titleAr}"

    /**
     * Returns true if the user's administrative scope covers the given P-code.
     * Central supervisors always return true; enumerators only match their own district.
     */
    fun coversAdministrativeArea(governoratePcode: String?, districtPcode: String?): Boolean {
        return when (role) {
            UserRole.CENTRAL_SUPERVISOR -> true
            UserRole.GOVERNORATE_SUPERVISOR -> governorateCode == governoratePcode
            UserRole.DISTRICT_SUPERVISOR,
            UserRole.ENUMERATOR -> governorateCode == governoratePcode && districtCode == districtPcode
        }
    }
}
