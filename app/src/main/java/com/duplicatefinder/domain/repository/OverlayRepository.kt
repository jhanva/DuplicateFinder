package com.duplicatefinder.domain.repository

import com.duplicatefinder.domain.model.OverlayDetection
import com.duplicatefinder.domain.model.ImageItem

interface OverlayRepository {
    suspend fun getCachedDetections(
        imageIds: List<Long>,
        modelVersion: String
    ): Map<Long, OverlayDetection>

    suspend fun detectOverlayCandidates(
        images: List<ImageItem>,
        modelVersion: String
    ): List<OverlayDetection>

    suspend fun saveDetections(detections: List<OverlayDetection>)
}
