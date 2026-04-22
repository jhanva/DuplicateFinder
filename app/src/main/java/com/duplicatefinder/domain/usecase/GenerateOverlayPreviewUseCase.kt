package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.model.CleaningPreview
import com.duplicatefinder.domain.model.OverlayDetection
import com.duplicatefinder.domain.repository.OverlayCleaningRepository
import javax.inject.Inject

class GenerateOverlayPreviewUseCase @Inject constructor(
    private val ensureOverlayCleaningModelUseCase: EnsureOverlayCleaningModelUseCase,
    private val overlayCleaningRepository: OverlayCleaningRepository
) {

    suspend operator fun invoke(
        detection: OverlayDetection,
        allowDownload: Boolean
    ): Result<CleaningPreview> {
        val modelResult = ensureOverlayCleaningModelUseCase(allowDownload = allowDownload)
        val modelInfo = modelResult.modelInfo ?: return Result.failure(
            IllegalStateException(
                modelResult.errorMessage ?: "Overlay cleaning model is not available."
            )
        )

        return overlayCleaningRepository.generatePreview(
            image = detection.image,
            detection = detection,
            bundleInfo = modelInfo
        )
    }
}
