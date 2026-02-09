package com.duplicatefinder.domain.model

data class DuplicateGroup(
    val id: String,
    val images: List<ImageItem>,
    val matchType: MatchType,
    val similarityScore: Float,
    val totalSize: Long,
    val potentialSavings: Long
) {
    val imageCount: Int
        get() = images.size

    val originalImage: ImageItem?
        get() = images.minByOrNull { it.dateModified }

    val duplicates: List<ImageItem>
        get() = images.drop(1)
}

enum class MatchType {
    EXACT,
    SIMILAR,
    BOTH
}
