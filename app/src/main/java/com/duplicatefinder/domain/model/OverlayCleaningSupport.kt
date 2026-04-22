package com.duplicatefinder.domain.model

import kotlin.math.max

private val SUPPORTED_OVERLAY_CLEANING_MIME_TYPES = listOf(
    "image/jpeg",
    "image/jpg",
    "image/png",
    "image/webp"
)

fun ImageItem.supportsOverlayCleaning(): Boolean {
    val normalizedMimeType = mimeType.trim().lowercase()
    return SUPPORTED_OVERLAY_CLEANING_MIME_TYPES.any { supported ->
        normalizedMimeType == supported
    }
}

fun ImageItem.overlayPreviewDecodeMaxDimension(minDimension: Int): Int {
    val sourceMaxDimension = max(width, height)
    return if (sourceMaxDimension > 0) {
        sourceMaxDimension.coerceAtLeast(minDimension)
    } else {
        minDimension
    }
}
