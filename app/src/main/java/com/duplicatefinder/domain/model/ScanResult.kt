package com.duplicatefinder.domain.model

data class ScanResult(
    val totalImages: Int,
    val duplicateGroups: List<DuplicateGroup>,
    val totalDuplicates: Int,
    val potentialSavings: Long,
    val scanDuration: Long,
    val timestamp: Long = System.currentTimeMillis()
) {
    val hasDuplicates: Boolean
        get() = duplicateGroups.isNotEmpty()

    companion object {
        fun empty() = ScanResult(
            totalImages = 0,
            duplicateGroups = emptyList(),
            totalDuplicates = 0,
            potentialSavings = 0,
            scanDuration = 0
        )
    }
}
