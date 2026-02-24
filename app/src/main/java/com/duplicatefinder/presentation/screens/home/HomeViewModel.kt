package com.duplicatefinder.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duplicatefinder.domain.repository.ImageRepository
import com.duplicatefinder.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val imageRepository: ImageRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val imageCount = imageRepository.getImageCount()
                val lastScan = settingsRepository.lastScanTimestamp.first()
                val folders = imageRepository.getFolders()
                val selectedFolders = settingsRepository.scanFolders.first()
                val validSelection = if (folders.isEmpty()) {
                    emptySet()
                } else {
                    selectedFolders.intersect(folders.toSet())
                }
                if (validSelection != selectedFolders) {
                    settingsRepository.setScanFolders(validSelection)
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        totalImages = imageCount,
                        lastScanTimestamp = lastScan,
                        availableFolders = folders,
                        selectedFolders = validSelection,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }

    fun setSelectedFolders(folders: Set<String>) {
        viewModelScope.launch {
            settingsRepository.setScanFolders(folders)
            _uiState.update { it.copy(selectedFolders = folders) }
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasPermission = granted) }
        if (granted) {
            loadData()
        }
    }

    fun updateScanResults(duplicatesFound: Int, spaceRecoverable: Long) {
        _uiState.update {
            it.copy(
                duplicatesFound = duplicatesFound,
                spaceRecoverable = spaceRecoverable,
                lastScanTimestamp = System.currentTimeMillis()
            )
        }
    }
}
