package com.yemen.watersurvey.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yemen.watersurvey.data.entity.DeviceSequencePoolEntity

@Dao
interface DeviceSequenceDao {

    @Query("SELECT * FROM device_sequence_pools WHERE adminBucketKey = :adminBucketKey AND facilityType = :facilityType LIMIT 1")
    suspend fun getPool(adminBucketKey: String, facilityType: String): DeviceSequencePoolEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePool(pool: DeviceSequencePoolEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePools(pools: List<DeviceSequencePoolEntity>)

    @Query("SELECT * FROM device_sequence_pools")
    suspend fun getAllPools(): List<DeviceSequencePoolEntity>
}
