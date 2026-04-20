package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.model.CleaningPreview
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.OverlayPreviewDecision
import com.duplicatefinder.domain.repository.OverlayCleaningRepository
import javax.inject.Inject

class ApplyOverlayPreviewDecisionUseCase @Inject constructor(
    private val overlayCleaningRepository: OverlayCleaningRepository
) {

    suspend operator fun invoke(
        image: ImageItem,
        preview: CleaningPreview,
        decision: OverlayPreviewDecision
    ): Result<Unit> {
        return overlayCleaningRepository.applyDecision(
            image = image,
            preview = preview,
            decision = decision
        )
    }
}
