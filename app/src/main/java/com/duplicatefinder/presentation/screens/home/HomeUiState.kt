package com.duplicatefinder.presentation.screens.home

data class HomeUiState(
    val isLoading: Boolean = false,
    val totalImages: Int = 0,
    val duplicatesFound: Int = 0,
    val spaceRecoverable: Long = 0,
    val lastScanTimestamp: Long = 0,
    val hasPermission: Boolean = false,
    val error: String? = null
) {
    val hasScannedBefore: Boolean
        get() = lastScanTimestamp > 0
}
