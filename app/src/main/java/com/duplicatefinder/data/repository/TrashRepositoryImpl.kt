package com.duplicatefinder.data.repository

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.duplicatefinder.data.local.datastore.SettingsDataStore
import com.duplicatefinder.data.local.db.dao.TrashDao
import com.duplicatefinder.data.local.db.entities.TrashEntity
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.TrashItem
import com.duplicatefinder.domain.model.UserConfirmationRequiredException
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
import java.io.IOException
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
                if (images.isEmpty()) return@withContext Result.success(0)

                val autoDeleteDays = settingsDataStore.autoDeleteDays.first()
                val now = System.currentTimeMillis()
                val expiresAt = now + (autoDeleteDays * 24 * 60 * 60 * 1000L)

                var movedCount = 0
                var failedCount = 0
                var lastError: Exception? = null

                for ((index, image) in images.withIndex()) {
                    val trashFile = File(trashDir, "${image.id}_${image.name}")
                    try {
                        val inputStream = context.contentResolver.openInputStream(image.uri)
                        if (inputStream == null) {
                            failedCount++
                            lastError = IOException("Failed to open source image: ${image.name}")
                            continue
                        }
                        inputStream.use { input ->
                            FileOutputStream(trashFile).use { output ->
                                input.copyTo(output)
                            }
                        }

                        if (trashFile.exists()) {
                            val deleteResult = context.contentResolver.delete(image.uri, null, null)
                            if (deleteResult > 0) {
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
                                movedCount++
                            } else {
                                trashFile.delete()
                                failedCount++
                                lastError = IOException("Failed to delete source image: ${image.name}")
                            }
                        } else {
                            failedCount++
                            lastError = IOException("Failed to copy image to trash: ${image.name}")
                        }
                    } catch (securityException: SecurityException) {
                        failedCount++
                        if (trashFile.exists()) {
                            trashFile.delete()
                        }
                        lastError = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val pendingUris = images
                                .drop(index)
                                .map { it.uri }
                                .distinct()
                            val intentSender = MediaStore.createWriteRequest(
                                context.contentResolver,
                                pendingUris
                            ).intentSender
                            UserConfirmationRequiredException(
                                intentSender = intentSender,
                                message = "Moving to trash requires user confirmation."
                            )
                        } else if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            securityException is RecoverableSecurityException
                        ) {
                            UserConfirmationRequiredException(
                                intentSender = securityException.userAction.actionIntent.intentSender,
                                message = "Moving to trash requires user confirmation."
                            )
                        } else {
                            securityException
                        }
                        if (lastError is UserConfirmationRequiredException) {
                            return@withContext Result.failure(lastError)
                        }
                    } catch (e: Exception) {
                        failedCount++
                        lastError = e
                        if (trashFile.exists()) {
                            trashFile.delete()
                        }
                    }
                }

                if (movedCount == 0 && failedCount > 0) {
                    Result.failure(
                        lastError ?: IOException("Failed to move ${images.size} image(s) to trash.")
                    )
                } else {
                    Result.success(movedCount)
                }
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
                        if (!trashFile.exists()) return@forEach

                        val restored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            restoreWithMediaStore(item, trashFile)
                        } else {
                            restoreWithLegacyPath(item, trashFile)
                        }

                        if (restored) {
                            trashFile.delete()
                            trashDao.deleteByIds(listOf(item.id))
                            restoredCount++
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

    private fun restoreWithMediaStore(item: TrashItem, trashFile: File): Boolean {
        val relativePath = extractRelativePath(item.originalPath)
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, item.name)
            put(MediaStore.Images.Media.MIME_TYPE, item.mimeType)
            if (!relativePath.isNullOrBlank()) {
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            }
        }

        val targetUri = context.contentResolver.insert(collection, contentValues) ?: return false
        return try {
            val copied = context.contentResolver.openOutputStream(targetUri)?.use { output ->
                FileInputStream(trashFile).use { input ->
                    input.copyTo(output)
                }
                true
            } ?: false
            if (!copied) {
                context.contentResolver.delete(targetUri, null, null)
            }
            copied
        } catch (e: Exception) {
            context.contentResolver.delete(targetUri, null, null)
            false
        }
    }

    private fun restoreWithLegacyPath(item: TrashItem, trashFile: File): Boolean {
        val originalFile = File(item.originalPath)
        originalFile.parentFile?.mkdirs()

        FileInputStream(trashFile).use { input ->
            FileOutputStream(originalFile).use { output ->
                input.copyTo(output)
            }
        }

        return originalFile.exists()
    }

    private fun extractRelativePath(originalPath: String): String? {
        if (originalPath.isBlank()) return null

        val normalized = originalPath.replace('\\', '/')
        val withoutStoragePrefix = normalized.substringAfter("/storage/emulated/0/", normalized)
        val parent = withoutStoragePrefix.substringBeforeLast('/', "")
        return if (parent.isBlank()) null else "$parent/"
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
