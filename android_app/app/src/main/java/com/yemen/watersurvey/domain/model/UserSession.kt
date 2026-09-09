package com.yemen.watersurvey.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Immutable snapshot of an active authenticated session.
 *
 * A UserSession is created upon successful authentication and remains valid until:
 * - Explicit logout is performed.
 * - The session timeout is exceeded.
 * - The app process is terminated (sessions are not persisted across launches in Phase 1).
 *
 * This model is intentionally minimal in Phase 1. Phase 2 will introduce
 * cryptographic session tokens and Phase 4 will add signing capabilities.
 *
 * @property sessionId Ephemeral UUID identifying this session instance (used in audit log).
 * @property user Authenticated user domain model (never contains sensitive credentials).
 * @property startedAt ISO timestamp when the session began.
 * @property lastActivityAt ISO timestamp of last user interaction (updated by session manager).
 * @property deviceId Hardware identifier of device running this session.
 * @property timeoutDurationMs Milliseconds of inactivity allowed before session expires.
 */
data class UserSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val user: User,
    val startedAt: String = currentIsoTimestamp(),
    val lastActivityAt: String = startedAt,
    val deviceId: String = "",
    val timeoutDurationMs: Long = DEFAULT_SESSION_TIMEOUT_MS
) {
    /**
     * Checks whether the session has expired due to inactivity.
     *
     * @param currentTimeMs Current epoch milliseconds (defaults to System.currentTimeMillis()).
     * @return true if elapsed time since lastActivityAt exceeds timeoutDurationMs.
     */
    fun isExpired(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val lastActivityMs = parseTimestampToMillis(lastActivityAt) ?: return false
        return (currentTimeMs - lastActivityMs) > timeoutDurationMs
    }

    /**
     * Creates an updated session snapshot reflecting recent user interaction.
     */
    fun withUpdatedActivity(timestampIso: String = currentIsoTimestamp()): UserSession {
        return copy(lastActivityAt = timestampIso)
    }

    companion object {
        /**
         * Default inactivity timeout: 30 minutes.
         */
        const val DEFAULT_SESSION_TIMEOUT_MS: Long = 30 * 60 * 1000L

        private fun currentIsoTimestamp(): String {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            return format.format(Date())
        }

        private fun parseTimestampToMillis(timestamp: String): Long? {
            return try {
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                isoFormat.parse(timestamp)?.time
            } catch (e: Exception) {
                try {
                    val fallbackFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    fallbackFormat.parse(timestamp)?.time
                } catch (e2: Exception) {
                    null
                }
            }
        }
    }
}
