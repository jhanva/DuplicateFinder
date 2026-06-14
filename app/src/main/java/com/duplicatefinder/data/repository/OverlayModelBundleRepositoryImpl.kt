package com.duplicatefinder.data.repository

import com.duplicatefinder.domain.repository.OverlayDetectorOutputFormat
import com.duplicatefinder.domain.repository.OverlayModelBundleInfo
import com.duplicatefinder.domain.repository.OverlayModelBundleRepository
import com.duplicatefinder.domain.repository.OverlayModelRuntime
import com.duplicatefinder.domain.repository.OverlayOnnxDetectorContract
import com.duplicatefinder.domain.repository.OverlayOnnxMaskRefinerContract
import com.duplicatefinder.domain.repository.OverlayOnnxRuntimeContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Loads the overlay detection model bundle from local app storage only.
 * The app is fully offline: the bundle ships pre-installed (sideloaded into
 * [bundleDir] alongside a `bundle.json` manifest) and is never downloaded.
 */
@Singleton
class OverlayModelBundleRepositoryImpl @Inject constructor(
    @Named("overlayModelBundleDir") private val bundleDir: File,
    private val assetInstaller: OverlayModelAssetInstaller
) : OverlayModelBundleRepository {

    override suspend fun getActiveBundleInfo(): OverlayModelBundleInfo? = withContext(Dispatchers.IO) {
        // Make sure the model that ships in the APK is unpacked locally before
        // we look for it; on first run this copies it into bundleDir.
        assetInstaller.installIfNeeded()

        val manifestFile = File(bundleDir, MANIFEST_FILE_NAME)
        if (!manifestFile.exists()) return@withContext null

        runCatching {
            manifestFile.readText().toBundleInfo()
                .takeIf(::isBundleComplete)
        }.getOrNull()
    }

    override suspend fun ensureBundleAvailable(): OverlayModelBundleInfo? {
        return getActiveBundleInfo()
    }

    private fun String.toBundleInfo(): OverlayModelBundleInfo {
        val json = JSONObject(this)
        return OverlayModelBundleInfo(
            bundleVersion = json.optString("bundleVersion", "overlay-bundle-v1"),
            runtime = json.optString("runtime")
                .takeIf { it.isNotBlank() }
                ?.let(OverlayModelRuntime::fromManifestValue)
                ?: OverlayModelRuntime.ONNX_RUNTIME_ANDROID,
            textDetectorPath = json.optString("textDetectorPath")
                .takeIf { it.isNotBlank() }
                ?: json.getString("detectorStage1Path"),
            maskRefinerEncoderPath = json.optString("maskRefinerEncoderPath")
                .takeIf { it.isNotBlank() }
                ?: json.optString("detectorStage2Path").takeIf { it.isNotBlank() },
            maskRefinerDecoderPath = json.optString("maskRefinerDecoderPath")
                .takeIf { it.isNotBlank() }
                ?: json.optString("detectorStage2Path").takeIf { it.isNotBlank() },
            inputSizeTextDetector = json.optInt(
                "inputSizeTextDetector",
                json.optInt("inputSizeStage1", 512)
            ),
            inputSizeMaskRefiner = json.optInt(
                "inputSizeMaskRefiner",
                json.optInt("inputSizeStage2", 512)
            ),
            onnx = json.optJSONObject("onnx").toOnnxRuntimeContract(),
            manifestUrl = null
        )
    }

    private fun JSONObject?.toOnnxRuntimeContract(): OverlayOnnxRuntimeContract {
        val detectorJson = this?.optJSONObject("detector")
        val maskRefinerJson = this?.optJSONObject("maskRefiner")
        return OverlayOnnxRuntimeContract(
            detector = OverlayOnnxDetectorContract(
                inputName = detectorJson?.optString("inputName")
                    .takeUnless { it.isNullOrBlank() }
                    ?: "image",
                outputName = detectorJson?.optString("outputName")
                    .takeUnless { it.isNullOrBlank() }
                    ?: "output",
                outputFormat = detectorJson?.optString("outputFormat")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(OverlayDetectorOutputFormat::fromManifestValue)
                    ?: OverlayDetectorOutputFormat.HEATMAP,
                confidenceThreshold = detectorJson?.optDouble("confidenceThreshold", 0.45)
                    ?.toFloat()
                    ?: 0.45f,
                minRegionAreaRatio = detectorJson?.optDouble("minRegionAreaRatio", 0.0025)
                    ?.toFloat()
                    ?: 0.0025f
            ),
            maskRefiner = OverlayOnnxMaskRefinerContract(
                encoderInputName = maskRefinerJson?.optString("encoderInputName")
                    .takeUnless { it.isNullOrBlank() }
                    ?: "image",
                encoderOutputName = maskRefinerJson?.optString("encoderOutputName")
                    .takeUnless { it.isNullOrBlank() }
                    ?: "image_embeddings",
                decoderEmbeddingInputName = maskRefinerJson?.optString("decoderEmbeddingInputName")
                    .takeUnless { it.isNullOrBlank() }
                    ?: "image_embeddings",
                decoderPointCoordsInputName = maskRefinerJson?.optString("decoderPointCoordsInputName")
                    .takeUnless { it.isNullOrBlank() }
                    ?: "point_coords",
                decoderPointLabelsInputName = maskRefinerJson?.optString("decoderPointLabelsInputName")
                    .takeUnless { it.isNullOrBlank() }
                    ?: "point_labels",
                decoderMaskInputName = maskRefinerJson?.optString("decoderMaskInputName")
                    .takeUnless { it.isNullOrBlank() }
                    ?: "mask_input",
                decoderHasMaskInputName = maskRefinerJson?.optString("decoderHasMaskInputName")
                    .takeUnless { it.isNullOrBlank() }
                    ?: "has_mask_input",
                decoderOrigImSizeInputName = maskRefinerJson?.optString("decoderOrigImSizeInputName")
                    .takeUnless { it.isNullOrBlank() }
                    ?: "orig_im_size",
                decoderOutputName = maskRefinerJson?.optString("decoderOutputName")
                    .takeUnless { it.isNullOrBlank() }
                    ?: "masks",
                decoderScoreOutputName = maskRefinerJson?.optString("decoderScoreOutputName")
                    .takeUnless { it.isNullOrBlank() }
                    ?: "iou_predictions",
                maskThreshold = maskRefinerJson?.optDouble("maskThreshold", 0.0)
                    ?.toFloat()
                    ?: 0f
            )
        )
    }

    private fun isBundleComplete(bundleInfo: OverlayModelBundleInfo): Boolean {
        return bundleInfo.requiredAssetPaths.all { path ->
            File(bundleDir, path.substringAfterLast('/')).let { file ->
                file.exists() && file.length() > 0L
            }
        }
    }

    companion object {
        private const val MANIFEST_FILE_NAME = "bundle.json"
    }
}
