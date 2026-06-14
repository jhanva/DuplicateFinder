package com.duplicatefinder.domain.model

data class ScanProgress(
    val phase: ScanPhase,
    val current: Int,
    val total: Int,
    val currentFile: String? = null
) {
    val progress: Float
        get() = if (total > 0) current.toFloat() / total else 0f

    val isComplete: Boolean
        get() = phase == ScanPhase.COMPLETE

    /**
     * Whether this phase reports a measurable, advancing percentage. Phases that
     * run as a single non-incremental operation (e.g. [ScanPhase.COMPARING]) or
     * that have no known total should render an indeterminate indicator so the UI
     * clearly conveys ongoing work instead of a bar frozen at 0%.
     */
    val isIndeterminate: Boolean
        get() = when (phase) {
            ScanPhase.IDLE, ScanPhase.COMPLETE, ScanPhase.ERROR -> false
            ScanPhase.COMPARING -> true
            else -> total <= 0
        }

    companion object {
        fun initial() = ScanProgress(
            phase = ScanPhase.IDLE,
            current = 0,
            total = 0
        )
    }
}

enum class ScanPhase {
    IDLE,
    LOADING,
    INDEXING,
    HASHING,
    ANALYZING,
    COMPARING,
    COMPLETE,
    ERROR
}
