package com.yemen.watersurvey.domain.model

/**
 * Detailed mapping method provenance for village and administrative cross-mapping.
 */
enum class CrossMappingMethod(val code: String, val titleAr: String) {
    EXPLICIT_ID_LINK("EXPLICIT_ID_LINK", "مطابقة مباشرة عبر المعرفات"),
    PARENT_HIERARCHY_EXACT("PARENT_HIERARCHY_EXACT", "مطابقة دقيقة عبر التسلسل الهرمي والاسم"),
    ARABIC_NORMALIZATION("ARABIC_NORMALIZATION", "مطابقة بعد تسوية وتجريد النص العربي"),
    ENGLISH_NORMALIZATION("ENGLISH_NORMALIZATION", "مطابقة بعد تسوية النص الإنجليزي"),
    GEOGRAPHIC_PROXIMITY("GEOGRAPHIC_PROXIMITY", "مطابقة مكانية عبر الإحداثيات الجغرافية"),
    MANUAL_SUPERVISOR_REVIEW("MANUAL_SUPERVISOR_REVIEW", "مطابقة معتمدة بعد مراجعة المشرف"),
    UNRESOLVED("UNRESOLVED", "غير مطابق - لا توجد علاقة مؤكدة")
}

/**
 * Raw Yemen-Info entity representation for ingestion.
 */
data class RawYemenInfoRecord(
    val sourceId: String,
    val govNameAr: String,
    val distNameAr: String,
    val uzlahNameAr: String,
    val villageNameAr: String,
    val villageNameEn: String? = null,
    val villageNameArTashkeel: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

/**
 * Result of cross-mapping a village to canonical OCHA reference.
 */
data class VillageCrossMappingResult(
    val villageId: String,
    val nameAr: String,
    val nameEn: String,
    val nameArTashkeel: String,
    val originalSourceId: String,
    val mappedAdmin3Pcode: String?,
    val mappedAdmin2Pcode: String?,
    val mappedAdmin1Pcode: String?,
    val mappingStatus: MappingStatus,
    val mappingMethod: CrossMappingMethod,
    val confidenceScore: Double, // 0.0 to 1.0
    val provenanceNote: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

/**
 * Comprehensive cross-mapping statistics report.
 */
data class CrossMappingStatisticsReport(
    val totalYemenInfoVillages: Int,
    val mappedToOchaAdmin3: Int,
    val mappedConfirmed: Int,
    val mappedByHierarchy: Int,
    val mappedByGeometry: Int,
    val ambiguousCount: Int,
    val needsReviewCount: Int,
    val unmappedCount: Int,
    val ochaAdmin1Count: Int,
    val ochaAdmin2Count: Int,
    val ochaAdmin3Count: Int,
    val topoJsonAdmin1Count: Int,
    val topoJsonAdmin2Count: Int,
    val topoJsonAdmin3Count: Int,
    val generatedAt: String,
    val packageVersionTag: String
)

/**
 * Administrative Ingestion Summary & Validation Report.
 */
data class AdminIngestionValidationReport(
    val isSuccess: Boolean,
    val admin1Imported: Int,
    val admin1Expected: Int = 22,
    val admin2Imported: Int,
    val admin2Expected: Int = 333,
    val admin3Imported: Int,
    val admin3Expected: Int = 2146,
    val orphanAdmin2Count: Int,
    val orphanAdmin3Count: Int,
    val duplicatePcodesFound: List<String> = emptyList(),
    val malformedPcodesFound: List<String> = emptyList(),
    val validationMessages: List<String> = emptyList(),
    val executionTimestamp: String
)

/**
 * Production Reference Package Container.
 */
data class ProductionReferencePackageContainer(
    val metadata: AdminReferencePackageMetadata,
    val mappingStatistics: CrossMappingStatisticsReport,
    val validationReport: AdminIngestionValidationReport,
    val governorates: List<Admin1Governorate>,
    val districts: List<Admin2District>,
    val uzlahs: List<Admin3Uzlah>,
    val villages: List<AdminVillage>,
    val geometries: List<AdminMultiPolygon>,
    val packageSha256: String
)
