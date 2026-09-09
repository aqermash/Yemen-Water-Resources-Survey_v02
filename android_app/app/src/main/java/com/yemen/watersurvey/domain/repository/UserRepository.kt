package com.yemen.watersurvey.domain.repository

import com.yemen.watersurvey.domain.model.AuthenticationResult
import com.yemen.watersurvey.domain.model.User
import com.yemen.watersurvey.domain.model.UserRole
import com.yemen.watersurvey.domain.model.UserSession
import kotlinx.coroutines.flow.Flow

/**
 * Domain Repository interface defining all user management, authentication,
 * session tracking, and provisioning operations.
 *
 * Implements Clean Architecture principles:
 * - Domain layer is completely independent of Android framework, Room, or SQLite dependencies.
 * - All operations return immutable domain models or sealed operation results.
 */
interface UserRepository {

    /**
     * Observes the complete list of users registered on this device.
     */
    fun observeAllUsers(): Flow<List<User>>

    /**
     * Observes the list of currently active users.
     */
    fun observeActiveUsers(): Flow<List<User>>

    /**
     * Observes a specific user by their unique userId.
     */
    fun observeUserById(userId: String): Flow<User?>

    /**
     * Observes a specific user by their username.
     */
    fun observeUserByUsername(username: String): Flow<User?>

    /**
     * Looks up a user by userId.
     */
    suspend fun getUserById(userId: String): User?

    /**
     * Looks up a user by username.
     */
    suspend fun getUserByUsername(username: String): User?

    /**
     * Retrieves all users matching a specific hierarchical role.
     */
    suspend fun getUsersByRole(role: UserRole): List<User>

    /**
     * Retrieves users scoped to an administrative area.
     */
    suspend fun getUsersByAdministrativeArea(
        governorateCode: String?,
        districtCode: String?
    ): List<User>

    /**
     * Authenticates a user using their username and PIN.
     *
     * @param username The unique login username.
     * @param pin The 4-to-6 digit numeric PIN.
     * @param currentDeviceId The device identifier requesting authentication (for device binding validation).
     * @return [AuthenticationResult] reflecting operation outcome.
     */
    suspend fun authenticate(
        username: String,
        pin: String,
        currentDeviceId: String
    ): AuthenticationResult

    /**
     * Provisions a new user account with initial PIN credentials.
     *
     * @param user The domain user model to create.
     * @param initialPin The initial PIN to set for this account.
     * @return Result indicating success with the created user, or error with message.
     */
    suspend fun provisionUser(user: User, initialPin: String): Result<User>

    /**
     * Updates an existing user's profile details.
     */
    suspend fun updateUser(user: User): Result<User>

    /**
     * Changes or resets a user's PIN.
     *
     * @param userId The unique userId.
     * @param newPin The new numeric PIN to set.
     */
    suspend fun changePin(userId: String, newPin: String): Result<Unit>

    /**
     * Updates the active status of a user (activate or deactivate).
     */
    suspend fun setUserActiveStatus(userId: String, isActive: Boolean): Result<Unit>

    /**
     * Binds a user account to a specific hardware device identifier.
     */
    suspend fun bindDevice(userId: String, deviceId: String): Result<Unit>

    /**
     * Deletes a user account.
     */
    suspend fun deleteUser(userId: String): Result<Unit>

    /**
     * Returns the total count of registered users on this device.
     */
    suspend fun getUserCount(): Int

    /**
     * Observes the currently active in-memory session (if any).
     */
    fun observeCurrentSession(): Flow<UserSession?>

    /**
     * Retrieves the current in-memory session.
     */
    fun getCurrentSession(): UserSession?

    /**
     * Updates activity timestamp on the current session to keep it alive.
     */
    fun touchSession(): Boolean

    /**
     * Terminates the current session (logs out).
     */
    fun logout()
}
