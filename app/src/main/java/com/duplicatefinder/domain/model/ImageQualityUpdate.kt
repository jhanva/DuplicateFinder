package com.duplicatefinder.domain.model

data class ImageQualityUpdate(
    val image: ImageItem,
    val qualityScore: Float,
    val sharpness: Float,
    val detailDensity: Float,
    val blockiness: Float
)

