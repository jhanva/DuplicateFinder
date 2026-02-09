package com.duplicatefinder.presentation.screens.trash

import com.duplicatefinder.domain.model.TrashItem

data class TrashUiState(
    val isLoading: Boolean = false,
    val trashItems: List<TrashItem> = emptyList(),
    val selectedItems: Set<Long> = emptySet(),
    val totalSize: Long = 0,
    val showDeleteDialog: Boolean = false,
    val showRestoreDialog: Boolean = false,
    val showEmptyTrashDialog: Boolean = false,
    val isProcessing: Boolean = false,
    val error: String? = null
) {
    val hasSelection: Boolean
        get() = selectedItems.isNotEmpty()

    val selectedCount: Int
        get() = selectedItems.size

    val isEmpty: Boolean
        get() = trashItems.isEmpty() && !isLoading
}
