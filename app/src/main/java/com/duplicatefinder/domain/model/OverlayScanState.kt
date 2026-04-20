package com.duplicatefinder.domain.model

data class OverlayScanState(
    val progress: ScanProgress,
    val items: List<OverlayReviewItem>,
    val candidateCount: Int,
    val scannedCount: Int
)
