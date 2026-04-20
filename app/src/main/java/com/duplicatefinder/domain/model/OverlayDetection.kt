package com.duplicatefinder.domain.model

data class OverlayDetection(
    val image: ImageItem,
    val preliminaryScore: Float,
    val refinedScore: Float,
    val overlayCoverageRatio: Float,
    val maskBounds: List<OverlayRegion>,
    val maskConfidence: Float,
    val overlayKinds: Set<OverlayKind>,
    val stage: DetectionStage,
    val modelVersion: String
)

data class OverlayRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float,
    val kind: OverlayKind
)

enum class OverlayKind {
    TEXT,
    HANDLE,
    SIGNATURE,
    DATE_STAMP,
    LOGO,
    CAPTION,
    STICKER_TEXT,
    UNKNOWN
}

enum class DetectionStage {
    STAGE_1_CANDIDATE,
    STAGE_2_REFINED
}
