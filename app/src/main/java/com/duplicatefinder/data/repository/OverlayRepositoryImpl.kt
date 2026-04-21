package com.duplicatefinder.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import com.duplicatefinder.data.local.db.dao.OverlayDetectionDao
import com.duplicatefinder.data.local.db.entities.OverlayDetectionEntity
import com.duplicatefinder.data.media.MediaStoreDataSource
import com.duplicatefinder.domain.model.DetectionStage
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.OverlayDetection
import com.duplicatefinder.domain.model.OverlayKind
import com.duplicatefinder.domain.model.OverlayRegion
import com.duplicatefinder.domain.repository.OverlayRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlayRepositoryImpl @Inject constructor(
    private val overlayDetectionDao: OverlayDetectionDao,
    @ApplicationContext private val context: Context,
    private val mediaStoreDataSource: MediaStoreDataSource
) : OverlayRepository {

    override suspend fun getCachedDetections(
        imageIds: List<Long>,
        modelVersion: String
    ): Map<Long, OverlayDetection> = withContext(Dispatchers.IO) {
        if (imageIds.isEmpty()) return@withContext emptyMap()

        val imagesById = mediaStoreDataSource.getImagesByIds(imageIds).associateBy { it.id }
        overlayDetectionDao.getByImageIds(imageIds)
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
        images.map { image -> detectOverlay(image, modelVersion) }
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
        modelVersion: String
    ): OverlayDetection {
        val analysis = runCatching { analyzeImageContent(image) }.getOrDefault(OverlayImageAnalysis.analyze(
            pixels = IntArray(MIN_ANALYSIS_DIMENSION * MIN_ANALYSIS_DIMENSION) { DEFAULT_PIXEL },
            width = MIN_ANALYSIS_DIMENSION,
            height = MIN_ANALYSIS_DIMENSION
        ))

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

    private fun analyzeImageContent(image: ImageItem): OverlayAnalysisResult {
        val bitmap = decodeAnalysisBitmap(image)
            ?: throw IOException("Unable to decode bitmap for overlay analysis.")
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
        private const val MIN_ANALYSIS_DIMENSION = 16
        private const val DEFAULT_PIXEL = -9671572
    }
}
