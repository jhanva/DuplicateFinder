package com.duplicatefinder.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duplicatefinder.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val similarityThreshold: StateFlow<Float> = settingsRepository.similarityThreshold
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.9f
        )

    val autoDeleteDays: StateFlow<Int> = settingsRepository.autoDeleteDays
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 30
        )

    val isDarkMode: StateFlow<Boolean> = settingsRepository.isDarkMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val excludedFolders: StateFlow<Set<String>> = settingsRepository.excludedFolders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    fun setSimilarityThreshold(threshold: Float) {
        viewModelScope.launch {
            settingsRepository.setSimilarityThreshold(threshold)
        }
    }

    fun setAutoDeleteDays(days: Int) {
        viewModelScope.launch {
            settingsRepository.setAutoDeleteDays(days)
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDarkMode(enabled)
        }
    }

    fun addExcludedFolder(folder: String) {
        viewModelScope.launch {
            val current = excludedFolders.value
            settingsRepository.setExcludedFolders(current + folder)
        }
    }

    fun removeExcludedFolder(folder: String) {
        viewModelScope.launch {
            val current = excludedFolders.value
            settingsRepository.setExcludedFolders(current - folder)
        }
    }
}
