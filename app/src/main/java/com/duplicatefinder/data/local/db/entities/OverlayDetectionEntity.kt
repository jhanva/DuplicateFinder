package com.duplicatefinder.data.local.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "overlay_detections")
data class OverlayDetectionEntity(
    @PrimaryKey
    val imageId: Long,
    val path: String,
    val preliminaryScore: Float,
    val refinedScore: Float,
    val overlayCoverageRatio: Float,
    val maskConfidence: Float,
    val overlayKinds: String,
    val regionsJson: String,
    val dateModified: Long,
    val size: Long,
    val modelVersion: String,
    val createdAt: Long = System.currentTimeMillis()
)
