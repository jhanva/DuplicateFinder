package com.duplicatefinder.domain.repository

import kotlinx.coroutines.flow.Flow
import com.duplicatefinder.domain.model.ScanMode

interface SettingsRepository {
    val similarityThreshold: Flow<Float>
    val autoDeleteDays: Flow<Int>
    val isDarkMode: Flow<Boolean>
    val excludedFolders: Flow<Set<String>>
    val lastScanTimestamp: Flow<Long>
    val scanMode: Flow<ScanMode>

    suspend fun setSimilarityThreshold(threshold: Float)
    suspend fun setAutoDeleteDays(days: Int)
    suspend fun setDarkMode(enabled: Boolean)
    suspend fun setExcludedFolders(folders: Set<String>)
    suspend fun setLastScanTimestamp(timestamp: Long)
    suspend fun setScanMode(mode: ScanMode)
}
