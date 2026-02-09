package com.duplicatefinder.presentation.screens.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duplicatefinder.domain.repository.TrashRepository
import com.duplicatefinder.domain.usecase.EmptyTrashUseCase
import com.duplicatefinder.domain.usecase.RestoreFromTrashUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val trashRepository: TrashRepository,
    private val restoreFromTrashUseCase: RestoreFromTrashUseCase,
    private val emptyTrashUseCase: EmptyTrashUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    init {
        loadTrashItems()
    }

    private fun loadTrashItems() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            trashRepository.getTrashItems().collect { items ->
                val totalSize = trashRepository.getTrashSize()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        trashItems = items,
                        totalSize = totalSize,
                        error = null
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

            try {
                val selectedIds = _uiState.value.selectedItems.toList()
                val itemsToDelete = _uiState.value.trashItems.filter { it.id in selectedIds }

                trashRepository.deletePermanently(itemsToDelete)

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        selectedItems = emptySet()
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        error = e.message
                    )
                }
            }
        }
    }

    fun restoreSelected() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, showRestoreDialog = false) }

            try {
                val selectedIds = _uiState.value.selectedItems.toList()
                val itemsToRestore = _uiState.value.trashItems.filter { it.id in selectedIds }

                restoreFromTrashUseCase(itemsToRestore)

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        selectedItems = emptySet()
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        error = e.message
                    )
                }
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, showEmptyTrashDialog = false) }

            try {
                emptyTrashUseCase()

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        selectedItems = emptySet()
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        error = e.message
                    )
                }
            }
        }
    }
}
