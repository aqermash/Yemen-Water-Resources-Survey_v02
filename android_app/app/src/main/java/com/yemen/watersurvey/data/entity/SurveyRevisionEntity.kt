package com.yemen.watersurvey.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room database entity storing immutable audit revision snapshots for survey records.
 */
@Entity(
    tableName = "survey_revisions",
    indices = [
        Index(value = ["surveyUUID"]),
        Index(value = ["recordId"]),
        Index(value = ["modifiedAt"])
    ]
)
data class SurveyRevisionEntity(
    @PrimaryKey
    val revisionId: String,
    val surveyUUID: String,
    val recordId: String,
    val revisionNumber: Int,
    val modifiedBy: String,
    val modifiedAt: String,
    val reasonForChange: String,
    val previousStatus: String,
    val newStatus: String,
    val changedFieldsJson: String = "[]",
    val snapshotDataJson: String = "{}",
    val sourcePackageId: String? = null
)
