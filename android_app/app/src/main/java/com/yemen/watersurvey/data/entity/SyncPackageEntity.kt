package com.yemen.watersurvey.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room database entity storing supervisor-side synchronization packages (.ywsync).
 * Tracks package metadata, digital checksums, lifecycle state, and validation summaries.
 */
@Entity(
    tableName = "sync_packages",
    indices = [
        Index(value = ["packageId"], unique = true),
        Index(value = ["packageSha256"]),
        Index(value = ["state"]),
        Index(value = ["governorate", "district"]),
        Index(value = ["isArchived"]),
        Index(value = ["receivedTimestamp"])
    ]
)
data class SyncPackageEntity(
    @PrimaryKey
    val packageId: String,
    val originalFileName: String,
    val packageFilePath: String,
    val extractedDirPath: String? = null,
    val packageSha256: String,
    val sourceDeviceId: String,
    val senderUsername: String,
    val senderRole: String,
    val governorate: String,
    val district: String,
    val exportTimestamp: String,
    val receivedTimestamp: String,
    val surveyCount: Int,
    val attachmentCount: Int,
    val totalAttachmentSizeBytes: Long,
    val packageSizeBytes: Long,
    val state: String = "RECEIVED", // Refers to SyncPackageState code
    val validationStatus: String = "PENDING", // PENDING, VALID, INVALID, CHECKSUM_MISMATCH
    val validationErrorsJson: String = "[]",
    val validationWarningsJson: String = "[]",
    val newRecordsCount: Int = 0,
    val updatesCount: Int = 0,
    val conflictsCount: Int = 0,
    val duplicatesCount: Int = 0,
    val mergedRecordsCount: Int = 0,
    val lastReviewedAt: String? = null,
    val reviewedBy: String? = null,
    val decisionReason: String? = null,
    val isArchived: Boolean = false,
    val notes: String? = null
)
