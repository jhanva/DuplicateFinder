package com.duplicatefinder.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.duplicatefinder.data.local.db.entities.TrashEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash_items ORDER BY deletedAt DESC")
    fun getAll(): Flow<List<TrashEntity>>

    @Query("SELECT * FROM trash_items WHERE id = :id")
    suspend fun getById(id: Long): TrashEntity?

    @Query("SELECT * FROM trash_items WHERE expiresAt <= :timestamp")
    suspend fun getExpired(timestamp: Long): List<TrashEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TrashEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<TrashEntity>): List<Long>

    @Delete
    suspend fun delete(entity: TrashEntity)

    @Query("DELETE FROM trash_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM trash_items")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM trash_items")
    suspend fun count(): Int

    @Query("SELECT SUM(size) FROM trash_items")
    suspend fun totalSize(): Long?
}
