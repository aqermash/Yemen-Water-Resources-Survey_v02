package com.yemen.watersurvey.presentation.form

import com.yemen.watersurvey.data.dao.AdminReferenceDao

/**
 * Authoritative production implementation of [AdminLookupProvider] backed by the independent
 * Administrative Reference Framework in Room database.
 *
 * Fully decoupled from Form Packages — administrative datasets are never bundled in packages.
 */
class RoomAdminLookupProvider(
    private val adminReferenceDao: AdminReferenceDao
) : AdminLookupProvider {

    override suspend fun getGovernorates(): List<AdminOption> {
        return adminReferenceDao.getAllActiveGovernorates().map {
            AdminOption(pcode = it.admin1Pcode, nameAr = it.nameAr, nameEn = it.nameEn)
        }
    }

    override suspend fun getDistricts(governoratePcode: String): List<AdminOption> {
        return adminReferenceDao.getDistrictsByGovernorate(governoratePcode).map {
            AdminOption(pcode = it.admin2Pcode, nameAr = it.nameAr, nameEn = it.nameEn)
        }
    }

    override suspend fun getUzlahs(districtPcode: String): List<AdminOption> {
        return adminReferenceDao.getUzlahsByDistrict(districtPcode).map {
            AdminOption(pcode = it.admin3Pcode, nameAr = it.nameAr, nameEn = it.nameEn)
        }
    }

    override suspend fun getVillages(uzlahPcode: String): List<AdminOption> {
        return adminReferenceDao.getVillagesByUzlah(uzlahPcode).map {
            AdminOption(pcode = it.villageId, nameAr = it.nameAr, nameEn = it.nameEn)
        }
    }
}
