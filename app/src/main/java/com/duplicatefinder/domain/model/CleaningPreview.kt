package com.duplicatefinder.domain.model

import android.net.Uri

data class CleaningPreview(
    val sourceImage: ImageItem,
    val previewUri: Uri,
    val maskUri: Uri? = null,
    val modelVersion: String,
    val generationTimeMs: Long,
    val status: PreviewStatus
)

enum class PreviewStatus {
    GENERATING,
    READY,
    FAILED,
    DISCARDED
}

enum class OverlayPreviewDecision {
    KEEP_CLEANED_REPLACE_ORIGINAL,
    DELETE_ALL,
    SKIP_KEEP_ORIGINAL
}
