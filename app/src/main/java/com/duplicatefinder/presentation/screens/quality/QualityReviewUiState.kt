package com.duplicatefinder.presentation.screens.quality

import android.content.IntentSender
import com.duplicatefinder.domain.model.ImageQualityItem
import com.duplicatefinder.domain.model.ScanProgress

data class QualityReviewUiState(
    val isScanning: Boolean = false,
    val isApplyingBatch: Boolean = false,
    val isPaused: Boolean = false,
    val scanProgress: ScanProgress = ScanProgress.initial(),
    val qualityItems: List<ImageQualityItem> = emptyList(),
    val currentIndex: Int = -1,
    val keptImageIds: Set<Long> = emptySet(),
    val markedForTrashIds: Set<Long> = emptySet(),
    val movedToTrashIds: Set<Long> = emptySet(),
    val pendingBatchIds: Set<Long> = emptySet(),
    val pendingDeleteIntentSender: IntentSender? = null,
    val requiresFolderSelection: Boolean = false,
    val error: String? = null
) {
    val totalCount: Int
        get() = qualityItems.size

    val hasItems: Boolean
        get() = qualityItems.isNotEmpty()

    val currentItem: ImageQualityItem?
        get() = qualityItems.getOrNull(currentIndex)

    val reviewedCount: Int
        get() = keptImageIds.size + markedForTrashIds.size + movedToTrashIds.size

    val pendingBatchCount: Int
        get() = markedForTrashIds.size

    val movedToTrashCount: Int
        get() = movedToTrashIds.size

    val keptCount: Int
        get() = keptImageIds.size

    val isReviewComplete: Boolean
        get() = !isScanning && hasItems && currentItem == null

    val hasNoResults: Boolean
        get() = !isScanning && !requiresFolderSelection && qualityItems.isEmpty() && error == null
}

