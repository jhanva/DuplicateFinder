package com.duplicatefinder.domain.repository

import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.TrashItem
import kotlinx.coroutines.flow.Flow

interface TrashRepository {
    fun getTrashItems(): Flow<List<TrashItem>>

    suspend fun getTrashItemById(id: Long): TrashItem?

    suspend fun moveToTrash(images: List<ImageItem>): Result<Int>

    suspend fun restoreFromTrash(items: List<TrashItem>): Result<Int>

    suspend fun deletePermanently(items: List<TrashItem>): Result<Int>

    suspend fun emptyTrash(): Result<Int>

    suspend fun deleteExpiredItems(): Result<Int>

    suspend fun getTrashSize(): Long

    suspend fun getTrashItemCount(): Int
}
