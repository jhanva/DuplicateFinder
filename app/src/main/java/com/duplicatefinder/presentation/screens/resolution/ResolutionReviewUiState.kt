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
    val sliderMegapixelMax: Float
        get() = maxOf(
            DEFAULT_REVIEW_MEGAPIXEL_MAX,
            resolutionItems.maxOfOrNull { it.megapixels } ?: DEFAULT_REVIEW_MEGAPIXEL_MAX
        )

    val filteredResolutionItems: List<ResolutionReviewItem> =
        resolutionItems.filter { item ->
            item.megapixels in reviewMegapixelMin..reviewMegapixelMax
        }

    val totalCount: Int
        get() = filteredResolutionItems.size

    val hasItems: Boolean
        get() = resolutionItems.isNotEmpty()

    val hasFilterMatches: Boolean
        get() = filteredResolutionItems.isNotEmpty()

    val currentItem: ResolutionReviewItem?
        get() = filteredResolutionItems.getOrNull(currentIndex)

    val reviewedCount: Int
        get() = filteredResolutionItems.count { item ->
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
        get() = !isScanning && !requiresFolderSelection && resolutionItems.isEmpty() && error == null

    val hasNoFilterMatches: Boolean
        get() = !isScanning && hasItems && !hasFilterMatches

    val showFullScanProgress: Boolean
        get() = isScanning && !hasFilterMatches

    val showInlineScanProgress: Boolean
        get() = isScanning && hasFilterMatches

    val filteredOutCount: Int
        get() = resolutionItems.size - filteredResolutionItems.size

    companion object {
        const val DEFAULT_REVIEW_MEGAPIXEL_MIN = 0f
        const val DEFAULT_REVIEW_MEGAPIXEL_MAX = 12f
    }
}
