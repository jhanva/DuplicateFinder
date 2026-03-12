package com.duplicatefinder.domain.repository

import kotlinx.coroutines.flow.Flow
import com.duplicatefinder.domain.model.ScanMode

interface SettingsRepository {
    val similarityThreshold: Flow<Float>
    val autoDeleteDays: Flow<Int>
    val isDarkMode: Flow<Boolean>
    val excludedFolders: Flow<Set<String>>
    val scanFolders: Flow<Set<String>>
    val lastScanTimestamp: Flow<Long>
    val lastDuplicateCount: Flow<Int>
    val lastPotentialSavings: Flow<Long>
    val scanMode: Flow<ScanMode>

    suspend fun setSimilarityThreshold(threshold: Float)
    suspend fun setAutoDeleteDays(days: Int)
    suspend fun setDarkMode(enabled: Boolean)
    suspend fun setExcludedFolders(folders: Set<String>)
    suspend fun setScanFolders(folders: Set<String>)
    suspend fun setLastScanTimestamp(timestamp: Long)
    suspend fun setLastScanSummary(timestamp: Long, duplicateCount: Int, potentialSavings: Long)
    suspend fun setScanMode(mode: ScanMode)
}
