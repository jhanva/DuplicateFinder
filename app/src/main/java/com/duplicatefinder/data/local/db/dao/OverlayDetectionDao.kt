package com.duplicatefinder.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.duplicatefinder.data.local.db.entities.OverlayDetectionEntity

@Dao
interface OverlayDetectionDao {
    @Query("SELECT * FROM overlay_detections WHERE imageId IN (:imageIds)")
    suspend fun getByImageIds(imageIds: List<Long>): List<OverlayDetectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<OverlayDetectionEntity>)

    @Query("DELETE FROM overlay_detections WHERE imageId = :imageId")
    suspend fun deleteByImageId(imageId: Long)

    @Query("DELETE FROM overlay_detections")
    suspend fun deleteAll()
}
