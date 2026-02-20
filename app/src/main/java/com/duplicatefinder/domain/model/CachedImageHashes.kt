package com.duplicatefinder.domain.model

data class CachedImageHashes(
    val md5Hash: String,
    val perceptualHash: String?,
    val dateModified: Long,
    val size: Long
)
