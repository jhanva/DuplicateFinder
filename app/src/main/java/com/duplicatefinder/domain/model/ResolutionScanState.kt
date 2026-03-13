package com.duplicatefinder.domain.model

data class ResolutionScanState(
    val progress: ScanProgress,
    val items: List<ResolutionReviewItem>
)
