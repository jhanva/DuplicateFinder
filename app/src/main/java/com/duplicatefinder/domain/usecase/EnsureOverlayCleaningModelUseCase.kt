package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.repository.OverlayCleaningModelRepository
import com.duplicatefinder.domain.repository.OverlayModelBundleInfo
import javax.inject.Inject

class EnsureOverlayCleaningModelUseCase @Inject constructor(
    private val cleaningModelRepository: OverlayCleaningModelRepository
) {

    suspend operator fun invoke(
        allowDownload: Boolean
    ): EnsureOverlayCleaningModelResult {
        val activeModel = cleaningModelRepository.getActiveModelInfo()
        if (activeModel != null) {
            return EnsureOverlayCleaningModelResult(
                status = EnsureOverlayCleaningModelStatus.AVAILABLE,
                modelInfo = activeModel
            )
        }

        if (!cleaningModelRepository.isDownloadConfigured()) {
            return EnsureOverlayCleaningModelResult(
                status = EnsureOverlayCleaningModelStatus.MISSING_CONFIGURATION,
                errorMessage = MISSING_CLEANING_MODEL_CONFIGURATION_MESSAGE
            )
        }

        if (!allowDownload) {
            return EnsureOverlayCleaningModelResult(
                status = EnsureOverlayCleaningModelStatus.MISSING_CONFIGURATION,
                errorMessage = CLEANING_MODEL_NOT_AVAILABLE_LOCALLY_MESSAGE
            )
        }

        val downloadResult = cleaningModelRepository.downloadModel()
        return downloadResult.fold(
            onSuccess = { modelInfo ->
                EnsureOverlayCleaningModelResult(
                    status = EnsureOverlayCleaningModelStatus.DOWNLOADED,
                    modelInfo = modelInfo
                )
            },
            onFailure = { error ->
                EnsureOverlayCleaningModelResult(
                    status = EnsureOverlayCleaningModelStatus.FAILED,
                    errorMessage = error.message
                )
            }
        )
    }
}

data class EnsureOverlayCleaningModelResult(
    val status: EnsureOverlayCleaningModelStatus,
    val modelInfo: OverlayModelBundleInfo? = null,
    val errorMessage: String? = null
)

enum class EnsureOverlayCleaningModelStatus {
    AVAILABLE,
    DOWNLOADED,
    MISSING_CONFIGURATION,
    FAILED
}

private const val MISSING_CLEANING_MODEL_CONFIGURATION_MESSAGE =
    "Overlay cleaning model URL is not configured in this build."

private const val CLEANING_MODEL_NOT_AVAILABLE_LOCALLY_MESSAGE =
    "Overlay cleaning model is not available locally. Enable downloads and try again."
