package com.duplicatefinder.domain.model

data class QualityScanState(
    val progress: ScanProgress,
    val items: List<ImageQualityItem>
)

