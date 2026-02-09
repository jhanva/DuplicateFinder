package com.duplicatefinder.presentation.screens.duplicates

import com.duplicatefinder.domain.model.DuplicateGroup
import com.duplicatefinder.domain.model.FilterCriteria

data class DuplicatesUiState(
    val isLoading: Boolean = false,
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val filteredGroups: List<DuplicateGroup> = emptyList(),
    val selectedImages: Set<Long> = emptySet(),
    val filterCriteria: FilterCriteria = FilterCriteria.empty(),
    val availableFolders: List<String> = emptyList(),
    val showFilterSheet: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val isDeleting: Boolean = false,
    val error: String? = null
) {
    val hasSelection: Boolean
        get() = selectedImages.isNotEmpty()

    val selectedCount: Int
        get() = selectedImages.size

    val totalPotentialSavings: Long
        get() = filteredGroups.sumOf { it.potentialSavings }

    val isEmpty: Boolean
        get() = filteredGroups.isEmpty() && !isLoading
}
