package com.duplicatefinder.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val similarityThreshold: Flow<Float>
    val autoDeleteDays: Flow<Int>
    val isDarkMode: Flow<Boolean>
    val excludedFolders: Flow<Set<String>>
    val lastScanTimestamp: Flow<Long>

    suspend fun setSimilarityThreshold(threshold: Float)
    suspend fun setAutoDeleteDays(days: Int)
    suspend fun setDarkMode(enabled: Boolean)
    suspend fun setExcludedFolders(folders: Set<String>)
    suspend fun setLastScanTimestamp(timestamp: Long)
}
