package com.duplicatefinder.data.local.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "image_hashes")
data class ImageHashEntity(
    @PrimaryKey
    val imageId: Long,
    val path: String,
    val md5Hash: String,
    val perceptualHash: String?,
    val dateModified: Long,
    val size: Long,
    val createdAt: Long = System.currentTimeMillis()
)
