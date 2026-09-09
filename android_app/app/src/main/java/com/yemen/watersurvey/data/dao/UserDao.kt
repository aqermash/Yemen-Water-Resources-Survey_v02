package com.yemen.watersurvey.data.dao

import androidx.room.*
import com.yemen.watersurvey.data.entity.UserEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for User entities.
 *
 * Provides atomic insert/update queries, transactional record state changes,
 * and reactive lookup flows for real-time authentication and user management.
 */
@Dao
interface UserDao {

    /**
     * Observes all registered users ordered by creation date descending.
     */
    @Query("SELECT * FROM users ORDER BY provisionedAt DESC")
    fun getAllUsersFlow(): Flow<List<UserEntity>>

    /**
     * Observes active users ordered by Arabic full name.
     */
    @Query("SELECT * FROM users WHERE isActive = 1 ORDER BY fullNameAr ASC")
    fun getActiveUsersFlow(): Flow<List<UserEntity>>

    /**
     * Retrieves all users as a one-shot query.
     */
    @Query("SELECT * FROM users ORDER BY fullNameAr ASC")
    suspend fun getAllUsers(): List<UserEntity>

    /**
     * Looks up a user by their unique primary key userId.
     */
    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    /**
     * Reactive observation of a user record by userId.
     */
    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    fun getUserByIdFlow(userId: String): Flow<UserEntity?>

    /**
     * Looks up a user by their unique login username (case-insensitive).
     */
    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    /**
     * Reactive observation of a user record by username.
     */
    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    fun getUserByUsernameFlow(username: String): Flow<UserEntity?>

    /**
     * Retrieves users assigned to a specific role.
     */
    @Query("SELECT * FROM users WHERE role = :roleCode AND isActive = 1 ORDER BY fullNameAr ASC")
    suspend fun getUsersByRole(roleCode: String): List<UserEntity>

    /**
     * Retrieves users scoped to an administrative governorate and district.
     */
    @Query("""
        SELECT * FROM users 
        WHERE (:governorateCode IS NULL OR governorateCode = :governorateCode)
          AND (:districtCode IS NULL OR districtCode = :districtCode)
          AND isActive = 1
        ORDER BY fullNameAr ASC
    """)
    suspend fun getUsersByAdministrativeArea(
        governorateCode: String?,
        districtCode: String?
    ): List<UserEntity>

    /**
     * Inserts or updates a user atomically.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUser(user: UserEntity)

    /**
     * Inserts a list of user entities (e.g. during provisioning package import).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUsers(users: List<UserEntity>)

    /**
     * Updates the last login timestamp for a user.
     */
    @Query("UPDATE users SET lastLoginAt = :timestampIso WHERE userId = :userId")
    suspend fun updateLastLogin(userId: String, timestampIso: String)

    /**
     * Binds a hardware device identifier to the user account upon initial device setup.
     */
    @Query("UPDATE users SET assignedDeviceId = :deviceId WHERE userId = :userId")
    suspend fun updateAssignedDevice(userId: String, deviceId: String)

    /**
     * Updates active status (activation/deactivation) of a user account.
     */
    @Query("UPDATE users SET isActive = :isActive WHERE userId = :userId")
    suspend fun updateUserActiveStatus(userId: String, isActive: Boolean)

    /**
     * Updates user credentials (salted hash and salt).
     */
    @Query("UPDATE users SET pinSalt = :salt, pinSaltedHash = :saltedHash WHERE userId = :userId")
    suspend fun updateCredentials(userId: String, salt: String, saltedHash: String)

    /**
     * Deletes a user record by userId.
     */
    @Query("DELETE FROM users WHERE userId = :userId")
    suspend fun deleteUserById(userId: String): Int

    /**
     * Returns the total count of registered users.
     */
    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    /**
     * Returns the count of active users with a specific role.
     */
    @Query("SELECT COUNT(*) FROM users WHERE role = :roleCode AND isActive = 1")
    suspend fun getActiveUserCountByRole(roleCode: String): Int
}
