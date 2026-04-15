package com.duplicatefinder.presentation.screens.resolution

import android.content.IntentSender
import com.duplicatefinder.domain.model.ResolutionReviewItem
import com.duplicatefinder.domain.model.ScanProgress

data class ResolutionReviewUiState(
    val isScanning: Boolean = false,
    val isApplyingBatch: Boolean = false,
    val isPaused: Boolean = false,
    val scanProgress: ScanProgress = ScanProgress.initial(),
    val resolutionItems: List<ResolutionReviewItem> = emptyList(),
    val reviewMegapixelMin: Float = DEFAULT_REVIEW_MEGAPIXEL_MIN,
    val reviewMegapixelMax: Float = DEFAULT_REVIEW_MEGAPIXEL_MAX,
    val currentIndex: Int = -1,
    val keptImageIds: Set<Long> = emptySet(),
    val markedForTrashIds: Set<Long> = emptySet(),
    val movedToTrashIds: Set<Long> = emptySet(),
    val pendingBatchIds: Set<Long> = emptySet(),
    val pendingDeleteIntentSender: IntentSender? = null,
    val requiresFolderSelection: Boolean = false,
    val error: String? = null
) {
    val sliderMegapixelMax: Float = maxOf(
        DEFAULT_REVIEW_MEGAPIXEL_MAX,
        resolutionItems.maxOfOrNull { it.megapixels } ?: DEFAULT_REVIEW_MEGAPIXEL_MAX
    )

    val filteredResolutionItems: List<ResolutionReviewItem> =
        resolutionItems.filter { item ->
            item.megapixels in reviewMegapixelMin..reviewMegapixelMax
        }

    val totalCount: Int = filteredResolutionItems.size

    val hasItems: Boolean = resolutionItems.isNotEmpty()

    val hasFilterMatches: Boolean = filteredResolutionItems.isNotEmpty()

    val currentItem: ResolutionReviewItem? = filteredResolutionItems.getOrNull(currentIndex)

    val reviewedCount: Int = filteredResolutionItems.count { item ->
        val id = item.image.id
        id in keptImageIds || id in markedForTrashIds || id in movedToTrashIds
    }

    val pendingBatchCount: Int = markedForTrashIds.size

    val movedToTrashCount: Int = movedToTrashIds.size

    val keptCount: Int = keptImageIds.size

    val isReviewComplete: Boolean = !isScanning && hasFilterMatches && currentItem == null

    val hasNoResults: Boolean =
        !isScanning && !requiresFolderSelection && resolutionItems.isEmpty() && error == null

    val hasNoFilterMatches: Boolean = !isScanning && hasItems && !hasFilterMatches

    val filteredOutCount: Int = resolutionItems.size - filteredResolutionItems.size

    companion object {
        const val DEFAULT_REVIEW_MEGAPIXEL_MIN = 0f
        const val DEFAULT_REVIEW_MEGAPIXEL_MAX = 12f
    }
}
