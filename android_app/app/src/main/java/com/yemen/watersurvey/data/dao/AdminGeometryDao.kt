package com.yemen.watersurvey.data.dao

import androidx.room.*
import com.yemen.watersurvey.data.entity.AdminGeometryEntity

/**
 * Data Access Object for AdminGeometryEntity.
 * Enables spatial bounding box filtering for offline Point-in-Polygon (PIP).
 */
@Dao
interface AdminGeometryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeometryList(list: List<AdminGeometryEntity>)

    @Query("SELECT * FROM admin_geometry WHERE pcode = :pcode LIMIT 1")
    suspend fun getGeometryByPcode(pcode: String): AdminGeometryEntity?

    @Query("""
        SELECT * FROM admin_geometry 
        WHERE adminLevel = :adminLevel 
        AND :lat >= minLat AND :lat <= maxLat 
        AND :lon >= minLon AND :lon <= maxLon
    """)
    suspend fun findCandidateGeometries(
        adminLevel: String,
        lat: Double,
        lon: Double
    ): List<AdminGeometryEntity>

    @Query("SELECT * FROM admin_geometry WHERE adminLevel = :adminLevel")
    suspend fun getGeometriesByLevel(adminLevel: String): List<AdminGeometryEntity>

    @Query("SELECT COUNT(*) FROM admin_geometry")
    suspend fun getGeometryCount(): Int

    @Query("DELETE FROM admin_geometry WHERE versionTag = :versionTag")
    suspend fun deleteGeometryByVersion(versionTag: String)
}
