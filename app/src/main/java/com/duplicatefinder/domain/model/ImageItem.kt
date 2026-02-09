package com.duplicatefinder.domain.model

import android.net.Uri

data class ImageItem(
    val id: Long,
    val uri: Uri,
    val path: String,
    val name: String,
    val size: Long,
    val dateModified: Long,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val md5Hash: String? = null,
    val perceptualHash: String? = null,
    val folderName: String = ""
) {
    val isHashed: Boolean
        get() = md5Hash != null

    val hasPerceptualHash: Boolean
        get() = perceptualHash != null
}
