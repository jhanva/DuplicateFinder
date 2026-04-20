package com.duplicatefinder.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.duplicatefinder.domain.model.CleaningPreview
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.OverlayDetection
import com.duplicatefinder.domain.model.OverlayPreviewDecision
import com.duplicatefinder.domain.model.PreviewStatus
import com.duplicatefinder.domain.repository.OverlayCleaningRepository
import com.duplicatefinder.domain.repository.OverlayModelBundleInfo
import com.duplicatefinder.domain.repository.TrashRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class OverlayCleaningRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trashRepository: TrashRepository,
    @Named("overlayPreviewDir") private val previewDir: File
) : OverlayCleaningRepository {

    override suspend fun generatePreview(
        image: ImageItem,
        detection: OverlayDetection,
        bundleInfo: OverlayModelBundleInfo
    ): Result<CleaningPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val startedAt = System.currentTimeMillis()
            previewDir.mkdirs()
            val previewFile = File(
                previewDir,
                "${image.id}_${System.currentTimeMillis()}_${image.name}"
            )

            context.contentResolver.openInputStream(image.uri)?.use { input ->
                FileOutputStream(previewFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IOException("Unable to open source image for preview generation.")

            CleaningPreview(
                sourceImage = image,
                previewUri = Uri.fromFile(previewFile),
                maskUri = null,
                modelVersion = bundleInfo.bundleVersion,
                generationTimeMs = System.currentTimeMillis() - startedAt,
                status = PreviewStatus.READY
            )
        }
    }

    override suspend fun applyDecision(
        image: ImageItem,
        preview: CleaningPreview,
        decision: OverlayPreviewDecision
    ): Result<Unit> = withContext(Dispatchers.IO) {
        when (decision) {
            OverlayPreviewDecision.KEEP_CLEANED_REPLACE_ORIGINAL ->
                replaceOriginal(image = image, preview = preview)

            OverlayPreviewDecision.DELETE_ALL ->
                deleteAll(image = image, preview = preview)

            OverlayPreviewDecision.SKIP_KEEP_ORIGINAL ->
                discardPreview(preview)
        }
    }

    override suspend fun discardPreview(preview: CleaningPreview): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            preview.previewUri.path?.let { path ->
                File(path).takeIf { it.exists() }?.delete()
            }
            Unit
        }
    }

    private suspend fun replaceOriginal(
        image: ImageItem,
        preview: CleaningPreview
    ): Result<Unit> = runCatching {
        val backupFile = File(previewDir, "${image.id}_backup_${image.name}")
        context.contentResolver.openInputStream(image.uri)?.use { input ->
            FileOutputStream(backupFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Unable to create backup for ${image.name}.")

        try {
            val previewFile = preview.previewUri.path?.let(::File)
                ?: throw IOException("Preview file path is missing.")
            if (!previewFile.exists()) {
                throw IOException("Preview file does not exist.")
            }

            context.contentResolver.openOutputStream(image.uri, "wt")?.use { output ->
                previewFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: throw IOException("Unable to overwrite original image.")

            discardPreview(preview).getOrThrow()
            backupFile.delete()
        } catch (error: Exception) {
            restoreBackup(image = image, backupFile = backupFile)
            throw error
        }
    }

    private suspend fun deleteAll(
        image: ImageItem,
        preview: CleaningPreview
    ): Result<Unit> {
        discardPreview(preview).getOrElse { return Result.failure(it) }
        return trashRepository.moveToTrash(listOf(image)).fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    private fun restoreBackup(
        image: ImageItem,
        backupFile: File
    ) {
        if (!backupFile.exists()) return
        runCatching {
            context.contentResolver.openOutputStream(image.uri, "wt")?.use { output ->
                backupFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
        }
        backupFile.delete()
    }

    @Suppress("unused")
    private fun buildReplacementContentValues(image: ImageItem): ContentValues {
        val relativePath = extractRelativePath(image.path)
        return ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, image.name)
            put(MediaStore.Images.Media.MIME_TYPE, image.mimeType)
            if (!relativePath.isNullOrBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            }
        }
    }

    private fun extractRelativePath(originalPath: String): String? {
        if (originalPath.isBlank()) return null

        val normalized = originalPath.replace('\\', '/').trim()
        val relativePath = when {
            normalized.startsWith("/storage/emulated/0/") ->
                normalized.removePrefix("/storage/emulated/0/")
            normalized.startsWith("storage/emulated/0/") ->
                normalized.removePrefix("storage/emulated/0/")
            normalized.startsWith("/storage/self/primary/") ->
                normalized.removePrefix("/storage/self/primary/")
            normalized.startsWith("storage/self/primary/") ->
                normalized.removePrefix("storage/self/primary/")
            normalized.startsWith("/sdcard/") ->
                normalized.removePrefix("/sdcard/")
            normalized.startsWith("sdcard/") ->
                normalized.removePrefix("sdcard/")
            else -> normalized.trimStart('/')
        }
        val parent = relativePath.substringBeforeLast('/', "")
        return if (parent.isBlank()) null else "$parent/"
    }
}
