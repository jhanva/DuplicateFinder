package com.duplicatefinder.presentation.screens.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duplicatefinder.domain.model.DuplicateGroup
import com.duplicatefinder.domain.model.FilterCriteria
import com.duplicatefinder.domain.model.ScanPhase
import com.duplicatefinder.domain.model.UserConfirmationRequiredException
import com.duplicatefinder.domain.repository.ScanResultStore
import com.duplicatefinder.domain.repository.SettingsRepository
import com.duplicatefinder.domain.usecase.FilterImagesUseCase
import com.duplicatefinder.domain.usecase.FindDuplicatesUseCase
import com.duplicatefinder.domain.usecase.MoveToTrashUseCase
import com.duplicatefinder.domain.usecase.ScanImagesUseCase
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
class DuplicatesViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scanImagesUseCase: ScanImagesUseCase,
    private val findDuplicatesUseCase: FindDuplicatesUseCase,
    private val filterImagesUseCase: FilterImagesUseCase,
    private val moveToTrashUseCase: MoveToTrashUseCase,
    private val scanResultStore: ScanResultStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuplicatesUiState())
    val uiState: StateFlow<DuplicatesUiState> = _uiState.asStateFlow()
    private var pendingDeleteImageIds: Set<Long> = emptySet()
    private var loadJob: Job? = null

    init {
        loadDuplicates(forceRescan = false)
    }

    private fun loadDuplicates(forceRescan: Boolean) {
        if (loadJob?.isActive == true) return

        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, scanProgress = null) }

            try {
                val selectedFolders = settingsRepository.scanFolders.first()
                if (selectedFolders.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            duplicateGroups = emptyList(),
                            filteredGroups = emptyList(),
                            requiresFolderSelection = true,
                            error = null
                        )
                    }
                    return@launch
                }

                val scanMode = settingsRepository.scanMode.first()

                if (!forceRescan) {
                    val snapshot = scanResultStore.get(selectedFolders, scanMode)
                    if (snapshot != null) {
                        publishGroups(snapshot.groups)
                        return@launch
                    }
                }

                scanImagesUseCase(scanMode, selectedFolders).collect { (progress, images) ->
                    _uiState.update { it.copy(scanProgress = progress) }

                    if (progress.phase == ScanPhase.COMPLETE) {
                        _uiState.update {
                            it.copy(
                                scanProgress = progress.copy(phase = ScanPhase.COMPARING)
                            )
                        }
                        val duplicates = if (images.isEmpty()) {
                            emptyList()
                        } else {
                            findDuplicatesUseCase(images, scanMode)
                        }

                        scanResultStore.save(duplicates, selectedFolders, scanMode)
                        settingsRepository.setLastScanSummary(
                            timestamp = System.currentTimeMillis() / 1000,
                            duplicateCount = duplicates.sumOf { it.imageCount - 1 },
                            potentialSavings = duplicates.sumOf { it.potentialSavings }
                        )

                        publishGroups(duplicates)
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        scanProgress = null,
                        requiresFolderSelection = false,
                        error = e.message
                    )
                }
            }
        }
    }

    private fun publishGroups(duplicates: List<DuplicateGroup>) {
        _uiState.update {
            it.copy(
                isLoading = false,
                scanProgress = null,
                duplicateGroups = duplicates,
                filteredGroups = duplicates,
                requiresFolderSelection = false,
                error = null
            )
        }
    }

    fun toggleImageSelection(imageId: Long) {
        _uiState.update {
            val newSelection = if (imageId in it.selectedImages) {
                it.selectedImages - imageId
            } else {
                it.selectedImages + imageId
            }
            it.copy(selectedImages = newSelection)
        }
    }

    fun selectAllDuplicates() {
        _uiState.update { state ->
            val allDuplicateIds = state.filteredGroups
                .flatMap { group -> group.duplicates.map { it.id } }
                .toSet()
            state.copy(selectedImages = allDuplicateIds)
        }
    }

    fun deselectAll() {
        _uiState.update { it.copy(selectedImages = emptySet()) }
    }

    fun showFilterSheet() {
        _uiState.update { it.copy(showFilterSheet = true) }
    }

    fun hideFilterSheet() {
        _uiState.update { it.copy(showFilterSheet = false) }
    }

    fun applyFilter(criteria: FilterCriteria) {
        viewModelScope.launch {
            _uiState.update { it.copy(showFilterSheet = false, filterCriteria = criteria) }

            val filteredGroups = filterImagesUseCase(
                _uiState.value.duplicateGroups,
                criteria
            )

            _uiState.update { it.copy(filteredGroups = filteredGroups) }
        }
    }

    fun resetFilter() {
        _uiState.update {
            it.copy(
                filterCriteria = FilterCriteria.empty(),
                filteredGroups = it.duplicateGroups,
                showFilterSheet = false
            )
        }
    }

    fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun hideDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun deleteSelectedImages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, showDeleteDialog = false) }

            try {
                val selectedIds = _uiState.value.selectedImages.toList()
                val imagesToDelete = _uiState.value.duplicateGroups
                    .flatMap { it.images }
                    .filter { it.id in selectedIds }
                pendingDeleteImageIds = selectedIds.toSet()

                val result = moveToTrashUseCase(imagesToDelete)

                result.onSuccess { deletedCount ->
                    if (deletedCount == imagesToDelete.size) {
                        pendingDeleteImageIds = emptySet()
                        _uiState.update { state ->
                            val updatedGroups = state.duplicateGroups.mapNotNull { group ->
                                val remainingImages = group.images.filterNot { it.id in selectedIds }
                                if (remainingImages.size >= 2) {
                                    group.copy(
                                        images = remainingImages,
                                        totalSize = remainingImages.sumOf { it.size },
                                        potentialSavings = remainingImages.drop(1).sumOf { it.size }
                                    )
                                } else null
                            }

                            scanResultStore.updateGroups(updatedGroups)

                            val filteredUpdated = filterImagesUseCase.invoke(
                                updatedGroups,
                                state.filterCriteria
                            )

                            state.copy(
                                isDeleting = false,
                                selectedImages = emptySet(),
                                duplicateGroups = updatedGroups,
                                filteredGroups = filteredUpdated
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                error = "Only $deletedCount of ${imagesToDelete.size} images were moved to trash.",
                                isDeleting = false,
                                selectedImages = emptySet()
                            )
                        }
                        loadDuplicates(forceRescan = true)
                    }
                }

                result.onFailure { e ->
                    if (e is UserConfirmationRequiredException) {
                        _uiState.update {
                            it.copy(
                                isDeleting = false,
                                pendingDeleteIntentSender = e.intentSender
                            )
                        }
                        return@onFailure
                    }
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            error = e.message
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        error = e.message
                    )
                }
            }
        }
    }

    fun onDeleteConfirmationResult(granted: Boolean) {
        _uiState.update { it.copy(pendingDeleteIntentSender = null) }
        if (!granted) {
            _uiState.update { it.copy(error = "Delete permission was not granted.") }
            return
        }

        if (pendingDeleteImageIds.isEmpty()) return
        deleteSelectedImages()
    }

    fun refresh() {
        loadDuplicates(forceRescan = true)
    }
}
