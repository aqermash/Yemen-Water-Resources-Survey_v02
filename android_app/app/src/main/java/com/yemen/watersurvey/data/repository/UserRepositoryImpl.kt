package com.yemen.watersurvey.data.repository

import com.yemen.watersurvey.data.dao.UserDao
import com.yemen.watersurvey.data.mapper.UserMapper
import com.yemen.watersurvey.data.mapper.toDomain
import com.yemen.watersurvey.domain.model.AuthenticationResult
import com.yemen.watersurvey.domain.model.User
import com.yemen.watersurvey.domain.model.UserRole
import com.yemen.watersurvey.domain.model.UserSession
import com.yemen.watersurvey.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*

/**
 * Concrete implementation of [UserRepository] handling Room persistence,
 * secure PIN cryptographic hashing, and thread-safe in-memory session management.
 *
 * Security Architecture:
 * - Passwords and PINs are salted with a 16-byte cryptographically secure random salt.
 * - Hashed with SHA-256 and stored as Base64.
 * - Verification uses constant-time comparison to protect against timing attacks.
 * - Sessions are held in-memory and invalidated upon logout or process death.
 */
class UserRepositoryImpl(
    private val userDao: UserDao
) : UserRepository {

    private val secureRandom = SecureRandom()

    private val _currentSession = MutableStateFlow<UserSession?>(null)
    override fun observeCurrentSession(): Flow<UserSession?> = _currentSession.asStateFlow()

    override fun observeAllUsers(): Flow<List<User>> {
        return userDao.getAllUsersFlow().map { list ->
            list.map { it.toDomain() }
        }.flowOn(Dispatchers.IO)
    }

    override fun observeActiveUsers(): Flow<List<User>> {
        return userDao.getActiveUsersFlow().map { list ->
            list.map { it.toDomain() }
        }.flowOn(Dispatchers.IO)
    }

    override fun observeUserById(userId: String): Flow<User?> {
        return userDao.getUserByIdFlow(userId).map { it?.toDomain() }.flowOn(Dispatchers.IO)
    }

    override fun observeUserByUsername(username: String): Flow<User?> {
        return userDao.getUserByUsernameFlow(username).map { it?.toDomain() }.flowOn(Dispatchers.IO)
    }

    override suspend fun getUserById(userId: String): User? = withContext(Dispatchers.IO) {
        userDao.getUserById(userId)?.toDomain()
    }

    override suspend fun getUserByUsername(username: String): User? = withContext(Dispatchers.IO) {
        userDao.getUserByUsername(username.trim().lowercase())?.toDomain()
    }

    override suspend fun getUsersByRole(role: UserRole): List<User> = withContext(Dispatchers.IO) {
        userDao.getUsersByRole(role.code).map { it.toDomain() }
    }

    override suspend fun getUsersByAdministrativeArea(
        governorateCode: String?,
        districtCode: String?
    ): List<User> = withContext(Dispatchers.IO) {
        userDao.getUsersByAdministrativeArea(governorateCode, districtCode).map { it.toDomain() }
    }

    override suspend fun authenticate(
        username: String,
        pin: String,
        currentDeviceId: String
    ): AuthenticationResult = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim().lowercase()
        if (cleanUsername.isBlank()) {
            return@withContext AuthenticationResult.ValidationError("يرجى إدخال اسم المستخدم.")
        }
        if (pin.isBlank() || pin.length < 4) {
            return@withContext AuthenticationResult.ValidationError("يرجى إدخال رمز PIN المكون من 4 أرقام على الأقل.")
        }

        try {
            val userEntity = userDao.getUserByUsername(cleanUsername)
                ?: return@withContext AuthenticationResult.InvalidCredentials()

            if (!userEntity.isActive) {
                return@withContext AuthenticationResult.AccountDisabled()
            }

            // Verify device binding if enforced on account
            if (!userEntity.assignedDeviceId.isNullOrBlank() && currentDeviceId.isNotBlank()) {
                if (userEntity.assignedDeviceId != currentDeviceId) {
                    return@withContext AuthenticationResult.DeviceMismatch(
                        expectedDeviceId = userEntity.assignedDeviceId,
                        actualDeviceId = currentDeviceId
                    )
                }
            }

            // Verify PIN hash using salted hash
            val salt = try {
                Base64.getDecoder().decode(userEntity.pinSalt)
            } catch (e: Exception) {
                return@withContext AuthenticationResult.HardwareError(cause = e)
            }

            val inputHash = hashPin(pin, salt)
            if (!constantTimeEquals(inputHash, userEntity.pinSaltedHash)) {
                return@withContext AuthenticationResult.InvalidCredentials()
            }

            // Authentication successful: update login timestamp and auto-bind device if first login
            val nowIso = currentIsoTimestamp()
            userDao.updateLastLogin(userEntity.userId, nowIso)

            if (userEntity.assignedDeviceId.isNullOrBlank() && currentDeviceId.isNotBlank()) {
                userDao.updateAssignedDevice(userEntity.userId, currentDeviceId)
            }

            val domainUser = userEntity.toDomain().copy(
                lastLoginAt = nowIso,
                assignedDeviceId = userEntity.assignedDeviceId ?: currentDeviceId.ifBlank { null }
            )

            val session = UserSession(
                user = domainUser,
                startedAt = nowIso,
                lastActivityAt = nowIso,
                deviceId = currentDeviceId
            )

            _currentSession.value = session
            AuthenticationResult.Success(session)
        } catch (e: Exception) {
            AuthenticationResult.HardwareError(cause = e)
        }
    }

    override suspend fun provisionUser(user: User, initialPin: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            val cleanUsername = user.username.trim().lowercase()
            if (cleanUsername.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("اسم المستخدم لا يمكن أن يكون فارغاً."))
            }
            if (user.fullNameAr.trim().isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("الاسم الكامل بالعربية مطلوب."))
            }
            if (initialPin.length < 4 || !initialPin.all { it.isDigit() }) {
                return@withContext Result.failure(IllegalArgumentException("رمز PIN يجب أن يتكون من 4 أرقام على الأقل."))
            }

            val existing = userDao.getUserByUsername(cleanUsername)
            if (existing != null) {
                return@withContext Result.failure(IllegalStateException("اسم المستخدم '$cleanUsername' مسجل بالفعل."))
            }

            val salt = generateSalt()
            val saltedHash = hashPin(initialPin, salt)
            val saltBase64 = Base64.getEncoder().encodeToString(salt)

            val nowIso = currentIsoTimestamp()
            val preparedUser = user.copy(
                userId = if (user.userId.isBlank()) UUID.randomUUID().toString() else user.userId,
                username = cleanUsername,
                provisionedAt = if (user.provisionedAt.isBlank()) nowIso else user.provisionedAt
            )

            val entity = UserMapper.toEntity(
                user = preparedUser,
                pinSalt = saltBase64,
                pinSaltedHash = saltedHash
            )

            userDao.insertOrUpdateUser(entity)
            Result.success(preparedUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateUser(user: User): Result<User> = withContext(Dispatchers.IO) {
        try {
            val existing = userDao.getUserById(user.userId)
                ?: return@withContext Result.failure(NoSuchElementException("المستخدم غير موجود."))

            val updatedEntity = UserMapper.updateEntityFromDomain(existing, user)
            userDao.insertOrUpdateUser(updatedEntity)

            // If active session belongs to this user, update in-memory session user snapshot
            val activeSession = _currentSession.value
            if (activeSession != null && activeSession.user.userId == user.userId) {
                _currentSession.value = activeSession.copy(user = user)
            }

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun changePin(userId: String, newPin: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (newPin.length < 4 || !newPin.all { it.isDigit() }) {
                return@withContext Result.failure(IllegalArgumentException("رمز PIN الجديد يجب أن يتكون من 4 أرقام على الأقل."))
            }

            if (userDao.getUserById(userId) == null) {
                return@withContext Result.failure(NoSuchElementException("المستخدم غير موجود."))
            }

            val newSalt = generateSalt()
            val newSaltedHash = hashPin(newPin, newSalt)
            val saltBase64 = Base64.getEncoder().encodeToString(newSalt)

            userDao.updateCredentials(userId, saltBase64, newSaltedHash)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setUserActiveStatus(userId: String, isActive: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            userDao.updateUserActiveStatus(userId, isActive)

            // If user was deactivated, terminate their active session immediately
            if (!isActive && _currentSession.value?.user?.userId == userId) {
                _currentSession.value = null
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun bindDevice(userId: String, deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            userDao.updateAssignedDevice(userId, deviceId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteUser(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val deletedCount = userDao.deleteUserById(userId)
            if (deletedCount == 0) {
                return@withContext Result.failure(NoSuchElementException("المستخدم غير موجود."))
            }

            if (_currentSession.value?.user?.userId == userId) {
                _currentSession.value = null
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserCount(): Int = withContext(Dispatchers.IO) {
        userDao.getUserCount()
    }

    override fun getCurrentSession(): UserSession? {
        val session = _currentSession.value ?: return null
        if (session.isExpired()) {
            _currentSession.value = null
            return null
        }
        return session
    }

    override fun touchSession(): Boolean {
        val session = _currentSession.value ?: return false
        if (session.isExpired()) {
            _currentSession.value = null
            return false
        }
        _currentSession.value = session.withUpdatedActivity()
        return true
    }

    override fun logout() {
        _currentSession.value = null
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

    /**
     * Constant-time string comparison to defend against timing attacks.
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        val aBytes = a.toByteArray(Charsets.UTF_8)
        val bBytes = b.toByteArray(Charsets.UTF_8)
        if (aBytes.size != bBytes.size) return false
        var result = 0
        for (i in aBytes.indices) {
            result = result or (aBytes[i].toInt() xor bBytes[i].toInt())
        }
        return result == 0
    }

    private fun currentIsoTimestamp(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        return format.format(Date())
    }
}
