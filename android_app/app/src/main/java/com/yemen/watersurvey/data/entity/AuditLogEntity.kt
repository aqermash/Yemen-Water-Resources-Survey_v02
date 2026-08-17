package com.yemen.watersurvey.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room database entity storing immutable audit trails for every system and supervisor action.
 */
@Entity(
    tableName = "audit_logs",
    indices = [
        Index(value = ["surveyUUID"]),
        Index(value = ["recordId"]),
        Index(value = ["actionType"]),
        Index(value = ["timestamp"]),
        Index(value = ["packageId"])
    ]
)
data class AuditLogEntity(
    @PrimaryKey
    val logId: String,
    val surveyUUID: String,
    val recordId: String,
    val actionType: String,
    val actorId: String,
    val actorRole: String,
    val timestamp: String,
    val decisionReason: String = "",
    val detailsJson: String = "{}",
    val packageId: String = ""
)
