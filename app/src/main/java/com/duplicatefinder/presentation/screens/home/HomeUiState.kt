package com.duplicatefinder.presentation.screens.home

import com.duplicatefinder.domain.model.ScanMode

data class HomeUiState(
    val isLoading: Boolean = false,
    val totalImages: Int = 0,
    val duplicatesFound: Int = 0,
    val spaceRecoverable: Long = 0,
    val lastScanTimestamp: Long = 0,
    val hasPermission: Boolean = false,
    val availableFolders: List<String> = emptyList(),
    val selectedFolders: Set<String> = emptySet(),
    val scanMode: ScanMode = ScanMode.EXACT_AND_SIMILAR,
    val error: String? = null
) {
    val hasScannedBefore: Boolean
        get() = lastScanTimestamp > 0

    val isExactOnly: Boolean
        get() = scanMode == ScanMode.EXACT
}
