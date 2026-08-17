package com.yemen.watersurvey.core.admin

import com.yemen.watersurvey.data.dao.DeviceSequenceDao
import com.yemen.watersurvey.data.entity.DeviceSequencePoolEntity
import com.yemen.watersurvey.domain.model.SurveyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result container for generated registry codes.
 */
data class RegistryCodeResult(
    val registryCode: String,
    val isPending: Boolean
)

/**
 * Core engine responsible for generating official GIS Registry Codes in the format:
 * YE-<admin1Pcode><admin2Pcode><admin3Pcode>-<facilityType>-<sequence>
 * e.g. YE221501-WL-0001
 *
 * If offline sequence pool for bucket is missing or exhausted, assigns fallback:
 * YE221501-WL-PENDING-<uuidPrefix>
 */
class RegistryCodeGenerator(
    private val sequenceDao: DeviceSequenceDao
) {
    suspend fun generateRegistryCode(
        admin1Pcode: String,
        admin2Pcode: String,
        admin3Pcode: String,
        surveyType: SurveyType,
        surveyUUID: String
    ): RegistryCodeResult = withContext(Dispatchers.IO) {
        val cleanAdmin1 = admin1Pcode.removePrefix("YE").takeLast(2).ifEmpty { "00" }
        val cleanAdmin2 = admin2Pcode.removePrefix("YE").takeLast(2).ifEmpty { "00" }
        val cleanAdmin3 = admin3Pcode.removePrefix("YE").takeLast(2).ifEmpty { "00" }
        val adminBucketKey = "YE$cleanAdmin1$cleanAdmin2$cleanAdmin3"

        val typeCode = when (surveyType) {
            SurveyType.WELL -> "WL"
            SurveyType.SPRING -> "SP"
            SurveyType.DAM -> "WH"
        }

        val pool = sequenceDao.getPool(adminBucketKey, typeCode)

        if (pool != null && pool.currentNext <= pool.rangeEnd) {
            val seqNumber = pool.currentNext
            val formattedSeq = String.format("%04d", seqNumber)
            val code = "$adminBucketKey-$typeCode-$formattedSeq"

            // Increment pool in DB
            val updatedPool = pool.copy(
                currentNext = seqNumber + 1,
                lastAllocatedTimestamp = System.currentTimeMillis()
            )
            sequenceDao.insertOrUpdatePool(updatedPool)

            RegistryCodeResult(
                registryCode = code,
                isPending = false
            )
        } else {
            // Pool exhausted or unprovisioned -> PENDING fallback
            val uuidSnippet = if (surveyUUID.length >= 8) surveyUUID.substring(0, 8) else surveyUUID
            val pendingCode = "$adminBucketKey-$typeCode-PENDING-$uuidSnippet"

            RegistryCodeResult(
                registryCode = pendingCode,
                isPending = true
            )
        }
    }
}
