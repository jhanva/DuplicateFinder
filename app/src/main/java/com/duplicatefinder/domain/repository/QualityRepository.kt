package com.duplicatefinder.domain.repository

import com.duplicatefinder.domain.model.CachedImageQuality
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.ImageQualityMetrics
import com.duplicatefinder.domain.model.ImageQualityUpdate

interface QualityRepository {
    suspend fun getCachedQualities(imageIds: List<Long>): Map<Long, CachedImageQuality>
    suspend fun saveQualityScores(updates: List<ImageQualityUpdate>)
    suspend fun calculateQualityMetrics(image: ImageItem): ImageQualityMetrics?
}

