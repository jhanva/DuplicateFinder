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
    val reviewScoreMin: Int = DEFAULT_REVIEW_SCORE_MIN,
    val reviewScoreMax: Int = DEFAULT_REVIEW_SCORE_MAX,
    val currentIndex: Int = -1,
    val keptImageIds: Set<Long> = emptySet(),
    val markedForTrashIds: Set<Long> = emptySet(),
    val movedToTrashIds: Set<Long> = emptySet(),
    val pendingBatchIds: Set<Long> = emptySet(),
    val pendingDeleteIntentSender: IntentSender? = null,
    val requiresFolderSelection: Boolean = false,
    val error: String? = null
) {
    val filteredQualityItems: List<ImageQualityItem>
        get() = qualityItems.filter { item ->
            item.qualityScore in reviewScoreMin.toFloat()..reviewScoreMax.toFloat()
        }

    val totalCount: Int
        get() = filteredQualityItems.size

    val hasItems: Boolean
        get() = qualityItems.isNotEmpty()

    val hasFilterMatches: Boolean
        get() = filteredQualityItems.isNotEmpty()

    val isFilterActive: Boolean
        get() = reviewScoreMin > DEFAULT_REVIEW_SCORE_MIN || reviewScoreMax < DEFAULT_REVIEW_SCORE_MAX

    val currentItem: ImageQualityItem?
        get() = filteredQualityItems.getOrNull(currentIndex)

    val reviewedCount: Int
        get() = filteredQualityItems.count { item ->
            val id = item.image.id
            id in keptImageIds || id in markedForTrashIds || id in movedToTrashIds
        }

    val pendingBatchCount: Int
        get() = markedForTrashIds.size

    val movedToTrashCount: Int
        get() = movedToTrashIds.size

    val keptCount: Int
        get() = keptImageIds.size

    val isReviewComplete: Boolean
        get() = !isScanning && hasFilterMatches && currentItem == null

    val hasNoResults: Boolean
        get() = !isScanning && !requiresFolderSelection && qualityItems.isEmpty() && error == null

    val hasNoFilterMatches: Boolean
        get() = !isScanning && hasItems && !hasFilterMatches

    val filteredOutCount: Int
        get() = qualityItems.size - filteredQualityItems.size

    companion object {
        const val DEFAULT_REVIEW_SCORE_MIN = 0
        const val DEFAULT_REVIEW_SCORE_MAX = 100
    }
}

