package com.duplicatefinder.util.image

import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.ImageQualityMetrics

object ImageQualityScorer {

    fun score(
        image: ImageItem,
        metrics: ImageQualityMetrics
    ): Float {
        val sharpness = metrics.sharpness.coerceIn(0f, 1f)
        val detailDensity = metrics.detailDensity.coerceIn(0f, 1f)
        val blockinessPenalty = metrics.blockiness.coerceIn(0f, 1f)

        val base = (sharpness * SHARPNESS_WEIGHT) +
            (detailDensity * DETAIL_WEIGHT) +
            ((1f - blockinessPenalty) * BLOCKINESS_WEIGHT)

        val megapixels = ((image.width.toLong() * image.height.toLong()).toFloat() / 1_000_000f)
            .coerceAtLeast(0f)
        val largeLowDetailPenalty = if (megapixels >= LARGE_IMAGE_THRESHOLD_MP &&
            detailDensity < LOW_DETAIL_THRESHOLD
        ) {
            val detailGap = LOW_DETAIL_THRESHOLD - detailDensity
            ((megapixels / LARGE_IMAGE_DIVISOR_MP) * detailGap * LOW_DETAIL_PENALTY_MULTIPLIER)
                .coerceAtMost(MAX_LOW_DETAIL_PENALTY)
        } else {
            0f
        }

        return ((base - largeLowDetailPenalty).coerceIn(0f, 1f) * 100f)
    }

    private const val SHARPNESS_WEIGHT = 0.55f
    private const val DETAIL_WEIGHT = 0.35f
    private const val BLOCKINESS_WEIGHT = 0.10f

    private const val LARGE_IMAGE_THRESHOLD_MP = 4f
    private const val LARGE_IMAGE_DIVISOR_MP = 12f
    private const val LOW_DETAIL_THRESHOLD = 0.25f
    private const val LOW_DETAIL_PENALTY_MULTIPLIER = 1.5f
    private const val MAX_LOW_DETAIL_PENALTY = 0.2f
}

