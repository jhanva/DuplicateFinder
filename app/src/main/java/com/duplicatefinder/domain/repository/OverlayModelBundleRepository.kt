package com.duplicatefinder.domain.repository

enum class OverlayModelRuntime {
    ONNX_RUNTIME_ANDROID;

    companion object {
        fun fromManifestValue(value: String): OverlayModelRuntime {
            return when (value.trim().lowercase()) {
                "onnxruntime-android" -> ONNX_RUNTIME_ANDROID
                else -> valueOf(value.replace('-', '_').uppercase())
            }
        }
    }
}

enum class OverlayDetectorOutputFormat {
    HEATMAP,
    BOXES_NORMALIZED;

    companion object {
        fun fromManifestValue(value: String): OverlayDetectorOutputFormat {
            return when (value.trim().lowercase()) {
                "heatmap" -> HEATMAP
                "boxes_normalized", "boxes-normalized", "boxes" -> BOXES_NORMALIZED
                else -> valueOf(value.replace('-', '_').uppercase())
            }
        }
    }
}

enum class OverlayTensorRange {
    ZERO_TO_ONE,
    NEGATIVE_ONE_TO_ONE;

    companion object {
        fun fromManifestValue(value: String): OverlayTensorRange {
            return when (value.trim().lowercase()) {
                "zero_to_one", "zero-to-one", "zero_to_one_rgb" -> ZERO_TO_ONE
                "negative_one_to_one", "negative-one-to-one", "minus_one_to_one" -> NEGATIVE_ONE_TO_ONE
                else -> valueOf(value.replace('-', '_').uppercase())
            }
        }
    }
}

data class OverlayOnnxDetectorContract(
    val inputName: String = "image",
    val outputName: String = "output",
    val outputFormat: OverlayDetectorOutputFormat = OverlayDetectorOutputFormat.HEATMAP,
    val confidenceThreshold: Float = 0.45f,
    val minRegionAreaRatio: Float = 0.0025f
)

data class OverlayOnnxMaskRefinerContract(
    val encoderInputName: String = "image",
    val encoderOutputName: String = "image_embeddings",
    val decoderEmbeddingInputName: String = "image_embeddings",
    val decoderPointCoordsInputName: String = "point_coords",
    val decoderPointLabelsInputName: String = "point_labels",
    val decoderMaskInputName: String = "mask_input",
    val decoderHasMaskInputName: String = "has_mask_input",
    val decoderOrigImSizeInputName: String = "orig_im_size",
    val decoderOutputName: String = "masks",
    val decoderScoreOutputName: String = "iou_predictions",
    val maskThreshold: Float = 0f
)

data class OverlayOnnxRuntimeContract(
    val detector: OverlayOnnxDetectorContract = OverlayOnnxDetectorContract(),
    val maskRefiner: OverlayOnnxMaskRefinerContract = OverlayOnnxMaskRefinerContract()
)

data class OverlayModelBundleInfo(
    val bundleVersion: String,
    val runtime: OverlayModelRuntime,
    val textDetectorPath: String,
    val maskRefinerEncoderPath: String,
    val maskRefinerDecoderPath: String,
    val inputSizeTextDetector: Int,
    val inputSizeMaskRefiner: Int,
    val onnx: OverlayOnnxRuntimeContract = OverlayOnnxRuntimeContract(),
    val manifestUrl: String? = null
) {
    val requiredAssetPaths: List<String>
        get() = listOf(
            textDetectorPath,
            maskRefinerEncoderPath,
            maskRefinerDecoderPath
        )
}

interface OverlayModelBundleRepository {
    fun isDownloadConfigured(): Boolean
    suspend fun getActiveBundleInfo(): OverlayModelBundleInfo?
    suspend fun ensureBundleAvailable(): OverlayModelBundleInfo?
    suspend fun downloadBundle(): Result<OverlayModelBundleInfo>
}
