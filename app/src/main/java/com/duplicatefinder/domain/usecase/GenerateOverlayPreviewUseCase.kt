package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.model.CleaningPreview
import com.duplicatefinder.domain.model.OverlayDetection
import com.duplicatefinder.domain.repository.OverlayCleaningRepository
import javax.inject.Inject

class GenerateOverlayPreviewUseCase @Inject constructor(
    private val ensureOverlayModelBundleUseCase: EnsureOverlayModelBundleUseCase,
    private val overlayCleaningRepository: OverlayCleaningRepository
) {

    suspend operator fun invoke(
        detection: OverlayDetection,
        allowDownload: Boolean
    ): Result<CleaningPreview> {
        val bundleResult = ensureOverlayModelBundleUseCase(allowDownload = allowDownload)
        val bundleInfo = bundleResult.bundleInfo ?: return Result.failure(
            IllegalStateException(
                bundleResult.errorMessage ?: "Overlay model bundle is not available."
            )
        )

        return overlayCleaningRepository.generatePreview(
            image = detection.image,
            detection = detection,
            bundleInfo = bundleInfo
        )
    }
}
