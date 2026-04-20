package com.duplicatefinder.data.repository

import com.duplicatefinder.data.local.db.dao.OverlayDetectionDao
import com.duplicatefinder.data.local.db.entities.OverlayDetectionEntity
import com.duplicatefinder.data.media.MediaStoreDataSource
import com.duplicatefinder.domain.model.DetectionStage
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.OverlayDetection
import com.duplicatefinder.domain.model.OverlayKind
import com.duplicatefinder.domain.model.OverlayRegion
import com.duplicatefinder.domain.repository.OverlayRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlayRepositoryImpl @Inject constructor(
    private val overlayDetectionDao: OverlayDetectionDao,
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
        val normalizedName = image.name.lowercase()
        val normalizedPath = image.path.lowercase()
        val keywords = linkedMapOf(
            OverlayKind.HANDLE to listOf("@"),
            OverlayKind.SIGNATURE to listOf("sign", "firma", "signed"),
            OverlayKind.LOGO to listOf("logo", "watermark", "wm"),
            OverlayKind.DATE_STAMP to listOf("date", "timestamp"),
            OverlayKind.CAPTION to listOf("meme", "caption", "quote"),
            OverlayKind.STICKER_TEXT to listOf("sticker"),
            OverlayKind.TEXT to listOf("text")
        )

        val matchedKinds = keywords
            .filterValues { tokens ->
                tokens.any { token ->
                    normalizedName.contains(token) || normalizedPath.contains(token)
                }
            }
            .keys
            .ifEmpty { setOf(OverlayKind.UNKNOWN) }

        val keywordHits = matchedKinds.size
        val preliminaryScore = (
            0.1f +
                (keywordHits * 0.18f) +
                scoreForDimensions(image)
            ).coerceIn(0f, 1f)
        val refinedScore = (
            preliminaryScore +
                scoreForAspectRatio(image) +
                if (OverlayKind.HANDLE in matchedKinds) 0.1f else 0f
            ).coerceIn(0f, 1f)
        val coverage = coverageForKinds(matchedKinds).coerceIn(0.02f, 0.45f)
        val regions = buildRegions(matchedKinds, refinedScore)

        return OverlayDetection(
            image = image,
            preliminaryScore = preliminaryScore,
            refinedScore = refinedScore,
            overlayCoverageRatio = coverage,
            maskBounds = regions,
            maskConfidence = refinedScore,
            overlayKinds = matchedKinds,
            stage = if (refinedScore > preliminaryScore) {
                DetectionStage.STAGE_2_REFINED
            } else {
                DetectionStage.STAGE_1_CANDIDATE
            },
            modelVersion = modelVersion
        )
    }

    private fun scoreForDimensions(image: ImageItem): Float {
        val megapixels = (image.width * image.height) / 1_000_000f
        return when {
            megapixels >= 10f -> 0.08f
            megapixels >= 4f -> 0.04f
            else -> 0f
        }
    }

    private fun scoreForAspectRatio(image: ImageItem): Float {
        if (image.width == 0 || image.height == 0) return 0f
        val ratio = image.width.toFloat() / image.height.toFloat()
        return if (ratio > 1.7f || ratio < 0.6f) 0.05f else 0f
    }

    private fun coverageForKinds(kinds: Set<OverlayKind>): Float {
        return when {
            OverlayKind.CAPTION in kinds || OverlayKind.STICKER_TEXT in kinds -> 0.35f
            OverlayKind.LOGO in kinds || OverlayKind.SIGNATURE in kinds -> 0.12f
            OverlayKind.HANDLE in kinds || OverlayKind.DATE_STAMP in kinds -> 0.08f
            else -> 0.05f
        }
    }

    private fun buildRegions(
        kinds: Set<OverlayKind>,
        confidence: Float
    ): List<OverlayRegion> {
        val regions = mutableListOf<OverlayRegion>()
        if (OverlayKind.CAPTION in kinds || OverlayKind.STICKER_TEXT in kinds) {
            regions += OverlayRegion(
                left = 0.05f,
                top = 0.72f,
                right = 0.95f,
                bottom = 0.94f,
                confidence = confidence,
                kind = OverlayKind.CAPTION
            )
        }
        if (OverlayKind.HANDLE in kinds || OverlayKind.DATE_STAMP in kinds) {
            regions += OverlayRegion(
                left = 0.6f,
                top = 0.02f,
                right = 0.95f,
                bottom = 0.16f,
                confidence = confidence,
                kind = if (OverlayKind.HANDLE in kinds) OverlayKind.HANDLE else OverlayKind.DATE_STAMP
            )
        }
        if (regions.isEmpty()) {
            regions += OverlayRegion(
                left = 0.7f,
                top = 0.78f,
                right = 0.95f,
                bottom = 0.94f,
                confidence = confidence,
                kind = kinds.firstOrNull() ?: OverlayKind.UNKNOWN
            )
        }
        return regions
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
}
