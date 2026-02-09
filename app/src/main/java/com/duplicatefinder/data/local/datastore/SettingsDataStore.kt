package com.duplicatefinder.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val SIMILARITY_THRESHOLD = floatPreferencesKey("similarity_threshold")
        val AUTO_DELETE_DAYS = intPreferencesKey("auto_delete_days")
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val EXCLUDED_FOLDERS = stringSetPreferencesKey("excluded_folders")
        val LAST_SCAN_TIMESTAMP = longPreferencesKey("last_scan_timestamp")
    }

    val similarityThreshold: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[Keys.SIMILARITY_THRESHOLD] ?: DEFAULT_SIMILARITY_THRESHOLD
    }

    val autoDeleteDays: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[Keys.AUTO_DELETE_DAYS] ?: DEFAULT_AUTO_DELETE_DAYS
    }

    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.IS_DARK_MODE] ?: false
    }

    val excludedFolders: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[Keys.EXCLUDED_FOLDERS] ?: emptySet()
    }

    val lastScanTimestamp: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[Keys.LAST_SCAN_TIMESTAMP] ?: 0L
    }

    suspend fun setSimilarityThreshold(threshold: Float) {
        context.dataStore.edit { preferences ->
            preferences[Keys.SIMILARITY_THRESHOLD] = threshold.coerceIn(0f, 1f)
        }
    }

    suspend fun setAutoDeleteDays(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.AUTO_DELETE_DAYS] = days.coerceAtLeast(1)
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.IS_DARK_MODE] = enabled
        }
    }

    suspend fun setExcludedFolders(folders: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[Keys.EXCLUDED_FOLDERS] = folders
        }
    }

    suspend fun setLastScanTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[Keys.LAST_SCAN_TIMESTAMP] = timestamp
        }
    }

    companion object {
        const val DEFAULT_SIMILARITY_THRESHOLD = 0.9f
        const val DEFAULT_AUTO_DELETE_DAYS = 30
    }
}
