package com.duplicatefinder.presentation.screens.overlay

import android.content.Intent
import android.content.IntentSender
import com.duplicatefinder.domain.model.OverlayReviewItem
import com.duplicatefinder.domain.model.ScanProgress

data class OverlayReviewUiState(
    val isScanning: Boolean = false,
    val isApplyingBatch: Boolean = false,
    val isPaused: Boolean = false,
    val scanProgress: ScanProgress = ScanProgress.initial(),
    val overlayItems: List<OverlayReviewItem> = emptyList(),
    val minOverlayScore: Float = DEFAULT_MIN_OVERLAY_SCORE,
    val maxOverlayScore: Float = DEFAULT_MAX_OVERLAY_SCORE,
    val currentIndex: Int = -1,
    val keptImageIds: Set<Long> = emptySet(),
    val markedForTrashIds: Set<Long> = emptySet(),
    val movedToTrashIds: Set<Long> = emptySet(),
    val editedInGalleryIds: Set<Long> = emptySet(),
    val pendingBatchIds: Set<Long> = emptySet(),
    val pendingDeleteIntentSender: IntentSender? = null,
    val externalEditSession: OverlayExternalEditSession? = null,
    val pendingExternalEditIntent: Intent? = null,
    val samsungGalleryDisabledReason: String? = null,
    val requiresFolderSelection: Boolean = false,
    val error: String? = null
) {
    val filteredOverlayItems: List<OverlayReviewItem> =
        overlayItems
            .filter { item ->
                item.rankScore in minOverlayScore..maxOverlayScore
            }
            .sortedByDescending { it.rankScore }

    val totalCount: Int
        get() = filteredOverlayItems.size

    val hasItems: Boolean
        get() = overlayItems.isNotEmpty()

    val hasFilterMatches: Boolean
        get() = filteredOverlayItems.isNotEmpty()

    val currentItem: OverlayReviewItem?
        get() = filteredOverlayItems.getOrNull(currentIndex)

    val reviewedCount: Int
        get() = filteredOverlayItems.count { item ->
            val id = item.image.id
            id in keptImageIds ||
                id in markedForTrashIds ||
                id in movedToTrashIds ||
                id in editedInGalleryIds
        }

    val pendingBatchCount: Int
        get() = markedForTrashIds.size

    val keptCount: Int
        get() = keptImageIds.size

    val movedToTrashCount: Int
        get() = movedToTrashIds.size

    val editedInGalleryCount: Int
        get() = editedInGalleryIds.size

    val isReviewComplete: Boolean
        get() = !isScanning && hasFilterMatches && currentItem == null

    val hasNoResults: Boolean
        get() = !isScanning && !requiresFolderSelection && overlayItems.isEmpty() && error == null

    val hasNoFilterMatches: Boolean
        get() = !isScanning && hasItems && !hasFilterMatches

    val showFullScanProgress: Boolean
        get() = isScanning && !hasFilterMatches

    val showInlineScanProgress: Boolean
        get() = isScanning && hasFilterMatches

    val filteredOutCount: Int
        get() = overlayItems.size - filteredOverlayItems.size

    companion object {
        const val DEFAULT_MIN_OVERLAY_SCORE = 0f
        const val DEFAULT_MAX_OVERLAY_SCORE = 1f
    }
}
