package com.duplicatefinder.presentation.screens.overlay

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duplicatefinder.domain.model.OverlayPreviewDecision
import com.duplicatefinder.domain.model.OverlayReviewItem
import com.duplicatefinder.domain.model.ScanPhase
import com.duplicatefinder.domain.model.UserConfirmationRequiredException
import com.duplicatefinder.domain.repository.ImageRepository
import com.duplicatefinder.domain.repository.SettingsRepository
import com.duplicatefinder.domain.usecase.ApplyOverlayPreviewDecisionUseCase
import com.duplicatefinder.domain.usecase.GenerateOverlayPreviewUseCase
import com.duplicatefinder.domain.usecase.MoveToTrashUseCase
import com.duplicatefinder.domain.usecase.ScanOverlayCandidatesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class OverlayReviewViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val imageRepository: ImageRepository,
    private val scanOverlayCandidatesUseCase: ScanOverlayCandidatesUseCase,
    private val generateOverlayPreviewUseCase: GenerateOverlayPreviewUseCase,
    private val applyOverlayPreviewDecisionUseCase: ApplyOverlayPreviewDecisionUseCase,
    private val moveToTrashUseCase: MoveToTrashUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverlayReviewUiState())
    val uiState: StateFlow<OverlayReviewUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    fun startReview() {
        if (scanJob?.isActive == true) return

        scanJob = viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isScanning = true,
                        isPaused = false,
                        isGeneratingPreview = false,
                        error = null,
                        requiresFolderSelection = false,
                        overlayItems = emptyList(),
                        minOverlayScore = OverlayReviewUiState.DEFAULT_MIN_OVERLAY_SCORE,
                        maxOverlayScore = OverlayReviewUiState.DEFAULT_MAX_OVERLAY_SCORE,
                        currentIndex = -1,
                        keptImageIds = emptySet(),
                        markedForTrashIds = emptySet(),
                        movedToTrashIds = emptySet(),
                        cleaningRequestedIds = emptySet(),
                        completedCleanReplaceIds = emptySet(),
                        skippedPreviewIds = emptySet(),
                        pendingBatchIds = emptySet(),
                        pendingDeleteIntentSender = null,
                        previewState = null
                    )
                }

                val selectedFolders = settingsRepository.scanFolders.first()
                if (selectedFolders.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isScanning = false,
                            requiresFolderSelection = true,
                            scanProgress = it.scanProgress.copy(phase = ScanPhase.ERROR),
                            error = "Select at least one folder from Home before starting watermark review."
                        )
                    }
                    return@launch
                }

                scanOverlayCandidatesUseCase(selectedFolders).collect { scanState ->
                    _uiState.update { current ->
                        val isComplete = scanState.progress.phase == ScanPhase.COMPLETE
                        val hasItems = scanState.items.isNotEmpty()

                        if (hasItems) {
                            val filteredItems = filterItemsByRange(
                                items = scanState.items,
                                minScore = current.minOverlayScore,
                                maxScore = current.maxOverlayScore
                            )
                            val currentItemId = current.currentItem?.image?.id
                            val newIndex = if (currentItemId != null) {
                                val idx = filteredItems.indexOfFirst { it.image.id == currentItemId }
                                if (idx >= 0) idx else nextUndecidedIndex(
                                    items = filteredItems,
                                    start = 0,
                                    kept = current.keptImageIds,
                                    marked = current.markedForTrashIds,
                                    moved = current.movedToTrashIds,
                                    cleaned = current.completedCleanReplaceIds,
                                    skipped = current.skippedPreviewIds
                                )
                            } else {
                                nextUndecidedIndex(
                                    items = filteredItems,
                                    start = 0,
                                    kept = current.keptImageIds,
                                    marked = current.markedForTrashIds,
                                    moved = current.movedToTrashIds,
                                    cleaned = current.completedCleanReplaceIds,
                                    skipped = current.skippedPreviewIds
                                )
                            }

                            current.copy(
                                isScanning = !isComplete,
                                scanProgress = scanState.progress,
                                overlayItems = scanState.items,
                                currentIndex = newIndex,
                                error = null
                            )
                        } else {
                            current.copy(
                                isScanning = !isComplete,
                                scanProgress = scanState.progress
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        error = e.message ?: "Failed to review image overlays."
                    )
                }
            }
        }
    }

    fun keepCurrent() {
        val state = _uiState.value
        if (state.isPaused || state.hasReadyPreview) return
        val current = state.currentItem ?: return

        _uiState.update {
            val kept = it.keptImageIds + current.image.id
            it.copy(
                keptImageIds = kept,
                currentIndex = nextUndecidedIndex(
                    items = it.filteredOverlayItems,
                    start = (it.currentIndex + 1).coerceAtLeast(0),
                    kept = kept,
                    marked = it.markedForTrashIds,
                    moved = it.movedToTrashIds,
                    cleaned = it.completedCleanReplaceIds,
                    skipped = it.skippedPreviewIds
                )
            )
        }
    }

    fun markCurrentForTrash() {
        val state = _uiState.value
        if (state.isPaused || state.hasReadyPreview) return
        val current = state.currentItem ?: return

        _uiState.update {
            val marked = it.markedForTrashIds + current.image.id
            it.copy(
                markedForTrashIds = marked,
                currentIndex = nextUndecidedIndex(
                    items = it.filteredOverlayItems,
                    start = (it.currentIndex + 1).coerceAtLeast(0),
                    kept = it.keptImageIds,
                    marked = marked,
                    moved = it.movedToTrashIds,
                    cleaned = it.completedCleanReplaceIds,
                    skipped = it.skippedPreviewIds
                )
            )
        }
    }

    fun generatePreviewForCurrent() {
        val state = _uiState.value
        if (state.isPaused || state.isGeneratingPreview) return
        val current = state.currentItem ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGeneratingPreview = true,
                    error = null,
                    cleaningRequestedIds = it.cleaningRequestedIds + current.image.id
                )
            }

            generateOverlayPreviewUseCase(
                detection = current.detection,
                allowDownload = true
            ).onSuccess { preview ->
                _uiState.update {
                    it.copy(
                        isGeneratingPreview = false,
                        previewState = preview
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isGeneratingPreview = false,
                        error = error.message ?: "Failed to generate cleaned preview."
                    )
                }
            }
        }
    }

    fun keepCleanedPreview() {
        applyPreviewDecision(OverlayPreviewDecision.KEEP_CLEANED_REPLACE_ORIGINAL)
    }

    fun deleteAllFromPreview() {
        applyPreviewDecision(OverlayPreviewDecision.DELETE_ALL)
    }

    fun skipPreview() {
        applyPreviewDecision(OverlayPreviewDecision.SKIP_KEEP_ORIGINAL)
    }

    fun updateReviewScoreRange(minScore: Float, maxScore: Float) {
        _uiState.update { state ->
            val normalizedMin = minScore.coerceIn(
                OverlayReviewUiState.DEFAULT_MIN_OVERLAY_SCORE,
                OverlayReviewUiState.DEFAULT_MAX_OVERLAY_SCORE
            )
            val normalizedMax = maxScore.coerceIn(
                OverlayReviewUiState.DEFAULT_MIN_OVERLAY_SCORE,
                OverlayReviewUiState.DEFAULT_MAX_OVERLAY_SCORE
            )
            val rangeMin = minOf(normalizedMin, normalizedMax)
            val rangeMax = maxOf(normalizedMin, normalizedMax)
            val filteredItems = filterItemsByRange(
                items = state.overlayItems,
                minScore = rangeMin,
                maxScore = rangeMax
            )
            val currentId = state.currentItem?.image?.id
            state.copy(
                minOverlayScore = rangeMin,
                maxOverlayScore = rangeMax,
                currentIndex = resolveCurrentIndexAfterRangeChange(
                    items = filteredItems,
                    currentId = currentId,
                    kept = state.keptImageIds,
                    marked = state.markedForTrashIds,
                    moved = state.movedToTrashIds,
                    cleaned = state.completedCleanReplaceIds,
                    skipped = state.skippedPreviewIds
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

    private fun applyPreviewDecision(decision: OverlayPreviewDecision) {
        val state = _uiState.value
        val preview = state.previewState ?: return
        val current = state.currentItem ?: return

        viewModelScope.launch {
            val currentImage = imageRepository.getImageById(current.image.id)
            if (currentImage == null) {
                applyOverlayPreviewDecisionUseCase.discardPreview(preview)
                _uiState.update {
                    it.copy(
                        previewState = null,
                        error = "The image changed or no longer exists. The temporary preview was discarded."
                    )
                }
                return@launch
            }

            applyOverlayPreviewDecisionUseCase(
                image = currentImage,
                preview = preview,
                decision = decision
            ).onSuccess {
                _uiState.update { currentState ->
                    when (decision) {
                        OverlayPreviewDecision.KEEP_CLEANED_REPLACE_ORIGINAL -> {
                            val cleaned = currentState.completedCleanReplaceIds + currentImage.id
                            currentState.copy(
                                previewState = null,
                                completedCleanReplaceIds = cleaned,
                                currentIndex = nextUndecidedIndex(
                                    items = currentState.filteredOverlayItems,
                                    start = (currentState.currentIndex + 1).coerceAtLeast(0),
                                    kept = currentState.keptImageIds,
                                    marked = currentState.markedForTrashIds,
                                    moved = currentState.movedToTrashIds,
                                    cleaned = cleaned,
                                    skipped = currentState.skippedPreviewIds
                                )
                            )
                        }

                        OverlayPreviewDecision.DELETE_ALL -> {
                            val moved = currentState.movedToTrashIds + currentImage.id
                            currentState.copy(
                                previewState = null,
                                movedToTrashIds = moved,
                                markedForTrashIds = currentState.markedForTrashIds - currentImage.id,
                                currentIndex = nextUndecidedIndex(
                                    items = currentState.filteredOverlayItems,
                                    start = (currentState.currentIndex + 1).coerceAtLeast(0),
                                    kept = currentState.keptImageIds,
                                    marked = currentState.markedForTrashIds - currentImage.id,
                                    moved = moved,
                                    cleaned = currentState.completedCleanReplaceIds,
                                    skipped = currentState.skippedPreviewIds
                                )
                            )
                        }

                        OverlayPreviewDecision.SKIP_KEEP_ORIGINAL -> {
                            val skipped = currentState.skippedPreviewIds + currentImage.id
                            currentState.copy(
                                previewState = null,
                                skippedPreviewIds = skipped,
                                currentIndex = nextUndecidedIndex(
                                    items = currentState.filteredOverlayItems,
                                    start = (currentState.currentIndex + 1).coerceAtLeast(0),
                                    kept = currentState.keptImageIds,
                                    marked = currentState.markedForTrashIds,
                                    moved = currentState.movedToTrashIds,
                                    cleaned = currentState.completedCleanReplaceIds,
                                    skipped = skipped
                                )
                            )
                        }
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(error = error.message ?: "Failed to apply preview decision.")
                }
            }
        }
    }

    private fun applyBatchToTrash(ids: Set<Long>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingBatch = true, error = null, pendingBatchIds = ids) }

            val state = _uiState.value
            val images = state.overlayItems
                .filter { it.image.id in ids }
                .map { it.image }

            if (images.isEmpty()) {
                _uiState.update { it.copy(isApplyingBatch = false, pendingBatchIds = emptySet()) }
                return@launch
            }

            val result = moveToTrashUseCase(images)
            result.onSuccess {
                val moved = resolveMovedIds(ids)
                _uiState.update { current ->
                    current.withMovedToTrash(moved).copy(
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
                        current.withMovedToTrash(moved).copy(
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
                        current.withMovedToTrash(moved).copy(
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

    private fun OverlayReviewUiState.withMovedToTrash(moved: Set<Long>): OverlayReviewUiState {
        if (moved.isEmpty()) return this
        val marked = markedForTrashIds - moved
        val movedSet = movedToTrashIds + moved
        return copy(
            markedForTrashIds = marked,
            movedToTrashIds = movedSet,
            currentIndex = nextUndecidedIndex(
                items = filteredOverlayItems,
                start = if (currentItem == null) filteredOverlayItems.size else currentIndex,
                kept = keptImageIds,
                marked = marked,
                moved = movedSet,
                cleaned = completedCleanReplaceIds,
                skipped = skippedPreviewIds
            )
        )
    }

    private fun nextUndecidedIndex(
        items: List<OverlayReviewItem>,
        start: Int,
        kept: Set<Long>,
        marked: Set<Long>,
        moved: Set<Long>,
        cleaned: Set<Long>,
        skipped: Set<Long>
    ): Int {
        for (index in start until items.size) {
            val id = items[index].image.id
            if (id !in kept &&
                id !in marked &&
                id !in moved &&
                id !in cleaned &&
                id !in skipped
            ) {
                return index
            }
        }
        return -1
    }

    private fun filterItemsByRange(
        items: List<OverlayReviewItem>,
        minScore: Float,
        maxScore: Float
    ): List<OverlayReviewItem> {
        return items
            .filter { it.rankScore in minScore..maxScore }
            .sortedByDescending { it.rankScore }
    }

    override fun onCleared() {
        scanJob?.cancel()
        discardPreviewFiles(_uiState.value.previewState)
        super.onCleared()
    }

    private fun discardPreviewFiles(preview: com.duplicatefinder.domain.model.CleaningPreview?) {
        preview ?: return
        deletePreviewUri(preview.previewUri)
        preview.maskUri?.let(::deletePreviewUri)
    }

    private fun deletePreviewUri(uri: Uri) {
        if (uri.scheme == null || uri.scheme == "file") {
            uri.path?.let { path ->
                File(path).takeIf { it.exists() }?.delete()
            }
        }
    }
}

internal fun resolveCurrentIndexAfterRangeChange(
    items: List<OverlayReviewItem>,
    currentId: Long?,
    kept: Set<Long>,
    marked: Set<Long>,
    moved: Set<Long>,
    cleaned: Set<Long>,
    skipped: Set<Long>
): Int {
    val firstUndecided = nextUndecidedIndexForRangeChange(
        items = items,
        start = 0,
        kept = kept,
        marked = marked,
        moved = moved,
        cleaned = cleaned,
        skipped = skipped
    )

    if (currentId == null ||
        currentId in kept ||
        currentId in marked ||
        currentId in moved ||
        currentId in cleaned ||
        currentId in skipped
    ) {
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
    items: List<OverlayReviewItem>,
    start: Int,
    kept: Set<Long>,
    marked: Set<Long>,
    moved: Set<Long>,
    cleaned: Set<Long>,
    skipped: Set<Long>
): Int {
    for (index in start until items.size) {
        val id = items[index].image.id
        if (id !in kept &&
            id !in marked &&
            id !in moved &&
            id !in cleaned &&
            id !in skipped
        ) {
            return index
        }
    }
    return -1
}
