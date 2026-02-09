package com.duplicatefinder.util.hash

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sqrt

@Singleton
class PerceptualHashCalculator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun calculate(uri: Uri): String? = withContext(Dispatchers.Default) {
        try {
            val bitmap = loadScaledBitmap(uri) ?: return@withContext null
            calculateHash(bitmap).also { bitmap.recycle() }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadScaledBitmap(uri: Uri): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        val sampleSize = calculateSampleSize(options.outWidth, options.outHeight)

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)?.let { bitmap ->
                Bitmap.createScaledBitmap(bitmap, HASH_SIZE, HASH_SIZE, true).also {
                    if (it != bitmap) bitmap.recycle()
                }
            }
        }
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        val targetSize = HASH_SIZE * 4

        while (width / sampleSize > targetSize && height / sampleSize > targetSize) {
            sampleSize *= 2
        }

        return sampleSize
    }

    private fun calculateHash(bitmap: Bitmap): String {
        val grayscale = toGrayscale(bitmap)
        val dct = applyDCT(grayscale)
        val average = calculateAverage(dct)

        return buildString {
            for (y in 0 until LOW_FREQ_SIZE) {
                for (x in 0 until LOW_FREQ_SIZE) {
                    if (!(x == 0 && y == 0)) {
                        append(if (dct[y][x] > average) '1' else '0')
                    }
                }
            }
        }
    }

    private fun toGrayscale(bitmap: Bitmap): Array<DoubleArray> {
        val result = Array(HASH_SIZE) { DoubleArray(HASH_SIZE) }

        for (y in 0 until HASH_SIZE) {
            for (x in 0 until HASH_SIZE) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                result[y][x] = 0.299 * r + 0.587 * g + 0.114 * b
            }
        }

        return result
    }

    private fun applyDCT(input: Array<DoubleArray>): Array<DoubleArray> {
        val size = input.size
        val result = Array(size) { DoubleArray(size) }
        val coefficient = sqrt(2.0 / size)

        for (u in 0 until size) {
            for (v in 0 until size) {
                var sum = 0.0

                for (i in 0 until size) {
                    for (j in 0 until size) {
                        sum += input[i][j] *
                                cos((2 * i + 1) * u * PI_OVER_2N) *
                                cos((2 * j + 1) * v * PI_OVER_2N)
                    }
                }

                val cu = if (u == 0) 1.0 / sqrt(2.0) else 1.0
                val cv = if (v == 0) 1.0 / sqrt(2.0) else 1.0

                result[u][v] = coefficient * coefficient * cu * cv * sum
            }
        }

        return result
    }

    private fun calculateAverage(dct: Array<DoubleArray>): Double {
        var sum = 0.0
        var count = 0

        for (y in 0 until LOW_FREQ_SIZE) {
            for (x in 0 until LOW_FREQ_SIZE) {
                if (!(x == 0 && y == 0)) {
                    sum += dct[y][x]
                    count++
                }
            }
        }

        return sum / count
    }

    companion object {
        private const val HASH_SIZE = 32
        private const val LOW_FREQ_SIZE = 8
        private val PI_OVER_2N = Math.PI / (2 * HASH_SIZE)

        fun hammingDistance(hash1: String, hash2: String): Int {
            if (hash1.length != hash2.length) return Int.MAX_VALUE

            return hash1.zip(hash2).count { (a, b) -> a != b }
        }

        fun similarity(hash1: String, hash2: String): Float {
            if (hash1.isEmpty() || hash2.isEmpty()) return 0f
            if (hash1.length != hash2.length) return 0f

            val distance = hammingDistance(hash1, hash2)
            return 1f - (distance.toFloat() / hash1.length)
        }
    }
}
