package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.repository.OverlayModelBundleInfo
import com.duplicatefinder.domain.repository.OverlayModelBundleRepository
import javax.inject.Inject

class EnsureOverlayModelBundleUseCase @Inject constructor(
    private val bundleRepository: OverlayModelBundleRepository
) {

    suspend operator fun invoke(): EnsureOverlayModelBundleResult {
        val activeBundle = bundleRepository.getActiveBundleInfo()
        if (activeBundle != null) {
            return EnsureOverlayModelBundleResult(
                status = EnsureOverlayModelBundleStatus.AVAILABLE,
                bundleInfo = activeBundle
            )
        }

        return EnsureOverlayModelBundleResult(
            status = EnsureOverlayModelBundleStatus.MISSING_BUNDLE,
            errorMessage = BUNDLE_NOT_AVAILABLE_LOCALLY_MESSAGE
        )
    }
}

data class EnsureOverlayModelBundleResult(
    val status: EnsureOverlayModelBundleStatus,
    val bundleInfo: OverlayModelBundleInfo? = null,
    val errorMessage: String? = null
)

enum class EnsureOverlayModelBundleStatus {
    AVAILABLE,
    MISSING_BUNDLE
}

private const val BUNDLE_NOT_AVAILABLE_LOCALLY_MESSAGE =
    "Overlay model could not be loaded. Falling back to the built-in heuristic analysis. " +
        "The detection model ships inside the app and runs fully offline."
