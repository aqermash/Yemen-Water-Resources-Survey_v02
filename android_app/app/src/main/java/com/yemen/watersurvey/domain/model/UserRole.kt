package com.yemen.watersurvey.domain.model

/**
 * Hierarchical role classification within the Yemen Water Survey System.
 *
 * The role hierarchy directly mirrors the administrative pyramid documented in
 * SurveySyncExporter and SurveySyncImporter:
 *
 * ENUMERATOR → DISTRICT_SUPERVISOR → GOVERNORATE_SUPERVISOR → CENTRAL_SUPERVISOR
 *
 * Each role determines:
 * - Which screens and actions the user can access.
 * - Which sync package operations are permitted (export vs. import vs. merge).
 * - The scope of administrative data visible to the user (village/district/governorate/national).
 *
 * @property code Stable machine-readable identifier used in database and sync packages.
 * @property titleAr Arabic display title for UI presentation.
 * @property descriptionAr Arabic description shown in profile and role management screens.
 * @property hierarchyLevel Numeric elevation used for permission comparisons (higher = broader authority).
 */
enum class UserRole(
    val code: String,
    val titleAr: String,
    val descriptionAr: String,
    val hierarchyLevel: Int
) {
    ENUMERATOR(
        code = "ENUMERATOR",
        titleAr = "ماسح ميداني",
        descriptionAr = "مسؤول عن جمع البيانات الميدانية للآبار والينابيع والسدود وتصديرها إلى مشرف المديرية.",
        hierarchyLevel = 1
    ),
    DISTRICT_SUPERVISOR(
        code = "DISTRICT_SUPERVISOR",
        titleAr = "مشرف المديرية",
        descriptionAr = "مسؤول عن مراجعة استمارات الماسحين الميدانيين ضمن نطاق المديرية واعتمادها أو رفضها.",
        hierarchyLevel = 2
    ),
    GOVERNORATE_SUPERVISOR(
        code = "GOVERNORATE_SUPERVISOR",
        titleAr = "مشرف المحافظة",
        descriptionAr = "مسؤول عن دمج استمارات مديريات المحافظة واعتماد الحزم قبل رفعها إلى المركز.",
        hierarchyLevel = 3
    ),
    CENTRAL_SUPERVISOR(
        code = "CENTRAL_SUPERVISOR",
        titleAr = "المشرف المركزي (الوزارة)",
        descriptionAr = "مسؤول الاعتماد النهائي على مستوى الجمهورية وإصدار حزم المستخدمين ونماذج الاستمارات.",
        hierarchyLevel = 4
    );

    /**
     * Returns true if this role holds authority equal to or greater than the required role.
     */
    fun hasAuthorityOver(required: UserRole): Boolean = this.hierarchyLevel >= required.hierarchyLevel

    /**
     * Returns true if this role is permitted to export sync packages upward in the hierarchy.
     */
    val canExportPackages: Boolean get() = this != CENTRAL_SUPERVISOR
    /**
     * Returns true if this role is permitted to import and merge received sync packages.
     */
    val canImportPackages: Boolean get() = this != ENUMERATOR
    /**
     * Returns true if this role is permitted to provision (create) user accounts.
     */
    val canProvisionUsers: Boolean get() = this.hierarchyLevel >= GOVERNORATE_SUPERVISOR.hierarchyLevel
    companion object {
        /**
         * Resolves a UserRole from its stable code string, defaulting to ENUMERATOR
         * if the code is unrecognized (defensive fallback for legacy sync packages).
         */
        fun fromCode(code: String): UserRole =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENUMERATOR
    }
}
