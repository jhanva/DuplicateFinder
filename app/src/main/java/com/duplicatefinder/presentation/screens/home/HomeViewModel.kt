package com.duplicatefinder.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duplicatefinder.domain.model.ScanMode
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

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val lastScan = settingsRepository.lastScanTimestamp.first()
                val lastDuplicateCount = settingsRepository.lastDuplicateCount.first()
                val lastPotentialSavings = settingsRepository.lastPotentialSavings.first()
                val scanMode = settingsRepository.scanMode.first()
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
                val imageCount = if (validSelection.isEmpty()) {
                    0
                } else {
                    imageRepository.getImageCount(validSelection)
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        totalImages = imageCount,
                        duplicatesFound = lastDuplicateCount,
                        spaceRecoverable = lastPotentialSavings,
                        lastScanTimestamp = lastScan,
                        availableFolders = folders,
                        selectedFolders = validSelection,
                        scanMode = scanMode,
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
            try {
                settingsRepository.setScanFolders(folders)
                val imageCount = if (folders.isEmpty()) {
                    0
                } else {
                    imageRepository.getImageCount(folders)
                }
                _uiState.update {
                    it.copy(
                        selectedFolders = folders,
                        totalImages = imageCount
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasPermission = granted) }
        if (granted) {
            loadData()
        }
    }

    fun setExactOnly(enabled: Boolean) {
        viewModelScope.launch {
            val mode = if (enabled) ScanMode.EXACT else ScanMode.EXACT_AND_SIMILAR
            settingsRepository.setScanMode(mode)
            _uiState.update { it.copy(scanMode = mode) }
        }
    }

    fun updateScanResults(duplicatesFound: Int, spaceRecoverable: Long) {
        val timestamp = System.currentTimeMillis() / 1000
        viewModelScope.launch {
            settingsRepository.setLastScanSummary(
                timestamp = timestamp,
                duplicateCount = duplicatesFound,
                potentialSavings = spaceRecoverable
            )
            _uiState.update {
                it.copy(
                    duplicatesFound = duplicatesFound,
                    spaceRecoverable = spaceRecoverable,
                    lastScanTimestamp = timestamp
                )
            }
        }
    }
}
