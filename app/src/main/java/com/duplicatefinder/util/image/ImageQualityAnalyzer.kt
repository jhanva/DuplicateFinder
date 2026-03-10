package com.duplicatefinder.util.image

import android.graphics.Bitmap
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.ImageQualityMetrics
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

@Singleton
class ImageQualityAnalyzer @Inject constructor(
    private val imageProcessor: ImageProcessor
) {

    suspend fun analyze(image: ImageItem): ImageQualityMetrics? {
        val bitmap = imageProcessor.loadThumbnail(image.uri, THUMBNAIL_SIZE) ?: return null
        return try {
            val width = bitmap.width
            val height = bitmap.height
            if (width < MIN_ANALYSIS_SIZE || height < MIN_ANALYSIS_SIZE) return null

            val grayscale = bitmap.toGrayscale()
            val sharpness = calculateSharpness(grayscale, width, height)
            val detailDensity = calculateDetailDensity(grayscale, width, height)
            val blockiness = calculateBlockiness(grayscale, width, height)

            ImageQualityMetrics(
                sharpness = sharpness,
                detailDensity = detailDensity,
                blockiness = blockiness
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun Bitmap.toGrayscale(): FloatArray {
        val width = this.width
        val height = this.height
        val pixels = IntArray(width * height)
        val grayscale = FloatArray(width * height)

        getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            grayscale[i] = (0.299f * r) + (0.587f * g) + (0.114f * b)
        }
        return grayscale
    }

    private fun calculateSharpness(
        gray: FloatArray,
        width: Int,
        height: Int
    ): Float {
        var sum = 0.0
        var sumSquares = 0.0
        var count = 0

        for (y in 1 until (height - 1)) {
            val row = y * width
            val rowUp = (y - 1) * width
            val rowDown = (y + 1) * width
            for (x in 1 until (width - 1)) {
                val idx = row + x
                val laplacian = (4f * gray[idx]) -
                    gray[idx - 1] -
                    gray[idx + 1] -
                    gray[rowUp + x] -
                    gray[rowDown + x]

                val value = laplacian.toDouble()
                sum += value
                sumSquares += value * value
                count++
            }
        }

        if (count == 0) return 0f

        val mean = sum / count
        val variance = (sumSquares / count) - (mean * mean)
        return (variance / (variance + SHARPNESS_NORMALIZATION)).toFloat().coerceIn(0f, 1f)
    }

    private fun calculateDetailDensity(
        gray: FloatArray,
        width: Int,
        height: Int
    ): Float {
        var sumMagnitude = 0.0
        var count = 0

        for (y in 1 until (height - 1)) {
            for (x in 1 until (width - 1)) {
                val topLeft = gray[(y - 1) * width + (x - 1)]
                val top = gray[(y - 1) * width + x]
                val topRight = gray[(y - 1) * width + (x + 1)]
                val left = gray[y * width + (x - 1)]
                val right = gray[y * width + (x + 1)]
                val bottomLeft = gray[(y + 1) * width + (x - 1)]
                val bottom = gray[(y + 1) * width + x]
                val bottomRight = gray[(y + 1) * width + (x + 1)]

                val gx = (-topLeft + topRight) +
                    (-2f * left + 2f * right) +
                    (-bottomLeft + bottomRight)
                val gy = (topLeft + 2f * top + topRight) -
                    (bottomLeft + 2f * bottom + bottomRight)

                sumMagnitude += sqrt((gx * gx + gy * gy).toDouble())
                count++
            }
        }

        if (count == 0) return 0f
        val averageMagnitude = (sumMagnitude / count).toFloat()
        return (averageMagnitude / DETAIL_NORMALIZATION).coerceIn(0f, 1f)
    }

    private fun calculateBlockiness(
        gray: FloatArray,
        width: Int,
        height: Int
    ): Float {
        var boundaryDiff = 0.0
        var boundaryCount = 0
        var interiorDiff = 0.0
        var interiorCount = 0

        for (x in 1 until width) {
            val isBoundary = x % BLOCK_SIZE == 0
            for (y in 0 until height) {
                val idx = y * width + x
                val diff = abs(gray[idx] - gray[idx - 1]).toDouble()
                if (isBoundary) {
                    boundaryDiff += diff
                    boundaryCount++
                } else {
                    interiorDiff += diff
                    interiorCount++
                }
            }
        }

        for (y in 1 until height) {
            val isBoundary = y % BLOCK_SIZE == 0
            val row = y * width
            val rowUp = (y - 1) * width
            for (x in 0 until width) {
                val diff = abs(gray[row + x] - gray[rowUp + x]).toDouble()
                if (isBoundary) {
                    boundaryDiff += diff
                    boundaryCount++
                } else {
                    interiorDiff += diff
                    interiorCount++
                }
            }
        }

        if (boundaryCount == 0 || interiorCount == 0) return 0f

        val boundaryAvg = boundaryDiff / boundaryCount
        val interiorAvg = interiorDiff / interiorCount
        if (interiorAvg <= 0.0) return 0f

        val ratio = (boundaryAvg / interiorAvg).toFloat()
        return ((ratio - 1f) / BLOCKINESS_NORMALIZATION).coerceIn(0f, 1f)
    }

    companion object {
        private const val THUMBNAIL_SIZE = 256
        private const val MIN_ANALYSIS_SIZE = 16
        private const val SHARPNESS_NORMALIZATION = 600f
        private const val DETAIL_NORMALIZATION = 80f
        private const val BLOCK_SIZE = 8
        private const val BLOCKINESS_NORMALIZATION = 1.5f
    }
}

