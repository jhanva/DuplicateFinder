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
