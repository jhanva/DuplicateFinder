package com.duplicatefinder.domain.model

data class CachedImageQuality(
    val qualityScore: Float,
    val sharpness: Float,
    val detailDensity: Float,
    val blockiness: Float,
    val dateModified: Long,
    val size: Long
)

