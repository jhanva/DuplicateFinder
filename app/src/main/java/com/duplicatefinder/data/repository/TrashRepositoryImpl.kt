package com.duplicatefinder.data.repository

import android.content.Context
import android.net.Uri
import com.duplicatefinder.data.local.datastore.SettingsDataStore
import com.duplicatefinder.data.local.db.dao.TrashDao
import com.duplicatefinder.data.local.db.entities.TrashEntity
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.TrashItem
import com.duplicatefinder.domain.repository.TrashRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrashRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trashDao: TrashDao,
    private val settingsDataStore: SettingsDataStore
) : TrashRepository {

    private val trashDir: File by lazy {
        File(context.filesDir, TRASH_FOLDER).also { it.mkdirs() }
    }

    override fun getTrashItems(): Flow<List<TrashItem>> {
        return trashDao.getAll().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getTrashItemById(id: Long): TrashItem? {
        return trashDao.getById(id)?.toDomainModel()
    }

    override suspend fun moveToTrash(images: List<ImageItem>): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val autoDeleteDays = settingsDataStore.autoDeleteDays.first()
                val now = System.currentTimeMillis()
                val expiresAt = now + (autoDeleteDays * 24 * 60 * 60 * 1000L)

                var movedCount = 0

                images.forEach { image ->
                    try {
                        val trashFile = File(trashDir, "${image.id}_${image.name}")

                        context.contentResolver.openInputStream(image.uri)?.use { input ->
                            FileOutputStream(trashFile).use { output ->
                                input.copyTo(output)
                            }
                        }

                        if (trashFile.exists()) {
                            val entity = TrashEntity(
                                originalUri = image.uri.toString(),
                                originalPath = image.path,
                                trashPath = trashFile.absolutePath,
                                name = image.name,
                                size = image.size,
                                mimeType = image.mimeType,
                                deletedAt = now,
                                expiresAt = expiresAt
                            )

                            trashDao.insert(entity)

                            context.contentResolver.delete(image.uri, null, null)

                            movedCount++
                        }
                    } catch (e: Exception) {
                        // Continue with next image
                    }
                }

                Result.success(movedCount)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun restoreFromTrash(items: List<TrashItem>): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                var restoredCount = 0

                items.forEach { item ->
                    try {
                        val trashFile = File(item.trashPath)
                        val originalFile = File(item.originalPath)

                        if (trashFile.exists()) {
                            originalFile.parentFile?.mkdirs()

                            FileInputStream(trashFile).use { input ->
                                FileOutputStream(originalFile).use { output ->
                                    input.copyTo(output)
                                }
                            }

                            if (originalFile.exists()) {
                                trashFile.delete()
                                trashDao.deleteByIds(listOf(item.id))
                                restoredCount++
                            }
                        }
                    } catch (e: Exception) {
                        // Continue with next item
                    }
                }

                Result.success(restoredCount)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun deletePermanently(items: List<TrashItem>): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                var deletedCount = 0

                items.forEach { item ->
                    try {
                        val trashFile = File(item.trashPath)
                        if (trashFile.exists()) {
                            trashFile.delete()
                        }
                        trashDao.deleteByIds(listOf(item.id))
                        deletedCount++
                    } catch (e: Exception) {
                        // Continue with next item
                    }
                }

                Result.success(deletedCount)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun emptyTrash(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val items = trashDao.getAll().first()
            var deletedCount = 0

            items.forEach { entity ->
                try {
                    val trashFile = File(entity.trashPath)
                    if (trashFile.exists()) {
                        trashFile.delete()
                    }
                    deletedCount++
                } catch (e: Exception) {
                    // Continue with next item
                }
            }

            trashDao.deleteAll()

            Result.success(deletedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteExpiredItems(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val expiredItems = trashDao.getExpired(now)
            var deletedCount = 0

            expiredItems.forEach { entity ->
                try {
                    val trashFile = File(entity.trashPath)
                    if (trashFile.exists()) {
                        trashFile.delete()
                    }
                    deletedCount++
                } catch (e: Exception) {
                    // Continue with next item
                }
            }

            trashDao.deleteByIds(expiredItems.map { it.id })

            Result.success(deletedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTrashSize(): Long {
        return trashDao.totalSize() ?: 0L
    }

    override suspend fun getTrashItemCount(): Int {
        return trashDao.count()
    }

    private fun TrashEntity.toDomainModel() = TrashItem(
        id = id,
        originalUri = Uri.parse(originalUri),
        originalPath = originalPath,
        trashPath = trashPath,
        name = name,
        size = size,
        deletedAt = deletedAt,
        expiresAt = expiresAt,
        mimeType = mimeType
    )

    companion object {
        private const val TRASH_FOLDER = ".trash"
    }
}
