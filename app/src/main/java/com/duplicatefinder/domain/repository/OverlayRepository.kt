package com.duplicatefinder.domain.repository

import com.duplicatefinder.domain.model.OverlayDetection
import com.duplicatefinder.domain.model.ImageItem

interface OverlayRepository {
    suspend fun getCachedDetections(
        images: List<ImageItem>,
        modelVersion: String
    ): Map<Long, OverlayDetection>

    /**
     * Runs overlay detection for [images]. Images that fail to decode or
     * analyze are skipped (not present in the result) so they are retried on
     * the next scan instead of being cached with fabricated scores.
     */
    suspend fun detectOverlayCandidates(
        images: List<ImageItem>,
        modelVersion: String
    ): List<OverlayDetection>

    suspend fun saveDetections(detections: List<OverlayDetection>)
}
