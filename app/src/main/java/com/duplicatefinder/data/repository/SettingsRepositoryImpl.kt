package com.duplicatefinder.data.repository

import com.duplicatefinder.data.local.datastore.SettingsDataStore
import com.duplicatefinder.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : SettingsRepository {

    override val similarityThreshold: Flow<Float>
        get() = settingsDataStore.similarityThreshold

    override val autoDeleteDays: Flow<Int>
        get() = settingsDataStore.autoDeleteDays

    override val isDarkMode: Flow<Boolean>
        get() = settingsDataStore.isDarkMode

    override val excludedFolders: Flow<Set<String>>
        get() = settingsDataStore.excludedFolders

    override val lastScanTimestamp: Flow<Long>
        get() = settingsDataStore.lastScanTimestamp

    override suspend fun setSimilarityThreshold(threshold: Float) {
        settingsDataStore.setSimilarityThreshold(threshold)
    }

    override suspend fun setAutoDeleteDays(days: Int) {
        settingsDataStore.setAutoDeleteDays(days)
    }

    override suspend fun setDarkMode(enabled: Boolean) {
        settingsDataStore.setDarkMode(enabled)
    }

    override suspend fun setExcludedFolders(folders: Set<String>) {
        settingsDataStore.setExcludedFolders(folders)
    }

    override suspend fun setLastScanTimestamp(timestamp: Long) {
        settingsDataStore.setLastScanTimestamp(timestamp)
    }
}
