package com.yemen.watersurvey.data.mapper

import com.yemen.watersurvey.data.entity.UserEntity
import com.yemen.watersurvey.domain.model.User
import com.yemen.watersurvey.domain.model.UserRole
import org.json.JSONObject

/**
 * Bidirectional mappers translating between domain [User] and database [UserEntity].
 *
 * Keeps domain models free of persistence details while maintaining data integrity
 * and serializing extension properties cleanly.
 */
object UserMapper {

    /**
     * Maps a database [UserEntity] to an immutable domain [User].
     * Excludes internal sensitive fields such as [UserEntity.pinSalt] and [UserEntity.pinSaltedHash].
     */
    fun toDomain(entity: UserEntity): User {
        return User(
            userId = entity.userId,
            username = entity.username,
            fullNameAr = entity.fullNameAr,
            fullNameEn = entity.fullNameEn,
            role = UserRole.fromCode(entity.role),
            governorateCode = entity.governorateCode,
            districtCode = entity.districtCode,
            assignedDeviceId = entity.assignedDeviceId,
            publicKeyBase64 = entity.publicKeyBase64,
            isActive = entity.isActive,
            provisionedBy = entity.provisionedBy,
            provisionedAt = entity.provisionedAt,
            lastLoginAt = entity.lastLoginAt,
            metadataExtra = parseMetadataJson(entity.metadataExtraJson)
        )
    }

    /**
     * Maps a domain [User] to a Room [UserEntity].
     *
     * @param user The domain user model.
     * @param pinSalt The cryptographic salt generated for this user's credentials.
     * @param pinSaltedHash The salted hash corresponding to the user's PIN.
     */
    fun toEntity(
        user: User,
        pinSalt: String = "",
        pinSaltedHash: String = ""
    ): UserEntity {
        return UserEntity(
            userId = user.userId,
            username = user.username.trim().lowercase(),
            fullNameAr = user.fullNameAr.trim(),
            fullNameEn = user.fullNameEn.trim(),
            role = user.role.code,
            pinSalt = pinSalt,
            pinSaltedHash = pinSaltedHash,
            governorateCode = user.governorateCode,
            districtCode = user.districtCode,
            assignedDeviceId = user.assignedDeviceId,
            publicKeyBase64 = user.publicKeyBase64,
            isActive = user.isActive,
            provisionedBy = user.provisionedBy,
            provisionedAt = user.provisionedAt,
            lastLoginAt = user.lastLoginAt,
            metadataExtraJson = serializeMetadataJson(user.metadataExtra)
        )
    }

    /**
     * Updates an existing [UserEntity] with modified domain [User] values while preserving
     * existing credentials.
     */
    fun updateEntityFromDomain(existingEntity: UserEntity, updatedUser: User): UserEntity {
        return existingEntity.copy(
            username = updatedUser.username.trim().lowercase(),
            fullNameAr = updatedUser.fullNameAr.trim(),
            fullNameEn = updatedUser.fullNameEn.trim(),
            role = updatedUser.role.code,
            governorateCode = updatedUser.governorateCode,
            districtCode = updatedUser.districtCode,
            assignedDeviceId = updatedUser.assignedDeviceId ?: existingEntity.assignedDeviceId,
            publicKeyBase64 = updatedUser.publicKeyBase64 ?: existingEntity.publicKeyBase64,
            isActive = updatedUser.isActive,
            metadataExtraJson = serializeMetadataJson(updatedUser.metadataExtra)
        )
    }

    private fun parseMetadataJson(jsonStr: String?): Map<String, String> {
        if (jsonStr.isNullOrBlank() || jsonStr == "{}") return emptyMap()
        return try {
            val json = JSONObject(jsonStr)
            val map = mutableMapOf<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = json.optString(key, "")
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun serializeMetadataJson(metadata: Map<String, String>): String {
        if (metadata.isEmpty()) return "{}"
        val json = JSONObject()
        metadata.forEach { (key, value) ->
            json.put(key, value)
        }
        return json.toString()
    }
}

/**
 * Convenience extension to map [UserEntity] directly to [User].
 */
fun UserEntity.toDomain(): User = UserMapper.toDomain(this)

/**
 * Convenience extension to map [User] directly to [UserEntity].
 */
fun User.toEntity(pinSalt: String = "", pinSaltedHash: String = ""): UserEntity =
    UserMapper.toEntity(this, pinSalt, pinSaltedHash)
