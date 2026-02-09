package com.duplicatefinder.domain.model

import android.net.Uri

data class TrashItem(
    val id: Long,
    val originalUri: Uri,
    val originalPath: String,
    val trashPath: String,
    val name: String,
    val size: Long,
    val deletedAt: Long,
    val expiresAt: Long,
    val mimeType: String
) {
    val daysUntilExpiry: Int
        get() {
            val now = System.currentTimeMillis()
            val remaining = expiresAt - now
            return (remaining / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
        }

    val isExpired: Boolean
        get() = System.currentTimeMillis() >= expiresAt
}
