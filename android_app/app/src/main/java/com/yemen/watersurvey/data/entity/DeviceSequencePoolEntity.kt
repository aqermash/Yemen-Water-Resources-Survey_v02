package com.yemen.watersurvey.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity storing offline sequence pools allocated to this device
 * per administrative location bucket (Admin1 + Admin2 + Admin3) and facility type.
 */
@Entity(
    tableName = "device_sequence_pools",
    primaryKeys = ["adminBucketKey", "facilityType"],
    indices = [
        Index(value = ["adminBucketKey"]),
        Index(value = ["facilityType"])
    ]
)
data class DeviceSequencePoolEntity(
    val adminBucketKey: String, // e.g. "YE221501" (Admin1 + Admin2 + Admin3)
    val facilityType: String,   // "WL", "SP", "WH"
    val rangeStart: Int,        // e.g. 1
    val rangeEnd: Int,          // e.g. 100
    val currentNext: Int,       // e.g. 1
    val lastAllocatedTimestamp: Long = System.currentTimeMillis()
)
