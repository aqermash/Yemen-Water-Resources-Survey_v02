package com.yemen.watersurvey.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object PinLockManager {
    private const val PREFS_NAME = "pin_secure_prefs"
    private const val KEY_PIN_HASH = "pin_salted_hash"
    private const val KEY_SALT = "pin_salt"
    private const val PIN_LENGTH = 4
    private val secureRandom = SecureRandom()

    fun isPinSet(context: Context): Boolean {
        val prefs = getEncryptedPrefs(context)
        return prefs.contains(KEY_PIN_HASH)
    }

    fun setPin(context: Context, pin: String): Boolean {
        if (pin.length != PIN_LENGTH || !pin.all { it.isDigit() }) {
            return false
        }
        val salt = generateSalt()
        val saltedHash = hashPin(pin, salt)
        val prefs = getEncryptedPrefs(context)
        prefs.edit()
            .putString(KEY_SALT, Base64.getEncoder().encodeToString(salt))
            .putString(KEY_PIN_HASH, saltedHash)
            .apply()
        return true
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = getEncryptedPrefs(context)
        val saltString = prefs.getString(KEY_SALT, null) ?: return false
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = Base64.getDecoder().decode(saltString)
        val inputHash = hashPin(pin, salt)
        return inputHash == storedHash
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        return salt
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val hashBytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(hashBytes)
    }

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}