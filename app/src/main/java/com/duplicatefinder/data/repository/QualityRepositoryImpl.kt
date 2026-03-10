package com.duplicatefinder.data.repository

import com.duplicatefinder.data.local.db.dao.ImageQualityDao
import com.duplicatefinder.data.local.db.entities.ImageQualityEntity
import com.duplicatefinder.domain.model.CachedImageQuality
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.ImageQualityMetrics
import com.duplicatefinder.domain.model.ImageQualityUpdate
import com.duplicatefinder.domain.repository.QualityRepository
import com.duplicatefinder.util.image.ImageQualityAnalyzer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QualityRepositoryImpl @Inject constructor(
    private val imageQualityDao: ImageQualityDao,
    private val imageQualityAnalyzer: ImageQualityAnalyzer
) : QualityRepository {

    override suspend fun getCachedQualities(
        imageIds: List<Long>
    ): Map<Long, CachedImageQuality> {
        if (imageIds.isEmpty()) return emptyMap()

        val result = mutableMapOf<Long, CachedImageQuality>()
        imageIds.chunked(QUERY_BATCH_SIZE).forEach { batch ->
            imageQualityDao.getByImageIds(batch).forEach { entity ->
                result[entity.imageId] = CachedImageQuality(
                    qualityScore = entity.qualityScore,
                    sharpness = entity.sharpness,
                    detailDensity = entity.detailDensity,
                    blockiness = entity.blockiness,
                    dateModified = entity.dateModified,
                    size = entity.size
                )
            }
        }
        return result
    }

    override suspend fun saveQualityScores(updates: List<ImageQualityUpdate>) {
        if (updates.isEmpty()) return
        val entities = updates.map { update ->
            ImageQualityEntity(
                imageId = update.image.id,
                path = update.image.path,
                qualityScore = update.qualityScore,
                sharpness = update.sharpness,
                detailDensity = update.detailDensity,
                blockiness = update.blockiness,
                dateModified = update.image.dateModified,
                size = update.image.size
            )
        }
        imageQualityDao.insertAll(entities)
    }

    override suspend fun calculateQualityMetrics(image: ImageItem): ImageQualityMetrics? {
        return imageQualityAnalyzer.analyze(image)
    }
}

private const val QUERY_BATCH_SIZE = 900

