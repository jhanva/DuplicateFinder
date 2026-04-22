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

        if (!bundleRepository.isDownloadConfigured()) {
            return EnsureOverlayModelBundleResult(
                status = EnsureOverlayModelBundleStatus.MISSING_CONFIGURATION,
                errorMessage = MISSING_MANIFEST_CONFIGURATION_MESSAGE
            )
        }

        if (!allowDownload) {
            return EnsureOverlayModelBundleResult(
                status = EnsureOverlayModelBundleStatus.MISSING_CONFIGURATION,
                errorMessage = BUNDLE_NOT_AVAILABLE_LOCALLY_MESSAGE
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

private const val MISSING_MANIFEST_CONFIGURATION_MESSAGE =
    "Overlay model manifest URL is not configured in this build. Rebuild the app with OVERLAY_MODEL_MANIFEST_URL or the overlayModelManifestUrl Gradle property."

private const val BUNDLE_NOT_AVAILABLE_LOCALLY_MESSAGE =
    "Overlay model bundle is not available locally. Enable downloads or ship the bundle with the app."
