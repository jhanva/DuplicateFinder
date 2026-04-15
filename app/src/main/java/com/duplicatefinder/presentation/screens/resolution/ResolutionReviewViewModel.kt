package com.duplicatefinder.presentation.screens.resolution

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duplicatefinder.domain.model.ResolutionReviewItem
import com.duplicatefinder.domain.model.ScanPhase
import com.duplicatefinder.domain.model.UserConfirmationRequiredException
import com.duplicatefinder.domain.repository.ImageRepository
import com.duplicatefinder.domain.repository.SettingsRepository
import com.duplicatefinder.domain.usecase.MoveToTrashUseCase
import com.duplicatefinder.domain.usecase.ScanResolutionImagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ResolutionReviewViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val imageRepository: ImageRepository,
    private val scanResolutionImagesUseCase: ScanResolutionImagesUseCase,
    private val moveToTrashUseCase: MoveToTrashUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResolutionReviewUiState())
    val uiState: StateFlow<ResolutionReviewUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    fun startReview() {
        if (scanJob?.isActive == true) return
        if (_uiState.value.resolutionItems.isNotEmpty()) return

        scanJob = viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isScanning = true,
                        isPaused = false,
                        error = null,
                        requiresFolderSelection = false,
                        resolutionItems = emptyList(),
                        reviewMegapixelMin = ResolutionReviewUiState.DEFAULT_REVIEW_MEGAPIXEL_MIN,
                        reviewMegapixelMax = ResolutionReviewUiState.DEFAULT_REVIEW_MEGAPIXEL_MAX,
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
                            error = "Select at least one folder from Home before starting resolution review."
                        )
                    }
                    return@launch
                }

                scanResolutionImagesUseCase(selectedFolders).collect { scanState ->
                    _uiState.update { current ->
                        if (scanState.progress.phase == ScanPhase.COMPLETE) {
                            val sliderMax = maxOf(
                                ResolutionReviewUiState.DEFAULT_REVIEW_MEGAPIXEL_MAX,
                                scanState.items.maxOfOrNull { it.megapixels }
                                    ?: ResolutionReviewUiState.DEFAULT_REVIEW_MEGAPIXEL_MAX
                            )
                            val reviewMin = ResolutionReviewUiState.DEFAULT_REVIEW_MEGAPIXEL_MIN
                            val reviewMax = minOf(
                                ResolutionReviewUiState.DEFAULT_REVIEW_MEGAPIXEL_MAX,
                                sliderMax
                            )
                            val filteredItems = filterItemsByRange(
                                items = scanState.items,
                                minMegapixels = reviewMin,
                                maxMegapixels = reviewMax
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
                                resolutionItems = scanState.items,
                                reviewMegapixelMin = reviewMin,
                                reviewMegapixelMax = reviewMax,
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
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        error = "Failed to review image resolution."
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
                    items = it.filteredResolutionItems,
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
                    items = it.filteredResolutionItems,
                    start = (it.currentIndex + 1).coerceAtLeast(0),
                    kept = it.keptImageIds,
                    marked = marked,
                    moved = it.movedToTrashIds
                )
            )
        }
    }

    fun updateReviewMegapixelRange(minMegapixels: Float, maxMegapixels: Float) {
        _uiState.update { state ->
            val sliderMax = state.sliderMegapixelMax
            val normalizedMin = normalizeMegapixels(minMegapixels).coerceIn(
                ResolutionReviewUiState.DEFAULT_REVIEW_MEGAPIXEL_MIN,
                sliderMax
            )
            val normalizedMax = normalizeMegapixels(maxMegapixels).coerceIn(
                ResolutionReviewUiState.DEFAULT_REVIEW_MEGAPIXEL_MIN,
                sliderMax
            )
            val rangeMin = minOf(normalizedMin, normalizedMax)
            val rangeMax = maxOf(normalizedMin, normalizedMax)
            val filteredItems = filterItemsByRange(
                items = state.resolutionItems,
                minMegapixels = rangeMin,
                maxMegapixels = rangeMax
            )
            val currentId = state.currentItem?.image?.id
            state.copy(
                reviewMegapixelMin = rangeMin,
                reviewMegapixelMax = rangeMax,
                currentIndex = resolveCurrentIndexAfterMegapixelRangeChange(
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
            val images = _uiState.value.resolutionItems
                .filter { it.image.id in ids }
                .map { it.image }

            if (images.isEmpty()) return@launch

            _uiState.update { it.copy(isApplyingBatch = true, error = null, pendingBatchIds = ids) }

            val result = moveToTrashUseCase(images)
            result.onSuccess {
                val moved = resolveMovedIds(ids)

                _uiState.update { current ->
                    current
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
                    _uiState.update { current ->
                        current
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
                    _uiState.update { current ->
                        current
                            .withMovedToTrash(moved)
                            .copy(
                                isApplyingBatch = false,
                                pendingBatchIds = emptySet(),
                                error = "Failed to move images to trash."
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

    private fun ResolutionReviewUiState.withMovedToTrash(moved: Set<Long>): ResolutionReviewUiState {
        if (moved.isEmpty()) return this
        val marked = markedForTrashIds - moved
        val movedSet = movedToTrashIds + moved
        return copy(
            markedForTrashIds = marked,
            movedToTrashIds = movedSet,
            currentIndex = nextUndecidedIndex(
                items = filteredResolutionItems,
                start = if (currentItem == null) filteredResolutionItems.size else currentIndex,
                kept = keptImageIds,
                marked = marked,
                moved = movedSet
            )
        )
    }

    private fun nextUndecidedIndex(
        items: List<ResolutionReviewItem>,
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
        items: List<ResolutionReviewItem>,
        minMegapixels: Float,
        maxMegapixels: Float
    ): List<ResolutionReviewItem> {
        return items.filter { it.megapixels in minMegapixels..maxMegapixels }
    }

    private fun normalizeMegapixels(value: Float): Float {
        return (value * 10f).roundToInt() / 10f
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}

internal fun resolveCurrentIndexAfterMegapixelRangeChange(
    items: List<ResolutionReviewItem>,
    currentId: Long?,
    kept: Set<Long>,
    marked: Set<Long>,
    moved: Set<Long>
): Int {
    val firstUndecided = nextUndecidedMegapixelIndexForRangeChange(
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

private fun nextUndecidedMegapixelIndexForRangeChange(
    items: List<ResolutionReviewItem>,
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
