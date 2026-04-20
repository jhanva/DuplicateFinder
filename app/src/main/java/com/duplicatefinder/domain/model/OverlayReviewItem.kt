package com.duplicatefinder.domain.model

data class OverlayReviewItem(
    val image: ImageItem,
    val detection: OverlayDetection,
    val rankScore: Float = detection.refinedScore
)
