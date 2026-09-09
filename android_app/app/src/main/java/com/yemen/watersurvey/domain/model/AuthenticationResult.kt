package com.yemen.watersurvey.domain.model

/**
 * Sealed class representing comprehensive operational outcomes for authentication.
 *
 * Designed to support granular error reporting, defensive security feedback,
 * and clear user-facing Arabic error resolution in UI workflows.
 */
sealed class AuthenticationResult {

    /**
     * Authentication succeeded and an active session was initialized.
     *
     * @property session The active authenticated user session.
     */
    data class Success(val session: UserSession) : AuthenticationResult()

    /**
     * Authentication failed because the username does not exist or the PIN/credentials were incorrect.
     *
     * @property messageAr Arabic localized explanation for UI display.
     * @property remainingAttempts Number of retry attempts remaining before temporary lockout (if applicable).
     */
    data class InvalidCredentials(
        val messageAr: String = "اسم المستخدم أو رمز PIN غير صحيح.",
        val remainingAttempts: Int? = null
    ) : AuthenticationResult()

    /**
     * Authentication rejected because the account has been deactivated or suspended by a supervisor.
     *
     * @property messageAr Arabic localized explanation for UI display.
     * @property reason Optional administrative suspension reason.
     */
    data class AccountDisabled(
        val messageAr: String = "هذا الحساب معطل حالياً. يرجى التواصل مع المشرف المختص.",
        val reason: String? = null
    ) : AuthenticationResult()

    /**
     * Authentication rejected because the account is bound to a specific hardware device identifier
     * and the current device does not match.
     *
     * @property expectedDeviceId Bound hardware identifier.
     * @property actualDeviceId Current hardware identifier attempting authentication.
     * @property messageAr Arabic localized explanation for UI display.
     */
    data class DeviceMismatch(
        val expectedDeviceId: String,
        val actualDeviceId: String,
        val messageAr: String = "هذا الحساب مقترن بجهاز آخر ولا يمكن تسجيل الدخول من هذا الجهاز."
    ) : AuthenticationResult()

    /**
     * Authentication failed due to cryptographic hardware error, keystore corruption, or platform failure.
     *
     * @property messageAr Arabic localized explanation for UI display.
     * @property cause Underlying exception if available.
     */
    data class HardwareError(
        val messageAr: String = "حدث خطأ أثناء الوصول إلى وحدة الأمان أو التخزين المشفر.",
        val cause: Throwable? = null
    ) : AuthenticationResult()

    /**
     * Authentication failed due to missing inputs (e.g. blank username or incomplete PIN).
     *
     * @property messageAr Arabic localized explanation for UI display.
     */
    data class ValidationError(
        val messageAr: String
    ) : AuthenticationResult()
}
