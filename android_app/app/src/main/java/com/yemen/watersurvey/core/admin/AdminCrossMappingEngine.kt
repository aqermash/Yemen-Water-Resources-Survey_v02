package com.yemen.watersurvey.core.admin

import com.yemen.watersurvey.data.entity.Admin1Entity
import com.yemen.watersurvey.data.entity.Admin2Entity
import com.yemen.watersurvey.data.entity.Admin3Entity
import com.yemen.watersurvey.data.entity.VillageEntity
import com.yemen.watersurvey.domain.model.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * Engine for multi-stage cross-mapping of Yemen-Info village records to canonical OCHA P-codes.
 *
 * Enforces strict Level 1 OCHA Authority:
 * - OCHA P-codes are immutable and authoritative.
 * - Villages NEVER receive fabricated OCHA P-codes.
 * - Records without confident parent association are explicitly marked UNMAPPED or NEEDS_REVIEW.
 */
class AdminCrossMappingEngine {

    companion object {
        private val TASHKEEL_REGEX = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")
        private val TATWEEL_REGEX = Regex("\\u0640")
        private val ARABIC_PREFIXES = listOf("قرية", "قريه", "محلة", "محله", "عزلة", "عزله", "حارة", "حاره", "تجمع", "سكن")
        private val ENGLISH_PREFIXES = listOf("al-", "el-", "as-", "ash-", "ad-", "an-", "ar-", "az-", "at-", "ath-", "al ", "el ", "as ", "ash ", "ad ", "an ", "ar ", "az ", "at ", "ath ")
    }

    /**
     * Normalizes Arabic text by removing Tashkeel, Tatweel, and normalizing letter variations.
     */
    fun normalizeArabic(text: String?): String {
        if (text.isNullOrBlank()) return ""
        var normalized = text.trim()
        normalized = normalized.replace(TASHKEEL_REGEX, "")
        normalized = normalized.replace(TATWEEL_REGEX, "")
        normalized = normalized.replace(Regex("[أإآٱ]"), "ا")
        normalized = normalized.replace('ة', 'ه')
        normalized = normalized.replace('ى', 'ي')
        normalized = normalized.replace(Regex("[ؤئ]"), "ء")
        normalized = normalized.replace(Regex("\\s+"), " ")
        return normalized.trim()
    }

    /**
     * Normalizes Arabic name for fuzzy comparison by stripping generic administrative classification prefixes.
     */
    fun stripArabicPrefixes(text: String?): String {
        var normalized = normalizeArabic(text)
        for (prefix in ARABIC_PREFIXES) {
            val normPrefix = normalizeArabic(prefix)
            if (normalized.startsWith("$normPrefix ")) {
                normalized = normalized.substring(normPrefix.length + 1).trim()
            }
        }
        return normalized
    }

