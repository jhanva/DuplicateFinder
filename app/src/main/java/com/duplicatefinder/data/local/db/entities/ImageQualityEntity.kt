package com.duplicatefinder.data.local.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "image_quality_scores")
data class ImageQualityEntity(
    @PrimaryKey
    val imageId: Long,
    val path: String,
    val qualityScore: Float,
    val sharpness: Float,
    val detailDensity: Float,
    val blockiness: Float,
    val dateModified: Long,
    val size: Long,
    val createdAt: Long = System.currentTimeMillis()
)

