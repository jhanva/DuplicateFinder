package com.duplicatefinder.presentation.screens.quality

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duplicatefinder.domain.model.ImageQualityItem
import com.duplicatefinder.domain.model.ScanPhase
import com.duplicatefinder.domain.model.UserConfirmationRequiredException
import com.duplicatefinder.domain.repository.ImageRepository
import com.duplicatefinder.domain.repository.SettingsRepository
import com.duplicatefinder.domain.usecase.MoveToTrashUseCase
import com.duplicatefinder.domain.usecase.ScanQualityImagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QualityReviewViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val imageRepository: ImageRepository,
    private val scanQualityImagesUseCase: ScanQualityImagesUseCase,
    private val moveToTrashUseCase: MoveToTrashUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(QualityReviewUiState())
    val uiState: StateFlow<QualityReviewUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    fun startReview() {
        if (scanJob?.isActive == true) return

        scanJob = viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isScanning = true,
                        isPaused = false,
                        error = null,
                        requiresFolderSelection = false,
                        qualityItems = emptyList(),
                        reviewScoreMin = QualityReviewUiState.DEFAULT_REVIEW_SCORE_MIN,
                        reviewScoreMax = QualityReviewUiState.DEFAULT_REVIEW_SCORE_MAX,
                        currentIndex = -1,
                        keptImageIds = emptySet(),
                        markedForTrashIds = emptySet(),
                        movedToTrashIds = emptySet(),
                        pendingBatchIds = emptySet(),
                        pendingDeleteIntentSender = null
                    )
                }

                val selectedFolders = settingsRepository.scanFolders.first()
                if (selectedFolders.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isScanning = false,
                            requiresFolderSelection = true,
                            scanProgress = it.scanProgress.copy(phase = ScanPhase.ERROR),
                            error = "Select at least one folder from Home before starting quality review."
                        )
                    }
                    return@launch
                }

                scanQualityImagesUseCase(selectedFolders).collect { scanState ->
                    _uiState.update { current ->
                        if (scanState.progress.phase == ScanPhase.COMPLETE) {
                            val filteredItems = filterItemsByRange(
                                items = scanState.items,
                                minScore = current.reviewScoreMin,
                                maxScore = current.reviewScoreMax
                            )
                            val firstIndex = nextUndecidedIndex(
                                items = filteredItems,
                                start = 0,
                                kept = emptySet(),
                                marked = emptySet(),
                                moved = emptySet()
                            )
                            current.copy(
                                isScanning = false,
                                scanProgress = scanState.progress,
                                qualityItems = scanState.items,
                                currentIndex = firstIndex,
                                error = null
                            )
                        } else {
                            current.copy(
                                isScanning = true,
                                scanProgress = scanState.progress
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        error = e.message ?: "Failed to review image quality."
                    )
                }
            }
        }
    }

    fun keepCurrent() {
        val state = _uiState.value
        if (state.isPaused || state.isScanning) return
        val current = state.currentItem ?: return

        _uiState.update {
            val kept = it.keptImageIds + current.image.id
            it.copy(
                keptImageIds = kept,
                currentIndex = nextUndecidedIndex(
                    items = it.filteredQualityItems,
                    start = (it.currentIndex + 1).coerceAtLeast(0),
                    kept = kept,
                    marked = it.markedForTrashIds,
                    moved = it.movedToTrashIds
                )
            )
        }
    }

    fun markCurrentForTrash() {
        val state = _uiState.value
        if (state.isPaused || state.isScanning) return
        val current = state.currentItem ?: return

        _uiState.update {
            val marked = it.markedForTrashIds + current.image.id
            it.copy(
                markedForTrashIds = marked,
                currentIndex = nextUndecidedIndex(
                    items = it.filteredQualityItems,
                    start = (it.currentIndex + 1).coerceAtLeast(0),
                    kept = it.keptImageIds,
                    marked = marked,
                    moved = it.movedToTrashIds
                )
            )
        }
    }

    fun updateReviewScoreRange(minScore: Int, maxScore: Int) {
        _uiState.update { state ->
            val normalizedMin = minScore.coerceIn(
                QualityReviewUiState.DEFAULT_REVIEW_SCORE_MIN,
                QualityReviewUiState.DEFAULT_REVIEW_SCORE_MAX
            )
            val normalizedMax = maxScore.coerceIn(
                QualityReviewUiState.DEFAULT_REVIEW_SCORE_MIN,
                QualityReviewUiState.DEFAULT_REVIEW_SCORE_MAX
            )
            val rangeMin = minOf(normalizedMin, normalizedMax)
            val rangeMax = maxOf(normalizedMin, normalizedMax)
            val filteredItems = filterItemsByRange(
                items = state.qualityItems,
                minScore = rangeMin,
                maxScore = rangeMax
            )
            val currentId = state.currentItem?.image?.id
            state.copy(
                reviewScoreMin = rangeMin,
                reviewScoreMax = rangeMax,
                currentIndex = resolveCurrentIndexAfterRangeChange(
                    items = filteredItems,
                    currentId = currentId,
                    kept = state.keptImageIds,
                    marked = state.markedForTrashIds,
                    moved = state.movedToTrashIds
                )
            )
        }
    }

    fun pauseReview() {
        _uiState.update { it.copy(isPaused = true) }
    }

    fun resumeReview() {
        _uiState.update { it.copy(isPaused = false) }
    }

    fun applyBatchToTrash() {
        val ids = _uiState.value.markedForTrashIds
        if (ids.isEmpty()) return
        applyBatchToTrash(ids)
    }

    fun onDeleteConfirmationResult(granted: Boolean) {
        val pendingIds = _uiState.value.pendingBatchIds
        _uiState.update { it.copy(pendingDeleteIntentSender = null) }

        if (!granted) {
            _uiState.update {
                it.copy(
                    isApplyingBatch = false,
                    pendingBatchIds = emptySet(),
                    error = "Storage permission was not granted. Batch move was canceled."
                )
            }
            return
        }

        if (pendingIds.isNotEmpty()) {
            applyBatchToTrash(pendingIds)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun applyBatchToTrash(ids: Set<Long>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingBatch = true, error = null, pendingBatchIds = ids) }

            val state = _uiState.value
            val images = state.qualityItems
                .filter { it.image.id in ids }
                .map { it.image }

            if (images.isEmpty()) {
                _uiState.update { it.copy(isApplyingBatch = false, pendingBatchIds = emptySet()) }
                return@launch
            }

            val result = moveToTrashUseCase(images)
            result.onSuccess {
                val moved = resolveMovedIds(ids)

                _uiState.update { state ->
                    state
                        .withMovedToTrash(moved)
                        .copy(
                        isApplyingBatch = false,
                        pendingBatchIds = emptySet(),
                        error = if (moved.size < ids.size) {
                            "Only ${moved.size} of ${ids.size} images were moved to trash."
                        } else {
                            null
                        }
                    )
                }
            }

            result.onFailure { error ->
                val moved = resolveMovedIds(ids)
                if (error is UserConfirmationRequiredException) {
                    val pendingIds = ids - moved
                    _uiState.update { state ->
                        state
                            .withMovedToTrash(moved)
                            .copy(
                            isApplyingBatch = false,
                            pendingBatchIds = pendingIds,
                            pendingDeleteIntentSender = if (pendingIds.isNotEmpty()) {
                                error.intentSender
                            } else {
                                null
                            }
                        )
                    }
                } else {
                    _uiState.update { state ->
                        state
                            .withMovedToTrash(moved)
                            .copy(
                            isApplyingBatch = false,
                            pendingBatchIds = emptySet(),
                            error = error.message ?: "Failed to move images to trash."
                        )
                    }
                }
            }
        }
    }

    private suspend fun resolveMovedIds(ids: Set<Long>): Set<Long> {
        if (ids.isEmpty()) return emptySet()
        val remaining = imageRepository.getImagesByIds(ids.toList()).map { it.id }.toSet()
        return ids - remaining
    }

    private fun QualityReviewUiState.withMovedToTrash(moved: Set<Long>): QualityReviewUiState {
        if (moved.isEmpty()) return this
        val marked = markedForTrashIds - moved
        val movedSet = movedToTrashIds + moved
        return copy(
            markedForTrashIds = marked,
            movedToTrashIds = movedSet,
            currentIndex = nextUndecidedIndex(
                items = filteredQualityItems,
                start = if (currentItem == null) filteredQualityItems.size else currentIndex,
                kept = keptImageIds,
                marked = marked,
                moved = movedSet
            )
        )
    }

    private fun nextUndecidedIndex(
        items: List<ImageQualityItem>,
        start: Int,
        kept: Set<Long>,
        marked: Set<Long>,
        moved: Set<Long>
    ): Int {
        for (index in start until items.size) {
            val id = items[index].image.id
            if (id !in kept && id !in marked && id !in moved) {
                return index
            }
        }
        return -1
    }

    private fun filterItemsByRange(
        items: List<ImageQualityItem>,
        minScore: Int,
        maxScore: Int
    ): List<ImageQualityItem> {
        val min = minScore.toFloat()
        val max = maxScore.toFloat()
        return items.filter { it.qualityScore in min..max }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}

internal fun resolveCurrentIndexAfterRangeChange(
    items: List<ImageQualityItem>,
    currentId: Long?,
    kept: Set<Long>,
    marked: Set<Long>,
    moved: Set<Long>
): Int {
    val firstUndecided = nextUndecidedIndexForRangeChange(
        items = items,
        start = 0,
        kept = kept,
        marked = marked,
        moved = moved
    )

    if (currentId == null || currentId in kept || currentId in marked || currentId in moved) {
        return firstUndecided
    }

    val currentIndex = items.indexOfFirst { it.image.id == currentId }
    if (currentIndex < 0) {
        return firstUndecided
    }

    return if (firstUndecided in 0 until currentIndex) {
        firstUndecided
    } else {
        currentIndex
    }
}

private fun nextUndecidedIndexForRangeChange(
    items: List<ImageQualityItem>,
    start: Int,
    kept: Set<Long>,
    marked: Set<Long>,
    moved: Set<Long>
): Int {
    for (index in start until items.size) {
        val id = items[index].image.id
        if (id !in kept && id !in marked && id !in moved) {
            return index
        }
    }
    return -1
}
