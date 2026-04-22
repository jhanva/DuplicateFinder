package com.duplicatefinder.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.duplicatefinder.domain.model.CleaningPreview
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.OverlayDetection
import com.duplicatefinder.domain.model.OverlayPreviewDecision
import com.duplicatefinder.domain.model.OverlayRegion
import com.duplicatefinder.domain.model.PreviewStatus
import com.duplicatefinder.domain.model.overlayPreviewDecodeMaxDimension
import com.duplicatefinder.domain.model.supportsOverlayCleaning
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
import kotlin.math.max

@Singleton
class OverlayCleaningRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trashRepository: TrashRepository,
    private val overlayOnnxRuntime: OverlayOnnxRuntime,
    @Named("overlayPreviewDir") private val previewDir: File,
    @Named("overlayCleaningModelDir") private val bundleDir: File
) : OverlayCleaningRepository {

    override suspend fun generatePreview(
        image: ImageItem,
        detection: OverlayDetection,
        bundleInfo: OverlayModelBundleInfo
    ): Result<CleaningPreview> = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val previewFile = createPreviewFile(image, bundleInfo.bundleVersion)
        runCatching {
            if (!image.supportsOverlayCleaning()) {
                throw IOException("Remove Watermark is not supported for ${image.mimeType}.")
            }
            ensureBundleAvailable(bundleInfo)
            previewDir.mkdirs()

            val sourceBitmap = decodePreviewBitmap(
                image = image,
                maxDimension = image.overlayPreviewDecodeMaxDimension(
                    bundleInfo.inputSizeInpainter.coerceAtLeast(MIN_PREVIEW_DIMENSION)
                )
            ) ?: throw IOException("Unable to decode source image for preview generation.")
            val cleanedBitmap = renderPreviewBitmap(
                sourceBitmap = sourceBitmap,
                regions = detection.maskBounds,
                bundleInfo = bundleInfo
            )

            FileOutputStream(previewFile).use { output ->
                if (!cleanedBitmap.compress(compressFormatFor(image), PREVIEW_QUALITY, output)) {
                    throw IOException("Unable to write cleaned preview to cache.")
                }
            }
            if (!previewFile.exists() || previewFile.length() <= 0L) {
                throw IOException("Generated preview file is empty.")
            }

            CleaningPreview(
                sourceImage = image,
                previewUri = Uri.fromFile(previewFile),
                maskUri = null,
                modelVersion = bundleInfo.bundleVersion,
                generationTimeMs = System.currentTimeMillis() - startedAt,
                status = PreviewStatus.READY
            )
        }.onFailure {
            previewFile.delete()
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
            deleteUriFile(preview.previewUri)
            preview.maskUri?.let(::deleteUriFile)
            Unit
        }
    }

    private suspend fun replaceOriginal(
        image: ImageItem,
        preview: CleaningPreview
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val previewFile = preview.previewUri.path?.let(::File)
                ?: throw IOException("Preview file path is missing.")
            if (!previewFile.exists() || previewFile.length() <= 0L) {
                throw IOException("Preview file does not exist.")
            }

            val backupFile = File(previewDir, "${image.id}_backup_${image.name}")
            createBackup(image, backupFile)

            try {
                context.contentResolver.openOutputStream(image.uri, "wt")?.use { output ->
                    previewFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                } ?: throw IOException("Unable to overwrite original image.")

                validateVisibleImage(image)
                discardPreview(preview).getOrThrow()
                backupFile.delete()
                Unit
            } catch (error: Exception) {
                restoreBackup(image = image, backupFile = backupFile)
                discardPreview(preview)
                throw error
            }
        }
    }

    private suspend fun deleteAll(
        image: ImageItem,
        preview: CleaningPreview
    ): Result<Unit> = withContext(Dispatchers.IO) {
        trashRepository.moveToTrash(listOf(image)).fold(
            onSuccess = {
                discardPreview(preview).fold(
                    onSuccess = { Result.success(Unit) },
                    onFailure = { Result.failure(it) }
                )
            },
            onFailure = { Result.failure(it) }
        )
    }

    private fun createPreviewFile(image: ImageItem, modelVersion: String): File {
        val startedAt = System.currentTimeMillis()
        val extension = extensionFor(image)
        return File(previewDir, "${image.id}_${modelVersion}_$startedAt.$extension")
    }

    private fun ensureBundleAvailable(bundleInfo: OverlayModelBundleInfo) {
        val requiredPath = bundleInfo.inpainterPath.takeIf { it.isNotBlank() }
            ?: throw IOException("Overlay cleaning model is missing.")
        val modelFile = File(bundleDir, requiredPath.substringAfterLast('/'))
        if (!modelFile.exists() || modelFile.length() <= 0L) {
            throw IOException("Overlay cleaning model is incomplete or missing.")
        }
    }

    private fun decodePreviewBitmap(image: ImageItem, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(image.uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val maxSourceDimension = max(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while ((maxSourceDimension / sampleSize) > maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(image.uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }

    internal fun renderPreviewBitmap(
        sourceBitmap: Bitmap,
        regions: List<OverlayRegion>,
        bundleInfo: OverlayModelBundleInfo
    ): Bitmap {
        return overlayOnnxRuntime.inpaint(
            sourceBitmap = sourceBitmap,
            regions = regions,
            bundleInfo = bundleInfo
        )
    }

    private fun createBackup(image: ImageItem, backupFile: File) {
        context.contentResolver.openInputStream(image.uri)?.use { input ->
            FileOutputStream(backupFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Unable to create backup for ${image.name}.")
    }

    private fun validateVisibleImage(image: ImageItem) {
        val descriptor = context.contentResolver.openAssetFileDescriptor(image.uri, "r")
            ?: throw IOException("Unable to validate replaced image.")
        descriptor.use {
            when {
                it.length > 0L -> return
                it.length == 0L -> throw IOException("Replaced image is empty.")
                else -> Unit
            }
        }

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(image.uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: throw IOException("Unable to validate replaced image contents.")

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Replaced image is unreadable.")
        }
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

    private fun deleteUriFile(uri: Uri) {
        uri.path?.let { path ->
            File(path).takeIf { it.exists() }?.delete()
        }
    }

    private fun compressFormatFor(image: ImageItem): Bitmap.CompressFormat {
        return when {
            image.mimeType.contains("png", ignoreCase = true) -> Bitmap.CompressFormat.PNG
            image.mimeType.contains("webp", ignoreCase = true) -> Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }
    }

    private fun extensionFor(image: ImageItem): String {
        return when {
            image.mimeType.contains("png", ignoreCase = true) -> "png"
            image.mimeType.contains("webp", ignoreCase = true) -> "webp"
            else -> "jpg"
        }
    }

    companion object {
        private const val PREVIEW_QUALITY = 95
        private const val MIN_PREVIEW_DIMENSION = 512
    }
}
