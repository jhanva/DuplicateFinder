package com.duplicatefinder.presentation.screens.scan

import com.duplicatefinder.domain.model.DuplicateGroup
import com.duplicatefinder.domain.model.ScanPhase
import com.duplicatefinder.domain.model.ScanProgress

data class ScanUiState(
    val scanProgress: ScanProgress = ScanProgress.initial(),
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val totalDuplicates: Int = 0,
    val potentialSavings: Long = 0,
    val error: String? = null
) {
    val isScanning: Boolean
        get() = scanProgress.phase in listOf(
            ScanPhase.LOADING,
            ScanPhase.HASHING,
            ScanPhase.COMPARING
        )

    val isComplete: Boolean
        get() = scanProgress.phase == ScanPhase.COMPLETE

    val hasError: Boolean
        get() = scanProgress.phase == ScanPhase.ERROR || error != null
}
