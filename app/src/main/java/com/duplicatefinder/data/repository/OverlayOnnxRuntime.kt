package com.duplicatefinder.data.repository

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.duplicatefinder.domain.model.DetectionStage
import com.duplicatefinder.domain.model.OverlayModelExecutionException
import com.duplicatefinder.domain.model.OverlayRegion
import com.duplicatefinder.domain.repository.OverlayInpainterInputFormat
import com.duplicatefinder.domain.repository.OverlayModelBundleInfo
import com.duplicatefinder.domain.repository.OverlayTensorRange
import java.io.File
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Singleton
class OverlayOnnxRuntime @Inject constructor(
    @Named("overlayModelBundleDir") private val bundleDir: File
) {

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val sessionCache = ConcurrentHashMap<OverlayOnnxSessionCacheKey, OrtSession>()
    private val sessionKeysByPath = ConcurrentHashMap<String, OverlayOnnxSessionCacheKey>()

    fun analyze(
        sourceBitmap: Bitmap,
        bundleInfo: OverlayModelBundleInfo
    ): OverlayAnalysisResult {
        val stage1Prepared = prepareSquareBitmap(
            bitmap = sourceBitmap,
            targetSize = bundleInfo.inputSizeTextDetector
        )
        return try {
            val detectorSession = loadSession(bundleInfo.textDetectorPath)
            val inputTensor = createImageTensor(
                bitmap = stage1Prepared.squareBitmap,
                tensorRange = OverlayTensorRange.ZERO_TO_ONE
            )
            try {
                detectorSession.run(
                    mapOf(bundleInfo.onnx.detector.inputName to inputTensor)
                ).useResult { result ->
                    val outputTensor = result.findTensor(bundleInfo.onnx.detector.outputName)
                        ?: throw OverlayModelExecutionException(
                            "Overlay model bundle failed during detection. Download the bundle again and retry."
                        )
                    val values = outputTensor.readFloatArray()
                    val shape = (outputTensor.info as TensorInfo).shape
                    val squareRegions = OverlayOnnxPostProcessor.decodeDetectorOutput(
                        values = values,
                        shape = shape,
                        width = stage1Prepared.squareBitmap.width,
                        height = stage1Prepared.squareBitmap.height,
                        outputFormat = bundleInfo.onnx.detector.outputFormat,
                        confidenceThreshold = bundleInfo.onnx.detector.confidenceThreshold,
                        minRegionAreaRatio = bundleInfo.onnx.detector.minRegionAreaRatio
                    )
                    val stage1Regions = stage1Prepared.restoreRegions(squareRegions)
                    val stage1Analysis = OverlayOnnxPostProcessor.buildAnalysisResult(
                        regions = stage1Regions,
                        stage = DetectionStage.STAGE_1_CANDIDATE
                    )
                    if (stage1Regions.isEmpty()) {
                        return@useResult stage1Analysis
                    }

                    val refinedRegions = refineRegions(
                        sourceBitmap = sourceBitmap,
                        detectorRegions = stage1Regions,
                        bundleInfo = bundleInfo
                    )
                    if (refinedRegions.isEmpty()) {
                        stage1Analysis
                    } else {
                        OverlayOnnxPostProcessor.buildAnalysisResult(
                            regions = refinedRegions,
                            stage = DetectionStage.STAGE_2_REFINED
                        )
                    }
                }
            } finally {
                inputTensor.close()
            }
        } catch (error: OverlayModelExecutionException) {
            throw error
        } catch (error: Exception) {
            throw OverlayModelExecutionException(
                "Overlay model bundle failed during detection. Download the bundle again and retry.",
                error
            )
        } finally {
            stage1Prepared.squareBitmap.recycle()
        }
    }

    fun inpaint(
        sourceBitmap: Bitmap,
        regions: List<OverlayRegion>,
        bundleInfo: OverlayModelBundleInfo
    ): Bitmap {
        if (regions.isEmpty()) return sourceBitmap.copy(Bitmap.Config.ARGB_8888, false)
        val prepared = prepareSquareBitmap(
            bitmap = sourceBitmap,
            targetSize = bundleInfo.inputSizeInpainter
        )
        return try {
            val inpainterSession = loadSession(bundleInfo.inpainterPath)
            val squareRegions = prepared.projectRegions(regions)
            val mask = OverlayOnnxPostProcessor.buildMask(
                width = prepared.squareBitmap.width,
                height = prepared.squareBitmap.height,
                regions = squareRegions
            )
            val imageTensor = createImageTensor(
                bitmap = prepared.squareBitmap,
                tensorRange = bundleInfo.onnx.inpainter.tensorRange
            )
            val maskTensor = createMaskTensor(
                mask = mask,
                width = prepared.squareBitmap.width,
                height = prepared.squareBitmap.height
            )

            try {
                val inputs = when (bundleInfo.onnx.inpainter.inputFormat) {
                    OverlayInpainterInputFormat.IMAGE_AND_MASK -> mapOf(
                        bundleInfo.onnx.inpainter.imageInputName to imageTensor,
                        bundleInfo.onnx.inpainter.maskInputName to maskTensor
                    )

                    OverlayInpainterInputFormat.CONCAT_IMAGE_MASK -> mapOf(
                        bundleInfo.onnx.inpainter.imageInputName to createConcatenatedImageMaskTensor(
                            bitmap = prepared.squareBitmap,
                            mask = mask,
                            tensorRange = bundleInfo.onnx.inpainter.tensorRange
                        )
                    )
                }

                try {
                    inpainterSession.run(inputs).useResult { result ->
                        val outputTensor = result.findTensor(bundleInfo.onnx.inpainter.outputName)
                            ?: throw OverlayModelExecutionException(
                                "Overlay model bundle failed during cleaning. Download the bundle again and retry."
                            )
                        val outputBitmap = tensorToBitmap(
                            tensor = outputTensor,
                            width = prepared.squareBitmap.width,
                            height = prepared.squareBitmap.height,
                            tensorRange = bundleInfo.onnx.inpainter.tensorRange
                        )
                        prepared.restoreBitmap(outputBitmap)
                    }
                } finally {
                    inputs.values.forEach { tensor -> if (tensor !== imageTensor && tensor !== maskTensor) tensor.close() }
                }
            } finally {
                imageTensor.close()
                maskTensor.close()
                prepared.squareBitmap.recycle()
            }
        } catch (error: OverlayModelExecutionException) {
            throw error
        } catch (error: Exception) {
            throw OverlayModelExecutionException(
                "Overlay model bundle failed during cleaning. Download the bundle again and retry.",
                error
            )
        }
    }

    private fun loadSession(relativePath: String): OrtSession {
        val file = File(bundleDir, relativePath.substringAfterLast('/'))
        val sessionKey = file.toSessionCacheKey()
        val previousKey = sessionKeysByPath.put(file.absolutePath, sessionKey)
        if (previousKey != null && previousKey != sessionKey) {
            sessionCache.remove(previousKey)?.closeQuietly()
        }
        return sessionCache.getOrPut(sessionKey) {
            environment.createSession(file.absolutePath, OrtSession.SessionOptions())
        }
    }

    private fun refineRegions(
        sourceBitmap: Bitmap,
        detectorRegions: List<OverlayRegion>,
        bundleInfo: OverlayModelBundleInfo
    ): List<OverlayRegion> {
        val prepared = prepareSquareBitmap(
            bitmap = sourceBitmap,
            targetSize = bundleInfo.inputSizeMaskRefiner
        )
        return try {
            val encoderSession = loadSession(bundleInfo.maskRefinerEncoderPath)
            val decoderSession = loadSession(bundleInfo.maskRefinerDecoderPath)
            val squareRegions = prepared.projectRegions(detectorRegions)
            val imageTensor = createImageTensor(
                bitmap = prepared.squareBitmap,
                tensorRange = OverlayTensorRange.ZERO_TO_ONE
            )
            try {
                encoderSession.run(
                    mapOf(bundleInfo.onnx.maskRefiner.encoderInputName to imageTensor)
                ).useResult { encoderResult ->
                    val embeddingTensor = encoderResult.findTensor(bundleInfo.onnx.maskRefiner.encoderOutputName)
                        ?: throw OverlayModelExecutionException(
                            "Overlay model bundle failed during detection. Download the bundle again and retry."
                        )
                    squareRegions.flatMap { squareRegion ->
                        runMaskRefinerDecoder(
                            decoderSession = decoderSession,
                            embeddingTensor = embeddingTensor,
                            squareRegion = squareRegion,
                            imageSize = prepared.squareBitmap.width to prepared.squareBitmap.height,
                            bundleInfo = bundleInfo
                        )
                    }.let(prepared::restoreRegions)
                }
            } finally {
                imageTensor.close()
            }
        } finally {
            prepared.squareBitmap.recycle()
        }
    }

    private fun runMaskRefinerDecoder(
        decoderSession: OrtSession,
        embeddingTensor: OnnxTensor,
        squareRegion: OverlayRegion,
        imageSize: Pair<Int, Int>,
        bundleInfo: OverlayModelBundleInfo
    ): List<OverlayRegion> {
        val (imageWidth, imageHeight) = imageSize
        val prompt = buildMobileSamBoxPrompt(squareRegion, imageWidth, imageHeight)
        val pointCoordsTensor = createTensor(
            values = prompt.pointCoords,
            shape = longArrayOf(1, 2, 2)
        )
        val pointLabelsTensor = createTensor(
            values = prompt.pointLabels,
            shape = longArrayOf(1, 2)
        )
        val maskInputShape = resolveMaskInputShape(decoderSession, bundleInfo)
        val emptyMaskTensor = createTensor(
            values = FloatArray(maskInputShape.drop(1).reduce(Int::times)),
            shape = maskInputShape.map(Int::toLong).toLongArray()
        )
        val hasMaskInputTensor = createTensor(
            values = floatArrayOf(0f),
            shape = longArrayOf(1)
        )
        val originalImageSizeTensor = createTensor(
            values = floatArrayOf(imageHeight.toFloat(), imageWidth.toFloat()),
            shape = longArrayOf(2)
        )

        return try {
            decoderSession.run(
                mapOf(
                    bundleInfo.onnx.maskRefiner.decoderEmbeddingInputName to embeddingTensor,
                    bundleInfo.onnx.maskRefiner.decoderPointCoordsInputName to pointCoordsTensor,
                    bundleInfo.onnx.maskRefiner.decoderPointLabelsInputName to pointLabelsTensor,
                    bundleInfo.onnx.maskRefiner.decoderMaskInputName to emptyMaskTensor,
                    bundleInfo.onnx.maskRefiner.decoderHasMaskInputName to hasMaskInputTensor,
                    bundleInfo.onnx.maskRefiner.decoderOrigImSizeInputName to originalImageSizeTensor
                )
            ).useResult { decoderResult ->
                val maskTensor = decoderResult.findTensor(bundleInfo.onnx.maskRefiner.decoderOutputName)
                    ?: throw OverlayModelExecutionException(
                        "Overlay model bundle failed during detection. Download the bundle again and retry."
                    )
                val scoreValues = decoderResult.findTensor(bundleInfo.onnx.maskRefiner.decoderScoreOutputName)
                    ?.readFloatArray()
                    ?: floatArrayOf()
                val bestMask = selectBestMaskSlice(
                    values = maskTensor.readFloatArray(),
                    shape = (maskTensor.info as TensorInfo).shape,
                    scoreValues = scoreValues
                )
                OverlayOnnxPostProcessor.decodeMask(
                    values = bestMask.values,
                    width = bestMask.width,
                    height = bestMask.height,
                    threshold = bundleInfo.onnx.maskRefiner.maskThreshold,
                    minRegionAreaRatio = bundleInfo.onnx.detector.minRegionAreaRatio
                ).map { region ->
                    region.copy(kind = squareRegion.kind)
                }
            }
        } finally {
            pointCoordsTensor.close()
            pointLabelsTensor.close()
            emptyMaskTensor.close()
            hasMaskInputTensor.close()
            originalImageSizeTensor.close()
        }
    }

    private fun createImageTensor(
        bitmap: Bitmap,
        tensorRange: OverlayTensorRange
    ): OnnxTensor {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val data = FloatArray(3 * width * height)
        val channelSize = width * height
        pixels.forEachIndexed { index, color ->
            data[index] = normalizeColor(Color.red(color), tensorRange)
            data[channelSize + index] = normalizeColor(Color.green(color), tensorRange)
            data[(channelSize * 2) + index] = normalizeColor(Color.blue(color), tensorRange)
        }
        return OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(data),
            longArrayOf(1, 3, height.toLong(), width.toLong())
        )
    }

    private fun createMaskTensor(
        mask: FloatArray,
        width: Int,
        height: Int
    ): OnnxTensor {
        return OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(mask),
            longArrayOf(1, 1, height.toLong(), width.toLong())
        )
    }

    private fun createConcatenatedImageMaskTensor(
        bitmap: Bitmap,
        mask: FloatArray,
        tensorRange: OverlayTensorRange
    ): OnnxTensor {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val data = FloatArray(4 * width * height)
        val channelSize = width * height
        pixels.forEachIndexed { index, color ->
            data[index] = normalizeColor(Color.red(color), tensorRange)
            data[channelSize + index] = normalizeColor(Color.green(color), tensorRange)
            data[(channelSize * 2) + index] = normalizeColor(Color.blue(color), tensorRange)
            data[(channelSize * 3) + index] = mask[index]
        }
        return OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(data),
            longArrayOf(1, 4, height.toLong(), width.toLong())
        )
    }

    private fun createTensor(
        values: FloatArray,
        shape: LongArray
    ): OnnxTensor {
        return OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(values),
            shape
        )
    }

    private fun tensorToBitmap(
        tensor: OnnxTensor,
        width: Int,
        height: Int,
        tensorRange: OverlayTensorRange
    ): Bitmap {
        val shape = (tensor.info as TensorInfo).shape
        val values = tensor.readFloatArray()
        val channelCount = shape.getOrNull(shape.size - 3)?.toInt() ?: 3
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        val planeSize = width * height
        for (index in pixels.indices) {
            val red = denormalizeColor(values.getOrElse(index) { 0f }, tensorRange)
            val green = denormalizeColor(values.getOrElse(planeSize + index) { values.getOrElse(index) { 0f } }, tensorRange)
            val blueIndex = if (channelCount >= 3) (planeSize * 2) + index else planeSize + index
            val blue = denormalizeColor(values.getOrElse(blueIndex) { values.getOrElse(index) { 0f } }, tensorRange)
            pixels[index] = Color.argb(255, red, green, blue)
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun normalizeColor(value: Int, tensorRange: OverlayTensorRange): Float {
        val normalized = value.toFloat() / 255f
        return when (tensorRange) {
            OverlayTensorRange.ZERO_TO_ONE -> normalized
            OverlayTensorRange.NEGATIVE_ONE_TO_ONE -> (normalized * 2f) - 1f
        }
    }

    private fun denormalizeColor(value: Float, tensorRange: OverlayTensorRange): Int {
        val normalized = when (tensorRange) {
            OverlayTensorRange.ZERO_TO_ONE -> value
            OverlayTensorRange.NEGATIVE_ONE_TO_ONE -> (value + 1f) / 2f
        }
        return (normalized.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
    }

    private fun OnnxTensor.readFloatArray(): FloatArray {
        return runCatching {
            val buffer = getFloatBuffer()
            val duplicate = buffer.duplicate()
            val result = FloatArray(duplicate.remaining())
            duplicate.get(result)
            result
        }.getOrElse {
            flattenFloatValues(value)
        }
    }

    private fun flattenFloatValues(value: Any?): FloatArray {
        val collected = mutableListOf<Float>()
        fun collect(next: Any?) {
            when (next) {
                null -> Unit
                is FloatArray -> next.forEach(collected::add)
                is Array<*> -> next.forEach(::collect)
                is Number -> collected += next.toFloat()
            }
        }
        collect(value)
        return collected.toFloatArray()
    }

    private fun OrtSession.Result.findTensor(preferredName: String): OnnxTensor? {
        return get(preferredName).orElse(null) as? OnnxTensor
            ?: get(0) as? OnnxTensor
    }

    private inline fun <T> OrtSession.Result.useResult(block: (OrtSession.Result) -> T): T {
        return try {
            block(this)
        } finally {
            close()
        }
    }

    private fun resolveMaskInputShape(
        decoderSession: OrtSession,
        bundleInfo: OverlayModelBundleInfo
    ): IntArray {
        val tensorInfo = decoderSession.getInputInfo()[bundleInfo.onnx.maskRefiner.decoderMaskInputName]
            ?.info as? TensorInfo
        val shape = tensorInfo?.shape?.map { dimension ->
            when {
                dimension <= 0L -> 1
                dimension > Int.MAX_VALUE -> 1
                else -> dimension.toInt()
            }
        } ?: listOf(1, 1, 256, 256)
        return shape.toIntArray()
    }

    private fun selectBestMaskSlice(
        values: FloatArray,
        shape: LongArray,
        scoreValues: FloatArray
    ): SelectedMaskSlice {
        val maskCount = when {
            shape.size >= 4 -> shape[shape.size - 3].toInt().coerceAtLeast(1)
            shape.size == 3 -> shape[0].toInt().coerceAtLeast(1)
            else -> 1
        }
        val maskHeight = shape.getOrNull(shape.size - 2)?.toInt()?.coerceAtLeast(1) ?: 1
        val maskWidth = shape.getOrNull(shape.size - 1)?.toInt()?.coerceAtLeast(1) ?: 1
        val maskSize = maskWidth * maskHeight
        val bestIndex = scoreValues.indices.maxByOrNull { scoreValues[it] } ?: 0
        val safeIndex = bestIndex.coerceIn(0, maskCount - 1)
        val start = safeIndex * maskSize
        val end = min(values.size, start + maskSize)
        return SelectedMaskSlice(
            values = values.copyOfRange(start, end),
            width = maskWidth,
            height = maskHeight
        )
    }

    private fun prepareSquareBitmap(
        bitmap: Bitmap,
        targetSize: Int
    ): PreparedSquareBitmap {
        val safeTarget = targetSize.coerceAtLeast(32)
        val scale = min(
            safeTarget.toFloat() / bitmap.width.toFloat(),
            safeTarget.toFloat() / bitmap.height.toFloat()
        )
        val scaledWidth = max(1, (bitmap.width * scale).roundToInt())
        val scaledHeight = max(1, (bitmap.height * scale).roundToInt())
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        val squareBitmap = Bitmap.createBitmap(safeTarget, safeTarget, Bitmap.Config.ARGB_8888)
        Canvas(squareBitmap).drawBitmap(
            scaledBitmap,
            ((safeTarget - scaledWidth) / 2f),
            ((safeTarget - scaledHeight) / 2f),
            null
        )
        scaledBitmap.recycle()
        return PreparedSquareBitmap(
            squareBitmap = squareBitmap,
            originalWidth = bitmap.width,
            originalHeight = bitmap.height,
            contentWidth = scaledWidth,
            contentHeight = scaledHeight,
            offsetX = (safeTarget - scaledWidth) / 2,
            offsetY = (safeTarget - scaledHeight) / 2
        )
    }

    private data class PreparedSquareBitmap(
        val squareBitmap: Bitmap,
        val originalWidth: Int,
        val originalHeight: Int,
        val contentWidth: Int,
        val contentHeight: Int,
        val offsetX: Int,
        val offsetY: Int
    ) {
        fun restoreRegions(regions: List<OverlayRegion>): List<OverlayRegion> {
            if (regions.isEmpty()) return emptyList()
            return regions.map { region ->
                OverlayRegion(
                    left = ((region.left * squareBitmap.width) - offsetX).toNormalized(contentWidth),
                    top = ((region.top * squareBitmap.height) - offsetY).toNormalized(contentHeight),
                    right = ((region.right * squareBitmap.width) - offsetX).toNormalized(contentWidth),
                    bottom = ((region.bottom * squareBitmap.height) - offsetY).toNormalized(contentHeight),
                    confidence = region.confidence,
                    kind = region.kind
                )
            }.filter { it.right > it.left && it.bottom > it.top }
        }

        fun projectRegions(regions: List<OverlayRegion>): List<OverlayRegion> {
            if (regions.isEmpty()) return emptyList()
            return regions.map { region ->
                OverlayRegion(
                    left = ((region.left * contentWidth) + offsetX).toSquareNormalized(squareBitmap.width),
                    top = ((region.top * contentHeight) + offsetY).toSquareNormalized(squareBitmap.height),
                    right = ((region.right * contentWidth) + offsetX).toSquareNormalized(squareBitmap.width),
                    bottom = ((region.bottom * contentHeight) + offsetY).toSquareNormalized(squareBitmap.height),
                    confidence = region.confidence,
                    kind = region.kind
                )
            }
        }

        fun restoreBitmap(squareOutput: Bitmap): Bitmap {
            val cropped = Bitmap.createBitmap(
                squareOutput,
                offsetX.coerceIn(0, squareOutput.width - 1),
                offsetY.coerceIn(0, squareOutput.height - 1),
                contentWidth.coerceIn(1, squareOutput.width - offsetX),
                contentHeight.coerceIn(1, squareOutput.height - offsetY)
            )
            if (cropped.width == originalWidth && cropped.height == originalHeight) {
                return cropped
            }
            val restored = Bitmap.createScaledBitmap(cropped, originalWidth, originalHeight, true)
            cropped.recycle()
            return restored
        }

        private fun Float.toNormalized(contentSize: Int): Float {
            if (contentSize <= 0) return 0f
            return (this / contentSize.toFloat()).coerceIn(0f, 1f)
        }

        private fun Float.toSquareNormalized(squareSize: Int): Float {
            if (squareSize <= 0) return 0f
            return (this / squareSize.toFloat()).coerceIn(0f, 1f)
        }
    }

    private data class SelectedMaskSlice(
        val values: FloatArray,
        val width: Int,
        val height: Int
    )
}

internal data class OverlayOnnxSessionCacheKey(
    val absolutePath: String,
    val fileLength: Long,
    val lastModifiedAt: Long
)

internal fun File.toSessionCacheKey(): OverlayOnnxSessionCacheKey {
    return OverlayOnnxSessionCacheKey(
        absolutePath = absolutePath,
        fileLength = length(),
        lastModifiedAt = lastModified()
    )
}

internal data class MobileSamBoxPrompt(
    val pointCoords: FloatArray,
    val pointLabels: FloatArray
)

internal fun buildMobileSamBoxPrompt(
    region: OverlayRegion,
    width: Int,
    height: Int
): MobileSamBoxPrompt {
    val left = region.left.coerceIn(0f, 1f) * width.toFloat()
    val top = region.top.coerceIn(0f, 1f) * height.toFloat()
    val right = region.right.coerceIn(0f, 1f) * width.toFloat()
    val bottom = region.bottom.coerceIn(0f, 1f) * height.toFloat()
    return MobileSamBoxPrompt(
        pointCoords = floatArrayOf(left, top, right, bottom),
        pointLabels = floatArrayOf(2f, 3f)
    )
}

private fun OrtSession.closeQuietly() {
    runCatching { close() }
}
