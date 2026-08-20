package com.yemen.watersurvey.core.identity

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Single source of truth for anonymous Enumerator Identity management.
 * Guarantees zero manual name entry and stamps consistent `enumeratorCode`
 * across SurveyRecords, AuditLogs, and SyncPackages.
 */
class EnumeratorProfileManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Retrieves or auto-generates the canonical anonymous enumerator code (e.g. ENUM-YEM-8492).
     */
    fun getOrCreateEnumeratorCode(): String {
        val code = prefs.getString(KEY_ENUMERATOR_CODE, null)
        if (code.isNullOrBlank()) {
            val randomSuffix = UUID.randomUUID().toString().substring(0, 6).uppercase()
            val newCode = "ENUM-YEM-$randomSuffix"
            prefs.edit().putString(KEY_ENUMERATOR_CODE, newCode).apply()
            return newCode
        }
        return code
    }

    /**
     * Retrieves or auto-generates the hardware device identifier (e.g. DEV-ANDROID-A1B2).
     */
    fun getOrCreateDeviceId(): String {
        val deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId.isNullOrBlank()) {
            val randomSuffix = UUID.randomUUID().toString().substring(0, 8).uppercase()
            val newDeviceId = "DEV-YEM-$randomSuffix"
            prefs.edit().putString(KEY_DEVICE_ID, newDeviceId).apply()
            return newDeviceId
        }
        return deviceId
    }

    companion object {
        private const val PREFS_NAME = "yemen_water_survey_identity_prefs"
        private const val KEY_ENUMERATOR_CODE = "key_enumerator_code"
        private const val KEY_DEVICE_ID = "key_device_id"
    }
}
