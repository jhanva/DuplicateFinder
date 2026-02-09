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

@Singleton
class DifferenceHashCalculator @Inject constructor(
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
                Bitmap.createScaledBitmap(bitmap, HASH_WIDTH, HASH_HEIGHT, true).also {
                    if (it != bitmap) bitmap.recycle()
                }
            }
        }
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        val targetSize = HASH_WIDTH * 4

        while (width / sampleSize > targetSize && height / sampleSize > targetSize) {
            sampleSize *= 2
        }

        return sampleSize
    }

    private fun calculateHash(bitmap: Bitmap): String {
        return buildString {
            for (y in 0 until HASH_HEIGHT) {
                for (x in 0 until HASH_HEIGHT) {
                    val leftPixel = bitmap.getPixel(x, y)
                    val rightPixel = bitmap.getPixel(x + 1, y)

                    val leftGray = toGrayscale(leftPixel)
                    val rightGray = toGrayscale(rightPixel)

                    append(if (leftGray > rightGray) '1' else '0')
                }
            }
        }
    }

    private fun toGrayscale(pixel: Int): Double {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return 0.299 * r + 0.587 * g + 0.114 * b
    }

    companion object {
        private const val HASH_WIDTH = 9
        private const val HASH_HEIGHT = 8

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
