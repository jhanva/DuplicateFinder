package com.duplicatefinder.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import com.duplicatefinder.data.local.db.dao.OverlayDetectionDao
import com.duplicatefinder.data.local.db.entities.OverlayDetectionEntity
import com.duplicatefinder.domain.model.DetectionStage
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.OverlayDetection
import com.duplicatefinder.domain.model.OverlayKind
import com.duplicatefinder.domain.model.OverlayRegion
import com.duplicatefinder.domain.repository.OverlayModelBundleRepository
import com.duplicatefinder.domain.repository.OverlayRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlayRepositoryImpl @Inject constructor(
    private val overlayDetectionDao: OverlayDetectionDao,
    @ApplicationContext private val context: Context,
    private val overlayModelBundleRepository: OverlayModelBundleRepository,
    private val overlayOnnxRuntime: OverlayOnnxRuntime
) : OverlayRepository {

    override suspend fun getCachedDetections(
        images: List<ImageItem>,
        modelVersion: String
    ): Map<Long, OverlayDetection> = withContext(Dispatchers.IO) {
        if (images.isEmpty()) return@withContext emptyMap()

        val imagesById = images.associateBy { it.id }
        overlayDetectionDao.getByImageIds(imagesById.keys.toList())
            .asSequence()
            .filter { it.modelVersion == modelVersion }
            .mapNotNull { entity ->
                val image = imagesById[entity.imageId] ?: return@mapNotNull null
                if (image.size != entity.size || image.dateModified != entity.dateModified) {
                    return@mapNotNull null
                }

                entity.imageId to entity.toDomain(image)
            }
            .toMap()
    }

    override suspend fun detectOverlayCandidates(
        images: List<ImageItem>,
        modelVersion: String
    ): List<OverlayDetection> = withContext(Dispatchers.Default) {
        if (images.isEmpty()) return@withContext emptyList()

        val activeBundle = overlayModelBundleRepository.getActiveBundleInfo()
            ?.takeIf { it.bundleVersion == modelVersion }

        // Decode + inference per image is CPU-bound; fan the batch out over a
        // small worker pool. OrtSession.run is thread-safe, and the cap keeps
        // peak bitmap/tensor memory bounded even on 8-core devices.
        val results = arrayOfNulls<OverlayDetection>(images.size)
        val nextIndex = AtomicInteger(0)
        List(detectionParallelism()) {
            launch {
                while (true) {
                    val index = nextIndex.getAndIncrement()
                    if (index >= images.size) break
                    results[index] = detectOverlay(images[index], modelVersion, activeBundle)
                }
            }
        }.joinAll()

        results.filterNotNull()
    }

    override suspend fun saveDetections(detections: List<OverlayDetection>) {
        if (detections.isEmpty()) return

        overlayDetectionDao.insertAll(
            detections.map { detection ->
                OverlayDetectionEntity(
                    imageId = detection.image.id,
                    path = detection.image.path,
                    preliminaryScore = detection.preliminaryScore,
                    refinedScore = detection.refinedScore,
                    overlayCoverageRatio = detection.overlayCoverageRatio,
                    maskConfidence = detection.maskConfidence,
                    overlayKinds = detection.overlayKinds.joinToString(",") { it.name },
                    regionsJson = encodeRegions(detection.maskBounds),
                    dateModified = detection.image.dateModified,
                    size = detection.image.size,
                    modelVersion = detection.modelVersion
                )
            }
        )
    }

    private fun detectOverlay(
        image: ImageItem,
        modelVersion: String,
        activeBundle: com.duplicatefinder.domain.repository.OverlayModelBundleInfo?
    ): OverlayDetection? {
        val analysis = runCatching {
            analyzeImageContent(
                image = image,
                activeBundle = activeBundle
            )
        }.getOrNull() ?: return null

        return OverlayDetection(
            image = image,
            preliminaryScore = analysis.preliminaryScore,
            refinedScore = analysis.refinedScore,
            overlayCoverageRatio = analysis.overlayCoverageRatio,
            maskBounds = analysis.regions,
            maskConfidence = analysis.maskConfidence,
            overlayKinds = analysis.overlayKinds,
            stage = analysis.stage,
            modelVersion = modelVersion
        )
    }

    private fun analyzeImageContent(
        image: ImageItem,
        activeBundle: com.duplicatefinder.domain.repository.OverlayModelBundleInfo?
    ): OverlayAnalysisResult {
        val bitmap = decodeAnalysisBitmap(image)
            ?: throw IOException("Unable to decode bitmap for overlay analysis.")
        return try {
            analyzeBitmap(bitmap, activeBundle)
        } finally {
            bitmap.recycle()
        }
    }

    internal fun analyzeBitmap(
        bitmap: android.graphics.Bitmap,
        activeBundle: com.duplicatefinder.domain.repository.OverlayModelBundleInfo?
    ): OverlayAnalysisResult {
        return if (activeBundle != null) {
            overlayOnnxRuntime.analyze(bitmap, activeBundle)
        } else {
            fallbackAnalyze(bitmap)
        }
    }

    private fun fallbackAnalyze(bitmap: android.graphics.Bitmap): OverlayAnalysisResult {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return OverlayImageAnalysis.analyze(
            pixels = pixels,
            width = bitmap.width,
            height = bitmap.height
        )
    }

    private fun decodeAnalysisBitmap(image: ImageItem) = context.contentResolver.openInputStream(image.uri)?.use { stream ->
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(stream, null, bounds)

        val maxDimension = maxOf(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while ((maxDimension / sampleSize) > MAX_ANALYSIS_DIMENSION) {
            sampleSize *= 2
        }
        context.contentResolver.openInputStream(image.uri)?.use { decodeStream ->
            BitmapFactory.decodeStream(
                decodeStream,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize.coerceAtLeast(1)
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
            )
        }
    }

    private fun encodeRegions(regions: List<OverlayRegion>): String {
        val array = JSONArray()
        regions.forEach { region ->
            array.put(
                JSONObject().apply {
                    put("left", region.left.toDouble())
                    put("top", region.top.toDouble())
                    put("right", region.right.toDouble())
                    put("bottom", region.bottom.toDouble())
                    put("confidence", region.confidence.toDouble())
                    put("kind", region.kind.name)
                }
            )
        }
        return array.toString()
    }

    private fun decodeRegions(regionsJson: String): List<OverlayRegion> {
        if (regionsJson.isBlank()) return emptyList()

        val array = JSONArray(regionsJson)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    OverlayRegion(
                        left = item.optDouble("left", 0.0).toFloat(),
                        top = item.optDouble("top", 0.0).toFloat(),
                        right = item.optDouble("right", 0.0).toFloat(),
                        bottom = item.optDouble("bottom", 0.0).toFloat(),
                        confidence = item.optDouble("confidence", 0.0).toFloat(),
                        kind = item.optString("kind")
                            .takeIf { it.isNotBlank() }
                            ?.let(OverlayKind::valueOf)
                            ?: OverlayKind.UNKNOWN
                    )
                )
            }
        }
    }

    private fun OverlayDetectionEntity.toDomain(image: ImageItem): OverlayDetection {
        val kinds = overlayKinds.split(',')
            .mapNotNull { value ->
                value.takeIf { it.isNotBlank() }?.let(OverlayKind::valueOf)
            }
            .toSet()
            .ifEmpty { setOf(OverlayKind.UNKNOWN) }

        return OverlayDetection(
            image = image,
            preliminaryScore = preliminaryScore,
            refinedScore = refinedScore,
            overlayCoverageRatio = overlayCoverageRatio,
            maskBounds = decodeRegions(regionsJson),
            maskConfidence = maskConfidence,
            overlayKinds = kinds,
            stage = if (refinedScore > preliminaryScore) {
                DetectionStage.STAGE_2_REFINED
            } else {
                DetectionStage.STAGE_1_CANDIDATE
            },
            modelVersion = modelVersion
        )
    }

    companion object {
        private const val MAX_ANALYSIS_DIMENSION = 384

        private fun detectionParallelism(): Int {
            return Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        }
    }
}
