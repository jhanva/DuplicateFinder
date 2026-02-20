package com.duplicatefinder.presentation.screens.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duplicatefinder.domain.model.FilterCriteria
import com.duplicatefinder.domain.model.ScanMode
import com.duplicatefinder.domain.repository.ImageRepository
import com.duplicatefinder.domain.repository.SettingsRepository
import com.duplicatefinder.domain.usecase.FilterImagesUseCase
import com.duplicatefinder.domain.usecase.FindDuplicatesUseCase
import com.duplicatefinder.domain.usecase.MoveToTrashUseCase
import com.duplicatefinder.domain.usecase.ScanImagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    private val imageRepository: ImageRepository,
    private val settingsRepository: SettingsRepository,
    private val scanImagesUseCase: ScanImagesUseCase,
    private val findDuplicatesUseCase: FindDuplicatesUseCase,
    private val filterImagesUseCase: FilterImagesUseCase,
    private val moveToTrashUseCase: MoveToTrashUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuplicatesUiState())
    val uiState: StateFlow<DuplicatesUiState> = _uiState.asStateFlow()

    init {
        loadDuplicates()
        loadFolders()
    }

    private fun loadDuplicates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val images = imageRepository.getAllImages()
                val cachedHashes = imageRepository.getCachedHashes(images.map { it.id })
                val scanMode = settingsRepository.scanMode.first()
                val computeSimilar = scanMode == ScanMode.EXACT_AND_SIMILAR
                val sizeCounts = images.groupingBy { it.size }.eachCount()

                val hashedImages = images.mapNotNull { image ->
                    val cachedHash = cachedHashes[image.id]
                    val cacheValid = cachedHash != null &&
                        cachedHash.dateModified == image.dateModified &&
                        cachedHash.size == image.size

                    val shouldComputeMd5 = (sizeCounts[image.size] ?: 0) > 1

                    val md5 = if (cacheValid) {
                        cachedHash!!.md5Hash
                    } else if (shouldComputeMd5) {
                        imageRepository.calculateMd5Hash(image)
                    } else {
                        null
                    }

                    val pHash = if (computeSimilar) {
                        if (cacheValid && cachedHash!!.perceptualHash != null) {
                            cachedHash.perceptualHash
                        } else {
                            imageRepository.calculatePerceptualHash(image)
                        }
                    } else {
                        null
                    }

                    if (md5 != null) {
                        val shouldSave = !cacheValid ||
                            (computeSimilar && cachedHash!!.perceptualHash == null && pHash != null)
                        if (shouldSave) {
                            imageRepository.saveHash(image, md5, pHash)
                        }
                    }

                    val result = image.copy(md5Hash = md5, perceptualHash = pHash)
                    if (md5 != null || (computeSimilar && pHash != null)) result else null
                }

                val duplicates = findDuplicatesUseCase(hashedImages, scanMode)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        duplicateGroups = duplicates,
                        filteredGroups = duplicates,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }

    private fun loadFolders() {
        viewModelScope.launch {
            try {
                val folders = imageRepository.getFolders()
                _uiState.update { it.copy(availableFolders = folders) }
            } catch (e: Exception) {
                // Ignore folder loading errors
            }
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

                val result = moveToTrashUseCase(imagesToDelete)

                result.onSuccess { deletedCount ->
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

                        val filteredUpdated = filterImagesUseCase.invoke(
                            updatedGroups,
                            state.filterCriteria
                        )

                        state.copy(
                            isDeleting = false,
                            selectedImages = emptySet(),
                            duplicateGroups = updatedGroups,
                            filteredGroups = updatedGroups
                        )
                    }
                }

                result.onFailure { e ->
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

    fun refresh() {
        loadDuplicates()
    }
}
