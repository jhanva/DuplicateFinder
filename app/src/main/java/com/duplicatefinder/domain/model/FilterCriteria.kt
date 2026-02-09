package com.duplicatefinder.domain.model

data class FilterCriteria(
    val folders: List<String> = emptyList(),
    val dateRange: DateRange? = null,
    val minSize: Long? = null,
    val maxSize: Long? = null,
    val mimeTypes: List<String> = emptyList(),
    val matchTypes: List<MatchType> = listOf(MatchType.EXACT, MatchType.SIMILAR)
) {
    val hasActiveFilters: Boolean
        get() = folders.isNotEmpty() ||
                dateRange != null ||
                minSize != null ||
                maxSize != null ||
                mimeTypes.isNotEmpty() ||
                matchTypes.size != 2

    companion object {
        fun empty() = FilterCriteria()
    }
}

data class DateRange(
    val startDate: Long,
    val endDate: Long
)
