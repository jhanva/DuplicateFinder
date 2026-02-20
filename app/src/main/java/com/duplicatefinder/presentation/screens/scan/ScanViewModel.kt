package com.duplicatefinder.presentation.screens.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duplicatefinder.domain.model.ScanPhase
import com.duplicatefinder.domain.model.ScanProgress
import com.duplicatefinder.domain.repository.SettingsRepository
import com.duplicatefinder.domain.usecase.FindDuplicatesUseCase
import com.duplicatefinder.domain.usecase.ScanImagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanImagesUseCase: ScanImagesUseCase,
    private val findDuplicatesUseCase: FindDuplicatesUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        if (scanJob?.isActive == true) return

        scanJob = viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        scanProgress = ScanProgress(ScanPhase.LOADING, 0, 0),
                        error = null
                    )
                }

                val scanMode = settingsRepository.scanMode.first()
                scanImagesUseCase(scanMode).collect { (progress, images) ->
                    _uiState.update { it.copy(scanProgress = progress) }

                    if (progress.phase == ScanPhase.COMPLETE && images.isNotEmpty()) {
                        _uiState.update {
                            it.copy(
                                scanProgress = ScanProgress(ScanPhase.COMPARING, 0, images.size)
                            )
                        }

                        val duplicateGroups = findDuplicatesUseCase(images, scanMode)

                        val totalDuplicates = duplicateGroups.sumOf { it.imageCount - 1 }
                        val potentialSavings = duplicateGroups.sumOf { it.potentialSavings }

                        settingsRepository.setLastScanTimestamp(System.currentTimeMillis() / 1000)

                        _uiState.update {
                            it.copy(
                                scanProgress = ScanProgress(ScanPhase.COMPLETE, images.size, images.size),
                                duplicateGroups = duplicateGroups,
                                totalDuplicates = totalDuplicates,
                                potentialSavings = potentialSavings
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        scanProgress = ScanProgress(ScanPhase.ERROR, 0, 0),
                        error = e.message ?: "Unknown error occurred"
                    )
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update {
            ScanUiState()
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}
