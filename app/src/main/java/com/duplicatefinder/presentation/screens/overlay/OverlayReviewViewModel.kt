package com.duplicatefinder.presentation.screens.overlay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duplicatefinder.domain.model.OverlayReviewItem
import com.duplicatefinder.domain.model.ScanPhase
import com.duplicatefinder.domain.model.UserConfirmationRequiredException
import com.duplicatefinder.domain.repository.ImageRepository
import com.duplicatefinder.domain.repository.SettingsRepository
import com.duplicatefinder.domain.usecase.MoveToTrashUseCase
import com.duplicatefinder.domain.usecase.ScanOverlayCandidatesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val samsungGalleryEditIntentFactory: SamsungGalleryEditIntentFactory,
    @Named("overlaySamsungGalleryLaunchFailedMessage")
    private val samsungGalleryLaunchFailedMessage: String,
    @Named("overlayNoGalleryChangesMessage")
    private val noGalleryChangesMessage: String,
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
                        scanProgress = com.duplicatefinder.domain.model.ScanProgress.initial(),
                        error = null,
                        requiresFolderSelection = false,
                        overlayItems = emptyList(),
                        minOverlayScore = OverlayReviewUiState.DEFAULT_MIN_OVERLAY_SCORE,
                        maxOverlayScore = OverlayReviewUiState.DEFAULT_MAX_OVERLAY_SCORE,
                        currentIndex = -1,
                        keptImageIds = emptySet(),
                        markedForTrashIds = emptySet(),
                        movedToTrashIds = emptySet(),
                        editedInGalleryIds = emptySet(),
                        pendingBatchIds = emptySet(),
                        pendingDeleteIntentSender = null,
                        externalEditSession = null,
                        pendingExternalEditRequest = null,
                        canOpenInSamsungGallery = false,
                        samsungGalleryHelperText = null
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
                        val nextState = if (hasItems) {
                            val filteredItems = filterItemsByRange(
                                items = scanState.items,
                                minScore = current.minOverlayScore,
                                maxScore = current.maxOverlayScore
                            )
                            val currentItemId = current.currentItem?.image?.id
                            val newIndex = if (currentItemId != null) {
                                val idx = filteredItems.indexOfFirst { it.image.id == currentItemId }
                                if (idx >= 0) {
                                    idx
                                } else {
                                    nextUndecidedIndex(
                                        items = filteredItems,
                                        start = 0,
                                        kept = current.keptImageIds,
                                        marked = current.markedForTrashIds,
                                        moved = current.movedToTrashIds,
                                        edited = current.editedInGalleryIds
                                    )
                                }
                            } else {
                                nextUndecidedIndex(
                                    items = filteredItems,
                                    start = 0,
                                    kept = current.keptImageIds,
                                    marked = current.markedForTrashIds,
                                    moved = current.movedToTrashIds,
                                    edited = current.editedInGalleryIds
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

                        withSamsungGalleryAvailability(nextState)
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
        if (state.isPaused) return
        val current = state.currentItem ?: return

        _uiState.update {
            val kept = it.keptImageIds + current.image.id
            withSamsungGalleryAvailability(
                it.copy(
                    keptImageIds = kept,
                    currentIndex = nextUndecidedIndex(
                        items = it.filteredOverlayItems,
                        start = (it.currentIndex + 1).coerceAtLeast(0),
                        kept = kept,
                        marked = it.markedForTrashIds,
                        moved = it.movedToTrashIds,
                        edited = it.editedInGalleryIds
                    )
                )
            )
        }
    }

    fun markCurrentForTrash() {
        val state = _uiState.value
        if (state.isPaused) return
        val current = state.currentItem ?: return

        _uiState.update {
            val marked = it.markedForTrashIds + current.image.id
            withSamsungGalleryAvailability(
                it.copy(
                    markedForTrashIds = marked,
                    currentIndex = nextUndecidedIndex(
                        items = it.filteredOverlayItems,
                        start = (it.currentIndex + 1).coerceAtLeast(0),
                        kept = it.keptImageIds,
                        marked = marked,
                        moved = it.movedToTrashIds,
                        edited = it.editedInGalleryIds
                    )
                )
            )
        }
    }

    fun openCurrentInSamsungGallery() {
        val state = _uiState.value
        if (state.isPaused || state.externalEditSession != null) return
        val current = state.currentItem ?: return
        val availability = samsungGalleryEditIntentFactory.availabilityFor(current.image)

        if (!availability.enabled) {
            _uiState.update {
                withSamsungGalleryAvailability(
                    it.copy(error = availability.helperText ?: "Samsung Gallery is not available.")
                )
            }
            return
        }

        _uiState.update {
            withSamsungGalleryAvailability(
                it.copy(
                    externalEditSession = OverlayExternalEditSession(
                        imageId = current.image.id,
                        originalSize = current.image.size,
                        originalDateModified = current.image.dateModified,
                        startedAt = System.currentTimeMillis()
                    ),
                    pendingExternalEditRequest = samsungGalleryEditIntentFactory.createLaunchRequest(current.image),
                    error = null
                )
            )
        }
    }

    fun onExternalEditorLaunchConsumed() {
        _uiState.update { it.copy(pendingExternalEditRequest = null) }
    }

    fun onExternalEditorLaunchFailed() {
        _uiState.update {
            withSamsungGalleryAvailability(
                it.copy(
                    externalEditSession = null,
                    pendingExternalEditRequest = null,
                    error = samsungGalleryLaunchFailedMessage
                )
            )
        }
    }

    fun onExternalEditorResult() {
        val session = _uiState.value.externalEditSession ?: return

        viewModelScope.launch {
            val latestImage = awaitExternalEditResult(session)
            _uiState.update { current ->
                if (latestImage == null) {
                    withSamsungGalleryAvailability(
                        current.copy(
                            externalEditSession = null,
                            pendingExternalEditRequest = null,
                            error = "The image changed or no longer exists."
                        )
                    )
                } else {
                    val updatedItems = current.overlayItems.replaceImage(latestImage)
                    val metadataChanged = latestImage.size != session.originalSize ||
                        latestImage.dateModified != session.originalDateModified

                    if (metadataChanged) {
                        val edited = current.editedInGalleryIds + latestImage.id
                        val filteredItems = filterItemsByRange(
                            items = updatedItems,
                            minScore = current.minOverlayScore,
                            maxScore = current.maxOverlayScore
                        )
                        withSamsungGalleryAvailability(
                            current.copy(
                                overlayItems = updatedItems,
                                editedInGalleryIds = edited,
                                externalEditSession = null,
                                pendingExternalEditRequest = null,
                                currentIndex = resolveCurrentIndexAfterRangeChange(
                                    items = filteredItems,
                                    currentId = current.currentItem?.image?.id,
                                    kept = current.keptImageIds,
                                    marked = current.markedForTrashIds,
                                    moved = current.movedToTrashIds,
                                    edited = edited
                                ),
                                error = null
                            )
                        )
                    } else {
                        withSamsungGalleryAvailability(
                            current.copy(
                                overlayItems = updatedItems,
                                externalEditSession = null,
                                pendingExternalEditRequest = null,
                                error = noGalleryChangesMessage
                            )
                        )
                    }
                }
            }
        }
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
            withSamsungGalleryAvailability(
                state.copy(
                    minOverlayScore = rangeMin,
                    maxOverlayScore = rangeMax,
                    currentIndex = resolveCurrentIndexAfterRangeChange(
                        items = filteredItems,
                        currentId = currentId,
                        kept = state.keptImageIds,
                        marked = state.markedForTrashIds,
                        moved = state.movedToTrashIds,
                        edited = state.editedInGalleryIds
                    )
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
                withSamsungGalleryAvailability(
                    it.copy(
                        isApplyingBatch = false,
                        pendingBatchIds = emptySet(),
                        error = "Storage permission was not granted. Batch move was canceled."
                    )
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
                    withSamsungGalleryAvailability(
                        current.withMovedToTrash(moved).copy(
                            isApplyingBatch = false,
                            pendingBatchIds = emptySet(),
                            error = if (moved.size < ids.size) {
                                "Only ${moved.size} of ${ids.size} images were moved to trash."
                            } else {
                                null
                            }
                        )
                    )
                }
            }

            result.onFailure { error ->
                val moved = resolveMovedIds(ids)
                if (error is UserConfirmationRequiredException) {
                    val pendingIds = ids - moved
                    _uiState.update { current ->
                        withSamsungGalleryAvailability(
                            current.withMovedToTrash(moved).copy(
                                isApplyingBatch = false,
                                pendingBatchIds = pendingIds,
                                pendingDeleteIntentSender = if (pendingIds.isNotEmpty()) {
                                    error.intentSender
                                } else {
                                    null
                                }
                            )
                        )
                    }
                } else {
                    _uiState.update { current ->
                        withSamsungGalleryAvailability(
                            current.withMovedToTrash(moved).copy(
                                isApplyingBatch = false,
                                pendingBatchIds = emptySet(),
                                error = error.message ?: "Failed to move images to trash."
                            )
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
                edited = editedInGalleryIds
            )
        )
    }

    private fun withSamsungGalleryAvailability(state: OverlayReviewUiState): OverlayReviewUiState {
        val availability = state.currentItem?.let { item ->
            samsungGalleryEditIntentFactory.availabilityFor(item.image)
        }
        return state.copy(
            canOpenInSamsungGallery = availability?.enabled == true,
            samsungGalleryHelperText = availability?.helperText
        )
    }

    private suspend fun awaitExternalEditResult(
        session: OverlayExternalEditSession
    ): com.duplicatefinder.domain.model.ImageItem? {
        var latestImage: com.duplicatefinder.domain.model.ImageItem? = null
        repeat(EXTERNAL_EDIT_REQUERY_ATTEMPTS) { attempt ->
            latestImage = imageRepository.getImageById(session.imageId)
            if (latestImage == null || hasExternalEditMetadataChange(session, latestImage!!)) {
                return latestImage
            }
            if (attempt < EXTERNAL_EDIT_REQUERY_ATTEMPTS - 1) {
                delay(EXTERNAL_EDIT_REQUERY_DELAY_MS)
            }
        }
        return latestImage
    }

    private fun hasExternalEditMetadataChange(
        session: OverlayExternalEditSession,
        latestImage: com.duplicatefinder.domain.model.ImageItem
    ): Boolean {
        return latestImage.size != session.originalSize ||
            latestImage.dateModified != session.originalDateModified
    }

    private fun nextUndecidedIndex(
        items: List<OverlayReviewItem>,
        start: Int,
        kept: Set<Long>,
        marked: Set<Long>,
        moved: Set<Long>,
        edited: Set<Long>
    ): Int {
        for (index in start until items.size) {
            val id = items[index].image.id
            if (id !in kept &&
                id !in marked &&
                id !in moved &&
                id !in edited
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
        super.onCleared()
    }
}

internal fun resolveCurrentIndexAfterRangeChange(
    items: List<OverlayReviewItem>,
    currentId: Long?,
    kept: Set<Long>,
    marked: Set<Long>,
    moved: Set<Long>,
    edited: Set<Long>
): Int {
    val firstUndecided = nextUndecidedIndexForRangeChange(
        items = items,
        start = 0,
        kept = kept,
        marked = marked,
        moved = moved,
        edited = edited
    )

    if (currentId == null ||
        currentId in kept ||
        currentId in marked ||
        currentId in moved ||
        currentId in edited
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
    edited: Set<Long>
): Int {
    for (index in start until items.size) {
        val id = items[index].image.id
        if (id !in kept &&
            id !in marked &&
            id !in moved &&
            id !in edited
        ) {
            return index
        }
    }
    return -1
}

private fun List<OverlayReviewItem>.replaceImage(latestImage: com.duplicatefinder.domain.model.ImageItem): List<OverlayReviewItem> {
    return map { item ->
        if (item.image.id == latestImage.id) {
            item.copy(image = latestImage)
        } else {
            item
        }
    }
}

private const val EXTERNAL_EDIT_REQUERY_ATTEMPTS = 5
private const val EXTERNAL_EDIT_REQUERY_DELAY_MS = 400L
