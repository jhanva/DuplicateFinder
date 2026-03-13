package com.duplicatefinder.domain.model

data class ResolutionReviewItem(
    val image: ImageItem,
    val pixelCount: Long,
    val megapixels: Float
) {
    companion object {
        fun from(image: ImageItem): ResolutionReviewItem? {
            if (image.width <= 0 || image.height <= 0) return null

            val pixelCount = image.width.toLong() * image.height.toLong()
            val megapixels = pixelCount / 1_000_000f

            return ResolutionReviewItem(
                image = image,
                pixelCount = pixelCount,
                megapixels = megapixels
            )
        }
    }
}
