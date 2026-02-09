package com.duplicatefinder.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.duplicatefinder.data.local.db.entities.ImageHashEntity

@Dao
interface ImageHashDao {
    @Query("SELECT * FROM image_hashes WHERE imageId = :imageId")
    suspend fun getByImageId(imageId: Long): ImageHashEntity?

    @Query("SELECT * FROM image_hashes WHERE imageId IN (:imageIds)")
    suspend fun getByImageIds(imageIds: List<Long>): List<ImageHashEntity>

    @Query("SELECT md5Hash FROM image_hashes WHERE imageId = :imageId AND dateModified = :dateModified")
    suspend fun getCachedHash(imageId: Long, dateModified: Long): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ImageHashEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ImageHashEntity>)

    @Query("DELETE FROM image_hashes WHERE imageId = :imageId")
    suspend fun deleteByImageId(imageId: Long)

    @Query("DELETE FROM image_hashes WHERE imageId IN (:imageIds)")
    suspend fun deleteByImageIds(imageIds: List<Long>)

    @Query("DELETE FROM image_hashes")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM image_hashes")
    suspend fun count(): Int

    @Query("SELECT * FROM image_hashes WHERE md5Hash = :hash")
    suspend fun getByMd5Hash(hash: String): List<ImageHashEntity>
}
