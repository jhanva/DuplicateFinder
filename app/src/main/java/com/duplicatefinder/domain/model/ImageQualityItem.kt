package com.duplicatefinder.domain.model

data class ImageQualityItem(
    val image: ImageItem,
    val qualityScore: Float,
    val sharpness: Float,
    val detailDensity: Float,
    val blockiness: Float
)

