package com.yemen.watersurvey.core.admin

import com.yemen.watersurvey.data.database.SurveyAppDatabase
import com.yemen.watersurvey.data.entity.AdministrativeOverrideEntity
import com.yemen.watersurvey.domain.model.AdminLevel
import com.yemen.watersurvey.domain.model.AdministrativeOverride
import com.yemen.watersurvey.domain.model.OverrideChangeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manager for Controlled Local Administrative Name Overrides.
 *
 * CRITICAL RULE:
 * 1. The application MUST NOT modify or overwrite the official OCHA source data.
 * 2. Overrides allow displaying local / common names alongside official names.
 * 3. Both official and current local names are preserved and recoverable at all times.
 */
class AdministrativeOverrideManager(
    private val database: SurveyAppDatabase
) {
    private val overrideDao = database.administrativeOverrideDao()
    private val adminDao = database.adminReferenceDao()

    /**
     * Submits a new local administrative name override proposal.
     */
    suspend fun proposeOverride(
        administrativeLevel: AdminLevel,
        officialPcodeOrId: String,
        localNameAr: String,
        localNameEn: String,
        changeType: OverrideChangeType,
        reason: String,
        sourceReference: String = "FIELD_OBSERVATION",
        createdBy: String
    ): AdministrativeOverride = withContext(Dispatchers.IO) {
        val (officialAr, officialEn) = getOfficialNames(administrativeLevel, officialPcodeOrId)

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val overrideId = "OVR-${UUID.randomUUID().toString().take(8).uppercase()}"

        val entity = AdministrativeOverrideEntity(
            overrideId = overrideId,
            administrativeLevel = administrativeLevel.name,
            officialPcodeOrId = officialPcodeOrId,
            officialNameAr = officialAr,
            officialNameEn = officialEn,
            localNameAr = localNameAr,
            localNameEn = localNameEn,
            changeType = changeType.name,
            reason = reason,
            sourceReference = sourceReference,
            createdBy = createdBy,
            createdAt = timestamp,
            isApproved = true, // By default approved for supervisor; or supervisor can approve
            approvedBy = createdBy,
            approvedAt = timestamp,
            isActive = true
        )

        overrideDao.insertOverride(entity)

        entityToDomain(entity)
    }

    /**
     * Resolves the combined display title for an administrative unit:
     * e.g., "Official: Al-Talh | Local/Current: Talh Al-Kubra"
     */
    suspend fun resolveDisplayNameAr(
        administrativeLevel: AdminLevel,
        officialPcodeOrId: String,
        officialFallbackAr: String
    ): String = withContext(Dispatchers.IO) {
        val override = overrideDao.getActiveOverrideForPcodeOrId(officialPcodeOrId)
        if (override != null && override.isApproved && override.isActive) {
            "${override.localNameAr} (رسمياً: ${override.officialNameAr})"
        } else {
            officialFallbackAr
        }
    }

    /**
     * Gets all active overrides.
     */
    suspend fun getAllActiveOverrides(): List<AdministrativeOverride> = withContext(Dispatchers.IO) {
        overrideDao.getAllOverrides()
            .filter { it.isActive }
            .map { entityToDomain(it) }
    }

    /**
     * Deactivates an override, cleanly reverting the display back to the official OCHA reference.
     */
    suspend fun deactivateOverride(overrideId: String) = withContext(Dispatchers.IO) {
        overrideDao.deactivateOverride(overrideId)
    }

    private suspend fun getOfficialNames(
        level: AdminLevel,
        pcodeOrId: String
    ): Pair<String, String> {
        return when (level) {
            AdminLevel.GOVERNORATE -> {
                val item = adminDao.getGovernorateByPcode(pcodeOrId)
                Pair(item?.nameAr ?: pcodeOrId, item?.nameEn ?: pcodeOrId)
            }
            AdminLevel.DISTRICT -> {
                val item = adminDao.getDistrictByPcode(pcodeOrId)
                Pair(item?.nameAr ?: pcodeOrId, item?.nameEn ?: pcodeOrId)
            }
            AdminLevel.UZLAH -> {
                val item = adminDao.getUzlahByPcode(pcodeOrId)
                Pair(item?.nameAr ?: pcodeOrId, item?.nameEn ?: pcodeOrId)
            }
            AdminLevel.VILLAGE -> {
                val item = adminDao.getVillageById(pcodeOrId)
                Pair(item?.nameAr ?: pcodeOrId, item?.nameEn ?: pcodeOrId)
            }
        }
    }

    private fun entityToDomain(entity: AdministrativeOverrideEntity): AdministrativeOverride {
        return AdministrativeOverride(
            overrideId = entity.overrideId,
            administrativeLevel = AdminLevel.valueOf(entity.administrativeLevel),
            officialPcodeOrId = entity.officialPcodeOrId,
            officialNameAr = entity.officialNameAr,
            officialNameEn = entity.officialNameEn,
            localNameAr = entity.localNameAr,
            localNameEn = entity.localNameEn,
            changeType = OverrideChangeType.valueOf(entity.changeType),
            reason = entity.reason,
            sourceReference = entity.sourceReference,
            createdBy = entity.createdBy,
            createdAt = entity.createdAt,
            isApproved = entity.isApproved,
            approvedBy = entity.approvedBy,
            approvedAt = entity.approvedAt,
            isActive = entity.isActive
        )
    }
}