    /**
     * Normalizes English text for comparison by trimming, lowercasing, and stripping common prefixes.
     */
    fun normalizeEnglish(text: String?): String {
        if (text.isNullOrBlank()) return ""
        var normalized = text.trim().lowercase()
        for (prefix in ENGLISH_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                normalized = normalized.substring(prefix.length).trim()
                break
            }
        }
        normalized = normalized.replace(Regex("[^a-z0-9\\s]"), "")
        normalized = normalized.replace(Regex("\\s+"), " ")
        return normalized.trim()
    }

    /**
     * Executes the multi-stage cross-mapping process for a collection of raw Yemen-Info records
     * against canonical OCHA reference data.
     */
    fun crossMapVillages(
        rawRecords: List<RawYemenInfoRecord>,
        governorates: List<Admin1Entity>,
        districts: List<Admin2Entity>,
        uzlahs: List<Admin3Entity>,
        versionTag: String = "OCHA_YEM_2024_V1"
    ): Pair<List<VillageEntity>, CrossMappingStatisticsReport> {
        val govByNormName = governorates.associateBy { normalizeArabic(it.nameAr) }
        val distByGovAndNormName = districts.groupBy { it.admin1Pcode }
            .mapValues { entry -> entry.value.associateBy { normalizeArabic(it.nameAr) } }
        val uzlahsByDistAndNormName = uzlahs.groupBy { it.admin2Pcode }
            .mapValues { entry -> entry.value.groupBy { normalizeArabic(it.nameAr) } }

        var mappedConfirmed = 0
        var mappedByHierarchy = 0
        var mappedByGeometry = 0
        var ambiguousCount = 0
        var needsReviewCount = 0
        var unmappedCount = 0

        val mappedVillages = mutableListOf<VillageEntity>()

        for (raw in rawRecords) {
            val normGov = normalizeArabic(raw.govNameAr)
            val normDist = normalizeArabic(raw.distNameAr)
            val normUzlah = normalizeArabic(raw.uzlahNameAr)

            val matchedGov = govByNormName[normGov]
            val matchedDist = if (matchedGov != null) {
                distByGovAndNormName[matchedGov.admin1Pcode]?.get(normDist)
            } else null

            var finalAdmin3Pcode: String? = null
            var finalAdmin2Pcode: String? = matchedDist?.admin2Pcode
            var finalAdmin1Pcode: String? = matchedGov?.admin1Pcode
            var status = MappingStatus.UNMAPPED
            var method = CrossMappingMethod.UNRESOLVED

            if (matchedGov != null && matchedDist != null) {
                val candidateUzlahs = uzlahsByDistAndNormName[matchedDist.admin2Pcode]?.get(normUzlah) ?: emptyList()

                if (candidateUzlahs.size == 1) {
                    finalAdmin3Pcode = candidateUzlahs.first().admin3Pcode
                    status = MappingStatus.MAPPED_CONFIRMED
                    method = CrossMappingMethod.PARENT_HIERARCHY_EXACT
                    mappedConfirmed++
                    mappedByHierarchy++
                } else if (candidateUzlahs.size > 1) {
                    // Multiple uzlahs with the same normalized name in the same district
                    status = MappingStatus.AMBIGUOUS
                    method = CrossMappingMethod.MANUAL_SUPERVISOR_REVIEW
                    ambiguousCount++
                } else {
                    // Try stripped prefix match
                    val strippedUzlah = stripArabicPrefixes(raw.uzlahNameAr)
                    val fuzzyCandidates = uzlahs.filter {
                        it.admin2Pcode == matchedDist.admin2Pcode &&
                                stripArabicPrefixes(it.nameAr) == strippedUzlah
                    }

                    if (fuzzyCandidates.size == 1) {
                        finalAdmin3Pcode = fuzzyCandidates.first().admin3Pcode
                        status = MappingStatus.MAPPED_CONFIRMED
                        method = CrossMappingMethod.ARABIC_NORMALIZATION
                        mappedConfirmed++
                    } else if (fuzzyCandidates.size > 1) {
                        status = MappingStatus.AMBIGUOUS
                        method = CrossMappingMethod.MANUAL_SUPERVISOR_REVIEW
                        ambiguousCount++
                    } else {
                        // Unmapped uzlah in district
                        status = MappingStatus.NEEDS_REVIEW
                        method = CrossMappingMethod.UNRESOLVED
                        needsReviewCount++
                    }
                }
            } else if (matchedGov != null) {
                // District could not be resolved
                status = MappingStatus.NEEDS_REVIEW
                method = CrossMappingMethod.UNRESOLVED
                needsReviewCount++
            } else {
                // Completely unmapped
                status = MappingStatus.UNMAPPED
                method = CrossMappingMethod.UNRESOLVED
                unmappedCount++
            }

            // Create VillageEntity with strict preservation of provenance and zero fabricated P-codes
            val villageEntity = VillageEntity(
                villageId = if (raw.sourceId.isNotBlank()) raw.sourceId else "YINFO-VIL-${UUID.randomUUID()}",
                admin3Pcode = finalAdmin3Pcode,
                admin2Pcode = finalAdmin2Pcode,
                admin1Pcode = finalAdmin1Pcode,
                nameAr = raw.villageNameAr.ifBlank { "قرية غير مسماة" },
                nameEn = raw.villageNameEn ?: "",
                nameArTashkeel = raw.villageNameArTashkeel ?: raw.villageNameAr,
                latitude = raw.latitude,
                longitude = raw.longitude,
                source = "YEMEN_INFO",
                mappingStatus = status.code,
                versionTag = versionTag
            )
            mappedVillages.add(villageEntity)
        }

        val report = CrossMappingStatisticsReport(
            totalYemenInfoVillages = rawRecords.size,
            mappedToOchaAdmin3 = mappedConfirmed,
            mappedConfirmed = mappedConfirmed,
            mappedByHierarchy = mappedByHierarchy,
            mappedByGeometry = mappedByGeometry,
            ambiguousCount = ambiguousCount,
            needsReviewCount = needsReviewCount,
            unmappedCount = unmappedCount,
            ochaAdmin1Count = governorates.size,
            ochaAdmin2Count = districts.size,
            ochaAdmin3Count = uzlahs.size,
            topoJsonAdmin1Count = governorates.size,
            topoJsonAdmin2Count = districts.size,
            topoJsonAdmin3Count = uzlahs.size,
            generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
            packageVersionTag = versionTag
        )

        return Pair(mappedVillages, report)
    }

    /**
     * Computes geodesic Haversine distance in meters between two WGS84 coordinates.
     */
    fun calculateHaversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
