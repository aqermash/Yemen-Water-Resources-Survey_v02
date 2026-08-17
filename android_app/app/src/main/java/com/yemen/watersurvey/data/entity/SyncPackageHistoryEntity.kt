package com.yemen.watersurvey.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room database entity storing immutable historical transitions for sync packages.
 * Guarantees a fully auditable lifecycle log for all supervisor actions.
 */
@Entity(
    tableName = "sync_package_history",
    indices = [
        Index(value = ["packageId"]),
        Index(value = ["timestamp"]),
        Index(value = ["toState"])
    ]
)
data class SyncPackageHistoryEntity(
    @PrimaryKey
    val historyId: String,
    val packageId: String,
    val fromState: String,
    val toState: String,
    val actorId: String,
    val actorRole: String,
    val timestamp: String,
    val actionDescription: String,
    val detailsJson: String = "{}"
)
