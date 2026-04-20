package com.duplicatefinder.domain.repository

import com.duplicatefinder.domain.model.CleaningPreview
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.OverlayDetection
import com.duplicatefinder.domain.model.OverlayPreviewDecision

interface OverlayCleaningRepository {
    suspend fun generatePreview(
        image: ImageItem,
        detection: OverlayDetection,
        bundleInfo: OverlayModelBundleInfo
    ): Result<CleaningPreview>

    suspend fun applyDecision(
        image: ImageItem,
        preview: CleaningPreview,
        decision: OverlayPreviewDecision
    ): Result<Unit>

    suspend fun discardPreview(preview: CleaningPreview): Result<Unit>
}
