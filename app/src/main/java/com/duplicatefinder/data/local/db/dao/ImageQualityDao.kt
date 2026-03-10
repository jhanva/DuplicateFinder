package com.duplicatefinder.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.duplicatefinder.data.local.db.entities.ImageQualityEntity

@Dao
interface ImageQualityDao {
    @Query("SELECT * FROM image_quality_scores WHERE imageId IN (:imageIds)")
    suspend fun getByImageIds(imageIds: List<Long>): List<ImageQualityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ImageQualityEntity>)

    @Query("DELETE FROM image_quality_scores WHERE imageId = :imageId")
    suspend fun deleteByImageId(imageId: Long)

    @Query("DELETE FROM image_quality_scores WHERE imageId IN (:imageIds)")
    suspend fun deleteByImageIds(imageIds: List<Long>)
}

