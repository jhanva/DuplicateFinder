package com.duplicatefinder.data.repository

import com.duplicatefinder.domain.repository.OverlayCleaningModelRepository
import com.duplicatefinder.domain.repository.OverlayInpainterInputFormat
import com.duplicatefinder.domain.repository.OverlayModelBundleInfo
import com.duplicatefinder.domain.repository.OverlayModelRuntime
import com.duplicatefinder.domain.repository.OverlayOnnxInpainterContract
import com.duplicatefinder.domain.repository.OverlayOnnxRuntimeContract
import com.duplicatefinder.domain.repository.OverlayTensorRange
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
class OverlayCleaningModelRepositoryImpl @Inject constructor(
    @Named("overlayCleaningModelUrl") private val modelUrl: String,
    @Named("overlayCleaningModelDir") private val modelDir: File,
    private val bundledModelSource: OverlayCleaningBundledModelSource
) : OverlayCleaningModelRepository {

    override fun isDownloadConfigured(): Boolean = modelUrl.isNotBlank()

    override suspend fun getActiveModelInfo(): OverlayModelBundleInfo? = withContext(Dispatchers.IO) {
        resolveActiveModelInfo()
    }

    override suspend fun downloadModel(): Result<OverlayModelBundleInfo> = withContext(Dispatchers.IO) {
        resolveActiveModelInfo()?.let { existingModel ->
            return@withContext Result.success(existingModel)
        }

        if (modelUrl.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Overlay cleaning model URL is not configured.")
            )
        }

        runCatching {
            modelDir.mkdirs()
            val targetFileName = URL(modelUrl).path.substringAfterLast('/').ifBlank { DEFAULT_MODEL_FILE_NAME }
            val target = File(modelDir, targetFileName)
            val tempTarget = File(modelDir, "$targetFileName.part")

            downloadAsset(
                sourceUrl = modelUrl,
                tempTarget = tempTarget
            )

            if (target.exists() && !target.delete()) {
                tempTarget.delete()
                throw IllegalStateException("Failed to replace existing overlay cleaning model: ${target.name}")
            }
            if (!tempTarget.renameTo(target)) {
                tempTarget.delete()
                throw IllegalStateException("Failed to finalize overlay cleaning model download: ${target.name}")
            }

            val modelInfo = buildModelInfo(
                fileName = targetFileName,
                sourceUrl = modelUrl
            )
            if (!isModelComplete(modelInfo)) {
                target.delete()
                throw IllegalStateException("Overlay cleaning model is incomplete after download.")
            }

            File(modelDir, METADATA_FILE_NAME).writeText(modelInfo.toMetadataJson())
            modelInfo
        }
    }

    private fun downloadAsset(
        sourceUrl: String,
        tempTarget: File
    ) {
        val connection = URL(sourceUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECTION_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.requestMethod = "GET"
        connection.connect()
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Failed to download overlay cleaning model: $sourceUrl")
            }
            tempTarget.outputStream().use { output ->
                connection.inputStream.use { input -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        if (tempTarget.length() <= 0L) {
            tempTarget.delete()
            throw IllegalStateException("Downloaded overlay cleaning model is empty: $sourceUrl")
        }
    }

    private fun buildModelInfo(
        fileName: String,
        sourceUrl: String?
    ): OverlayModelBundleInfo {
        return OverlayModelBundleInfo(
            bundleVersion = DEFAULT_MODEL_VERSION,
            runtime = OverlayModelRuntime.ONNX_RUNTIME_ANDROID,
            textDetectorPath = "",
            maskRefinerEncoderPath = "",
            maskRefinerDecoderPath = "",
            inpainterPath = fileName,
            inputSizeTextDetector = 0,
            inputSizeMaskRefiner = 0,
            inputSizeInpainter = DEFAULT_INPUT_SIZE,
            onnx = OverlayOnnxRuntimeContract(
                inpainter = OverlayOnnxInpainterContract(
                    imageInputName = "image",
                    maskInputName = "mask",
                    outputName = DEFAULT_OUTPUT_NAME,
                    inputFormat = OverlayInpainterInputFormat.IMAGE_AND_MASK,
                    tensorRange = OverlayTensorRange.NEGATIVE_ONE_TO_ONE
                )
            ),
            manifestUrl = sourceUrl
        )
    }

    private fun resolveActiveModelInfo(): OverlayModelBundleInfo? {
        return readStoredModelInfo() ?: materializeBundledModelIfAvailable()
    }

    private fun readStoredModelInfo(): OverlayModelBundleInfo? {
        val metadataFile = File(modelDir, METADATA_FILE_NAME)
        if (!metadataFile.exists()) return null

        return runCatching {
            metadataFile.readText()
                .toCleaningModelInfo()
                .takeIf(::isModelComplete)
        }.getOrNull()
    }

    private fun materializeBundledModelIfAvailable(): OverlayModelBundleInfo? {
        if (!bundledModelSource.exists()) return null

        modelDir.mkdirs()
        val targetFileName = bundledModelSource.fileName.ifBlank { DEFAULT_MODEL_FILE_NAME }
        val target = File(modelDir, targetFileName)
        if (!target.exists() || target.length() <= 0L) {
            val tempTarget = File(modelDir, "$targetFileName.part")
            bundledModelSource.open()?.use { input ->
                tempTarget.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            if (tempTarget.length() <= 0L) {
                tempTarget.delete()
                return null
            }
            if (target.exists() && !target.delete()) {
                tempTarget.delete()
                throw IllegalStateException("Failed to replace existing bundled overlay cleaning model: ${target.name}")
            }
            if (!tempTarget.renameTo(target)) {
                tempTarget.delete()
                throw IllegalStateException("Failed to finalize bundled overlay cleaning model: ${target.name}")
            }
        }

        val modelInfo = buildModelInfo(
            fileName = targetFileName,
            sourceUrl = bundledModelSource.sourceId
        )
        if (!isModelComplete(modelInfo)) {
            return null
        }
        File(modelDir, METADATA_FILE_NAME).writeText(modelInfo.toMetadataJson())
        return modelInfo
    }

    private fun OverlayModelBundleInfo.toMetadataJson(): String {
        return JSONObject().apply {
            put("bundleVersion", bundleVersion)
            put("runtime", "onnxruntime-android")
            put("inpainterPath", inpainterPath)
            put("inputSizeInpainter", inputSizeInpainter)
            put("modelUrl", manifestUrl ?: "")
            put(
                "onnx",
                JSONObject().apply {
                    put(
                        "inpainter",
                        JSONObject().apply {
                            put("imageInputName", onnx.inpainter.imageInputName)
                            put("maskInputName", onnx.inpainter.maskInputName)
                            put("outputName", onnx.inpainter.outputName)
                            put(
                                "inputFormat",
                                onnx.inpainter.inputFormat.name.lowercase().replace('_', '-')
                            )
                            put(
                                "tensorRange",
                                onnx.inpainter.tensorRange.name.lowercase().replace('_', '-')
                            )
                        }
                    )
                }
            )
        }.toString()
    }

    private fun String.toCleaningModelInfo(): OverlayModelBundleInfo {
        val json = JSONObject(this)
        val inpainterJson = json.optJSONObject("onnx")?.optJSONObject("inpainter")
        return OverlayModelBundleInfo(
            bundleVersion = json.optString("bundleVersion", DEFAULT_MODEL_VERSION),
            runtime = json.optString("runtime")
                .takeIf { it.isNotBlank() }
                ?.let(OverlayModelRuntime::fromManifestValue)
                ?: OverlayModelRuntime.ONNX_RUNTIME_ANDROID,
            textDetectorPath = "",
            maskRefinerEncoderPath = "",
            maskRefinerDecoderPath = "",
            inpainterPath = json.optString("inpainterPath", DEFAULT_MODEL_FILE_NAME),
            inputSizeTextDetector = 0,
            inputSizeMaskRefiner = 0,
            inputSizeInpainter = json.optInt("inputSizeInpainter", DEFAULT_INPUT_SIZE),
            onnx = OverlayOnnxRuntimeContract(
                inpainter = OverlayOnnxInpainterContract(
                    imageInputName = inpainterJson?.optString("imageInputName")
                        ?.takeUnless { it.isNullOrBlank() }
                        ?: "image",
                    maskInputName = inpainterJson?.optString("maskInputName")
                        ?.takeUnless { it.isNullOrBlank() }
                        ?: "mask",
                    outputName = inpainterJson?.optString("outputName")
                        ?.takeUnless { it.isNullOrBlank() }
                        ?: DEFAULT_OUTPUT_NAME,
                    inputFormat = inpainterJson?.optString("inputFormat")
                        ?.takeIf { it.isNotBlank() }
                        ?.let(OverlayInpainterInputFormat::fromManifestValue)
                        ?: OverlayInpainterInputFormat.IMAGE_AND_MASK,
                    tensorRange = inpainterJson?.optString("tensorRange")
                        ?.takeIf { it.isNotBlank() }
                        ?.let(OverlayTensorRange::fromManifestValue)
                        ?: OverlayTensorRange.NEGATIVE_ONE_TO_ONE
                )
            ),
            manifestUrl = json.optString("modelUrl").takeIf { it.isNotBlank() }
        )
    }

    private fun isModelComplete(modelInfo: OverlayModelBundleInfo): Boolean {
        val requiredFile = modelInfo.inpainterPath.takeIf { it.isNotBlank() } ?: return false
        return File(modelDir, requiredFile.substringAfterLast('/')).let { file ->
            file.exists() && file.length() > 0L
        }
    }

    companion object {
        private const val METADATA_FILE_NAME = "cleaning-model.json"
        private const val DEFAULT_MODEL_FILE_NAME = "AOT-GAN.onnx"
        private const val DEFAULT_MODEL_VERSION = "overlay-cleaning-aot-gan-v1"
        private const val DEFAULT_OUTPUT_NAME = "painted_image"
        private const val DEFAULT_INPUT_SIZE = 512
        private const val CONNECTION_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
    }
}
