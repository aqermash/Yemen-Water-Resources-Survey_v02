package com.yemen.watersurvey.core.admin

import com.yemen.watersurvey.data.database.SurveyAppDatabase
import com.yemen.watersurvey.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service for Cascading Administrative Selection:
 * Governorate (Admin1) -> District (Admin2) -> Uzlah (Admin3) -> Village
 *
 * Enforces strict P-code hierarchy filtering with lazy query loading
 * to prevent high memory consumption from large village tables.
 */
class AdminCascadingSelector(
    private val database: SurveyAppDatabase,
    private val overrideManager: AdministrativeOverrideManager = AdministrativeOverrideManager(database)
) {
    private val adminDao = database.adminReferenceDao()

    /**
     * Step 1: Load all 22 active Governorates.
     */
    suspend fun getGovernorates(): List<Admin1Governorate> = withContext(Dispatchers.IO) {
        adminDao.getAllActiveGovernorates().map {
            Admin1Governorate(
                admin1Pcode = it.admin1Pcode,
                nameAr = it.nameAr,
                nameEn = it.nameEn,
                nameArTashkeel = it.nameArTashkeel,
                source = AdminSource.valueOf(it.source),
                isActive = it.isActive,
                versionTag = it.versionTag
            )
        }
    }

    /**
     * Step 2: Load Districts restricted to the selected Governorate P-code (e.g. "YE11").
     */
    suspend fun getDistrictsForGovernorate(admin1Pcode: String): List<Admin2District> = withContext(Dispatchers.IO) {
        adminDao.getDistrictsByGovernorate(admin1Pcode).map {
            Admin2District(
                admin2Pcode = it.admin2Pcode,
                admin1Pcode = it.admin1Pcode,
                nameAr = it.nameAr,
                nameEn = it.nameEn,
                nameArTashkeel = it.nameArTashkeel,
                source = AdminSource.valueOf(it.source),
                isActive = it.isActive,
                versionTag = it.versionTag
            )
        }
    }

    /**
     * Step 3: Load Sub-districts (Uzlahs) restricted to the selected District P-code (e.g. "YE1101").
     */
    suspend fun getUzlahsForDistrict(admin2Pcode: String): List<Admin3Uzlah> = withContext(Dispatchers.IO) {
        adminDao.getUzlahsByDistrict(admin2Pcode).map {
            Admin3Uzlah(
                admin3Pcode = it.admin3Pcode,
                admin2Pcode = it.admin2Pcode,
                admin1Pcode = it.admin1Pcode,
                nameAr = it.nameAr,
                nameEn = it.nameEn,
                nameArTashkeel = it.nameArTashkeel,
                source = AdminSource.valueOf(it.source),
                isActive = it.isActive,
                versionTag = it.versionTag
            )
        }
    }

    /**
     * Alias conforming to technical terminology "Sub-district".
     */
    suspend fun getSubDistrictsForDistrict(admin2Pcode: String): List<Admin3Uzlah> = getUzlahsForDistrict(admin2Pcode)

    /**
     * Step 4: Load Villages restricted to the selected Sub-district P-code (e.g. "YE110101").
     */
    suspend fun getVillagesForUzlah(admin3Pcode: String): List<AdminVillage> = withContext(Dispatchers.IO) {
        adminDao.getVillagesByUzlah(admin3Pcode).map {
            AdminVillage(
                villageId = it.villageId,
                admin3Pcode = it.admin3Pcode,
                admin2Pcode = it.admin2Pcode,
                admin1Pcode = it.admin1Pcode,
                nameAr = it.nameAr,
                nameEn = it.nameEn,
                nameArTashkeel = it.nameArTashkeel,
                latitude = it.latitude,
                longitude = it.longitude,
                source = AdminSource.valueOf(it.source),
                mappingStatus = MappingStatus.valueOf(it.mappingStatus),
                versionTag = it.versionTag
            )
        }
    }

    /**
     * Alias conforming to technical terminology "Sub-district".
     */
    suspend fun getVillagesForSubDistrict(admin3Pcode: String): List<AdminVillage> = getVillagesForUzlah(admin3Pcode)

    /**
     * Look up single Governorate by P-code.
     */
    suspend fun getGovernorate(admin1Pcode: String): Admin1Governorate? = withContext(Dispatchers.IO) {
        adminDao.getGovernorateByPcode(admin1Pcode)?.let {
            Admin1Governorate(
                admin1Pcode = it.admin1Pcode,
                nameAr = it.nameAr,
                nameEn = it.nameEn,
                nameArTashkeel = it.nameArTashkeel,
                source = AdminSource.valueOf(it.source),
                isActive = it.isActive,
                versionTag = it.versionTag
            )
        }
    }

    /**
     * Look up single District by P-code.
     */
    suspend fun getDistrict(admin2Pcode: String): Admin2District? = withContext(Dispatchers.IO) {
        adminDao.getDistrictByPcode(admin2Pcode)?.let {
            Admin2District(
                admin2Pcode = it.admin2Pcode,
                admin1Pcode = it.admin1Pcode,
                nameAr = it.nameAr,
                nameEn = it.nameEn,
                nameArTashkeel = it.nameArTashkeel,
                source = AdminSource.valueOf(it.source),
                isActive = it.isActive,
                versionTag = it.versionTag
            )
        }
    }

    /**
     * Look up single Sub-district (Uzlah) by P-code.
     */
    suspend fun getSubDistrict(admin3Pcode: String): Admin3Uzlah? = withContext(Dispatchers.IO) {
        adminDao.getUzlahByPcode(admin3Pcode)?.let {
            Admin3Uzlah(
                admin3Pcode = it.admin3Pcode,
                admin2Pcode = it.admin2Pcode,
                admin1Pcode = it.admin1Pcode,
                nameAr = it.nameAr,
                nameEn = it.nameEn,
                nameArTashkeel = it.nameArTashkeel,
                source = AdminSource.valueOf(it.source),
                isActive = it.isActive,
                versionTag = it.versionTag
            )
        }
    }

    /**
     * Look up single Village by ID.
     */
    suspend fun getVillage(villageId: String): AdminVillage? = withContext(Dispatchers.IO) {
        adminDao.getVillageById(villageId)?.let {
            AdminVillage(
                villageId = it.villageId,
                admin3Pcode = it.admin3Pcode,
                admin2Pcode = it.admin2Pcode,
                admin1Pcode = it.admin1Pcode,
                nameAr = it.nameAr,
                nameEn = it.nameEn,
                nameArTashkeel = it.nameArTashkeel,
                latitude = it.latitude,
                longitude = it.longitude,
                source = AdminSource.valueOf(it.source),
                mappingStatus = MappingStatus.valueOf(it.mappingStatus),
                versionTag = it.versionTag
            )
        }
    }

    /**
     * Builds an immutable administrative location snapshot for a survey record.
     * Preserves Arabic administrative names at the time of data collection.
     */
    suspend fun createAdministrativeSnapshot(
        admin1Pcode: String,
        admin2Pcode: String,
        admin3Pcode: String,
        villageReferenceId: String? = null,
        customVillageNameAr: String? = null,
        isLocalNameOverride: Boolean = false,
        localOverrideId: String? = null
    ): AdministrativeLocationSnapshot = withContext(Dispatchers.IO) {
        val gov = getGovernorate(admin1Pcode)
        val dist = getDistrict(admin2Pcode)
        val subDist = getSubDistrict(admin3Pcode)
        val village = villageReferenceId?.let { getVillage(it) }

        val govName = gov?.nameAr ?: admin1Pcode
        val distName = dist?.nameAr ?: admin2Pcode
        val subDistName = subDist?.nameAr ?: admin3Pcode
        val villageName = when {
            !customVillageNameAr.isNullOrBlank() -> customVillageNameAr.trim()
            village != null -> village.nameAr
            else -> null
        }

        AdministrativeLocationSnapshot(
            admin1Pcode = admin1Pcode,
            admin2Pcode = admin2Pcode,
            admin3Pcode = admin3Pcode,
            governorateNameAr = govName,
            districtNameAr = distName,
            subDistrictNameAr = subDistName,
            villageReferenceId = villageReferenceId,
            villageNameAr = villageName,
            isLocalNameOverride = isLocalNameOverride,
            localOverrideId = localOverrideId,
            adminRefVersionTag = gov?.versionTag ?: "OCHA_YEM_2024_V1"
        )
    }

    /**
     * Searches villages across the database with text search.
     */
    suspend fun searchVillages(query: String): List<AdminVillage> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        adminDao.searchVillages(query.trim()).map {
            AdminVillage(
                villageId = it.villageId,
                admin3Pcode = it.admin3Pcode,
                admin2Pcode = it.admin2Pcode,
                admin1Pcode = it.admin1Pcode,
                nameAr = it.nameAr,
                nameEn = it.nameEn,
                nameArTashkeel = it.nameArTashkeel,
                latitude = it.latitude,
                longitude = it.longitude,
                source = AdminSource.valueOf(it.source),
                mappingStatus = MappingStatus.valueOf(it.mappingStatus),
                versionTag = it.versionTag
            )
        }
    }

    /**
     * Gets unmapped villages requiring review.
     */
    suspend fun getUnmappedOrReviewVillages(limit: Int = 50): List<AdminVillage> = withContext(Dispatchers.IO) {
        adminDao.getUnmappedOrReviewVillages(limit).map {
            AdminVillage(
                villageId = it.villageId,
                admin3Pcode = it.admin3Pcode,
                admin2Pcode = it.admin2Pcode,
                admin1Pcode = it.admin1Pcode,
                nameAr = it.nameAr,
                nameEn = it.nameEn,
                nameArTashkeel = it.nameArTashkeel,
                latitude = it.latitude,
                longitude = it.longitude,
                source = AdminSource.valueOf(it.source),
                mappingStatus = MappingStatus.valueOf(it.mappingStatus),
                versionTag = it.versionTag
            )
        }
    }
}
