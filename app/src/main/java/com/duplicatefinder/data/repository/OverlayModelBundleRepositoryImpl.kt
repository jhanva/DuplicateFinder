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
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class OverlayModelBundleRepositoryImpl @Inject constructor(
    @Named("overlayModelManifestUrl") private val manifestUrl: String,
    @Named("overlayModelBundleDir") private val bundleDir: File
) : OverlayModelBundleRepository {

    override fun isDownloadConfigured(): Boolean = manifestUrl.isNotBlank()

    override suspend fun getActiveBundleInfo(): OverlayModelBundleInfo? = withContext(Dispatchers.IO) {
        val manifestFile = File(bundleDir, MANIFEST_FILE_NAME)
        if (!manifestFile.exists()) return@withContext null

        runCatching {
            manifestFile.readText().toBundleInfo(manifestUrl)
                .takeIf(::isBundleComplete)
        }.getOrNull()
    }

    override suspend fun ensureBundleAvailable(): OverlayModelBundleInfo? {
        return getActiveBundleInfo()
    }

    override suspend fun downloadBundle(): Result<OverlayModelBundleInfo> = withContext(Dispatchers.IO) {
        if (manifestUrl.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Overlay model manifest URL is not configured.")
            )
        }

        runCatching {
            bundleDir.mkdirs()
            val manifestJson = downloadText(manifestUrl)
            val bundleInfo = manifestJson.toBundleInfo(manifestUrl)
            val writtenFiles = mutableListOf<File>()

            try {
                bundleInfo.requiredAssetPaths.forEach { assetPath ->
                    writtenFiles += downloadAsset(assetPath)
                }

                if (!isBundleComplete(bundleInfo)) {
                    throw IllegalStateException("Overlay model bundle is incomplete after download.")
                }

                File(bundleDir, MANIFEST_FILE_NAME).writeText(manifestJson)
            } catch (error: Exception) {
                writtenFiles.forEach { it.delete() }
                File(bundleDir, MANIFEST_FILE_NAME).delete()
                throw error
            }
            bundleInfo
        }
    }

    private fun downloadAsset(path: String): File {
        val source = if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            URL(URL(manifestUrl), path).toString()
        }
        val target = File(bundleDir, path.substringAfterLast('/'))
        val tempTarget = File(target.parentFile, "${target.name}.part")

        URL(source).openConnection().let { connection ->
            connection as HttpURLConnection
            connection.connectTimeout = CONNECTION_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Failed to download overlay asset: $source")
            }
            tempTarget.outputStream().use { output ->
                connection.inputStream.use { input -> input.copyTo(output) }
            }
            connection.disconnect()
        }
        if (tempTarget.length() <= 0L) {
            tempTarget.delete()
            throw IllegalStateException("Downloaded overlay asset is empty: $source")
        }
        if (target.exists() && !target.delete()) {
            tempTarget.delete()
            throw IllegalStateException("Failed to replace existing overlay asset: ${target.name}")
        }
        if (!tempTarget.renameTo(target)) {
            tempTarget.delete()
            throw IllegalStateException("Failed to finalize overlay asset download: ${target.name}")
        }
        return target
    }

    private fun downloadText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECTION_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.requestMethod = "GET"
        connection.connect()
        return try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Failed to download overlay manifest: $url")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun String.toBundleInfo(defaultManifestUrl: String): OverlayModelBundleInfo {
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
                ?: json.getString("detectorStage2Path"),
            maskRefinerDecoderPath = json.optString("maskRefinerDecoderPath")
                .takeIf { it.isNotBlank() }
                ?: json.getString("detectorStage2Path"),
            inputSizeTextDetector = json.optInt(
                "inputSizeTextDetector",
                json.optInt("inputSizeStage1", 512)
            ),
            inputSizeMaskRefiner = json.optInt(
                "inputSizeMaskRefiner",
                json.optInt("inputSizeStage2", 512)
            ),
            onnx = json.optJSONObject("onnx").toOnnxRuntimeContract(),
            manifestUrl = defaultManifestUrl
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
        private const val CONNECTION_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
    }
}
