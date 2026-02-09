package com.duplicatefinder.data.local.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trash_items")
data class TrashEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalUri: String,
    val originalPath: String,
    val trashPath: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val deletedAt: Long,
    val expiresAt: Long
)
