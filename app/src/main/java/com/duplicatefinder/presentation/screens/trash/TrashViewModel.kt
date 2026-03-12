package com.duplicatefinder.presentation.screens.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duplicatefinder.domain.repository.SettingsRepository
import com.duplicatefinder.domain.repository.TrashRepository
import com.duplicatefinder.domain.usecase.EmptyTrashUseCase
import com.duplicatefinder.domain.usecase.RestoreFromTrashUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val trashRepository: TrashRepository,
    private val settingsRepository: SettingsRepository,
    private val restoreFromTrashUseCase: RestoreFromTrashUseCase,
    private val emptyTrashUseCase: EmptyTrashUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
        loadTrashItems()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.autoDeleteDays.collect { autoDeleteDays ->
                _uiState.update { it.copy(autoDeleteDays = autoDeleteDays) }
            }
        }
    }

    private fun loadTrashItems() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            trashRepository.getTrashItems().collect { items ->
                val totalSize = trashRepository.getTrashSize()
                val validSelectedItems = _uiState.value.selectedItems.intersect(items.map { it.id }.toSet())
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        trashItems = items,
                        selectedItems = validSelectedItems,
                        totalSize = totalSize,
                        error = it.error
                    )
                }
            }
        }
    }

    fun toggleItemSelection(itemId: Long) {
        _uiState.update {
            val newSelection = if (itemId in it.selectedItems) {
                it.selectedItems - itemId
            } else {
                it.selectedItems + itemId
            }
            it.copy(selectedItems = newSelection)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            val allIds = state.trashItems.map { it.id }.toSet()
            state.copy(selectedItems = allIds)
        }
    }

    fun deselectAll() {
        _uiState.update { it.copy(selectedItems = emptySet()) }
    }

    fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun hideDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun showRestoreDialog() {
        _uiState.update { it.copy(showRestoreDialog = true) }
    }

    fun hideRestoreDialog() {
        _uiState.update { it.copy(showRestoreDialog = false) }
    }

    fun showEmptyTrashDialog() {
        _uiState.update { it.copy(showEmptyTrashDialog = true) }
    }

    fun hideEmptyTrashDialog() {
        _uiState.update { it.copy(showEmptyTrashDialog = false) }
    }

    fun deleteSelectedPermanently() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, showDeleteDialog = false) }

            val selectedIds = _uiState.value.selectedItems.toList()
            val itemsToDelete = _uiState.value.trashItems.filter { it.id in selectedIds }
            val result = trashRepository.deletePermanently(itemsToDelete)

            handleActionResult(
                result = result,
                requestedCount = itemsToDelete.size,
                partialFailureMessage = { deletedCount, requestedCount ->
                    "Only $deletedCount of $requestedCount items were deleted permanently."
                }
            )
        }
    }

    fun restoreSelected() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, showRestoreDialog = false) }

            val selectedIds = _uiState.value.selectedItems.toList()
            val itemsToRestore = _uiState.value.trashItems.filter { it.id in selectedIds }
            val result = restoreFromTrashUseCase(itemsToRestore)

            handleActionResult(
                result = result,
                requestedCount = itemsToRestore.size,
                partialFailureMessage = { restoredCount, requestedCount ->
                    "Only $restoredCount of $requestedCount items were restored."
                }
            )
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, showEmptyTrashDialog = false) }

            val requestedCount = _uiState.value.trashItems.size
            val result = emptyTrashUseCase()

            handleActionResult(
                result = result,
                requestedCount = requestedCount,
                partialFailureMessage = { deletedCount, totalCount ->
                    "Only $deletedCount of $totalCount trash items were deleted."
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun handleActionResult(
        result: Result<Int>,
        requestedCount: Int,
        partialFailureMessage: (processedCount: Int, requestedCount: Int) -> String
    ) {
        result
            .onSuccess { processedCount ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        selectedItems = emptySet(),
                        error = if (processedCount < requestedCount) {
                            partialFailureMessage(processedCount, requestedCount)
                        } else {
                            null
                        }
                    )
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        error = error.message ?: "Trash operation failed."
                    )
                }
            }
    }
}
