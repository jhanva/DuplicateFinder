package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.repository.OverlayModelBundleInfo
import com.duplicatefinder.domain.repository.OverlayModelBundleRepository
import javax.inject.Inject

class EnsureOverlayModelBundleUseCase @Inject constructor(
    private val bundleRepository: OverlayModelBundleRepository
) {

    suspend operator fun invoke(
        allowDownload: Boolean
    ): EnsureOverlayModelBundleResult {
        val activeBundle = bundleRepository.getActiveBundleInfo()
        if (activeBundle != null) {
            return EnsureOverlayModelBundleResult(
                status = EnsureOverlayModelBundleStatus.AVAILABLE,
                bundleInfo = activeBundle
            )
        }

        if (!allowDownload) {
            return EnsureOverlayModelBundleResult(
                status = EnsureOverlayModelBundleStatus.MISSING_CONFIGURATION
            )
        }

        val downloadResult = bundleRepository.downloadBundle()
        return downloadResult.fold(
            onSuccess = { bundleInfo ->
                EnsureOverlayModelBundleResult(
                    status = EnsureOverlayModelBundleStatus.DOWNLOADED,
                    bundleInfo = bundleInfo
                )
            },
            onFailure = { error ->
                EnsureOverlayModelBundleResult(
                    status = EnsureOverlayModelBundleStatus.FAILED,
                    errorMessage = error.message
                )
            }
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
    DOWNLOADED,
    MISSING_CONFIGURATION,
    FAILED
}
