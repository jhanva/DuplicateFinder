package com.duplicatefinder.util.image

import com.duplicatefinder.domain.model.ImageQualityMetrics
import com.duplicatefinder.domain.testImage
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageQualityScorerTest {

    @Test
    fun `higher sharpness and detail produce higher quality score`() {
        val image = testImage(id = 1, size = 1024).copy(width = 3000, height = 2000)
        val low = ImageQualityScorer.score(
            image = image,
            metrics = ImageQualityMetrics(
                sharpness = 0.15f,
                detailDensity = 0.12f,
                blockiness = 0.4f
            )
        )
        val high = ImageQualityScorer.score(
            image = image,
            metrics = ImageQualityMetrics(
                sharpness = 0.85f,
                detailDensity = 0.78f,
                blockiness = 0.1f
            )
        )

        assertTrue(high > low)
    }

    @Test
    fun `high blockiness lowers score`() {
        val image = testImage(id = 2, size = 1024).copy(width = 1600, height = 1200)
        val lowBlockiness = ImageQualityScorer.score(
            image = image,
            metrics = ImageQualityMetrics(
                sharpness = 0.65f,
                detailDensity = 0.62f,
                blockiness = 0.05f
            )
        )
        val highBlockiness = ImageQualityScorer.score(
            image = image,
            metrics = ImageQualityMetrics(
                sharpness = 0.65f,
                detailDensity = 0.62f,
                blockiness = 0.8f
            )
        )

        assertTrue(lowBlockiness > highBlockiness)
    }

    @Test
    fun `large image with low detail is penalized`() {
        val image = testImage(id = 3, size = 1024).copy(width = 5000, height = 4000)
        val penalized = ImageQualityScorer.score(
            image = image,
            metrics = ImageQualityMetrics(
                sharpness = 0.4f,
                detailDensity = 0.1f,
                blockiness = 0.2f
            )
        )
        val lessPenalized = ImageQualityScorer.score(
            image = image,
            metrics = ImageQualityMetrics(
                sharpness = 0.4f,
                detailDensity = 0.35f,
                blockiness = 0.2f
            )
        )

        assertTrue(lessPenalized > penalized)
    }
}

