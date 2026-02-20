package com.duplicatefinder.domain.model

data class ImageHashUpdate(
    val image: ImageItem,
    val md5Hash: String,
    val perceptualHash: String?
)
