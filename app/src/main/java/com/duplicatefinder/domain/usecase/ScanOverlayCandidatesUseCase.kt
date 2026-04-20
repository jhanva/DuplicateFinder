package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.model.OverlayDetection
import com.duplicatefinder.domain.model.OverlayReviewItem
import com.duplicatefinder.domain.model.OverlayScanState
import com.duplicatefinder.domain.model.ScanPhase
import com.duplicatefinder.domain.model.ScanProgress
import com.duplicatefinder.domain.repository.ImageRepository
import com.duplicatefinder.domain.repository.OverlayRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class ScanOverlayCandidatesUseCase @Inject constructor(
    private val imageRepository: ImageRepository,
    private val overlayRepository: OverlayRepository
) {

    operator fun invoke(
        folders: Set<String>,
        modelVersion: String = DEFAULT_MODEL_VERSION,
        reviewThreshold: Float = DEFAULT_REVIEW_THRESHOLD
    ): Flow<OverlayScanState> = flow {
        emit(
            OverlayScanState(
                progress = ScanProgress(ScanPhase.LOADING, 0, 0),
                items = emptyList(),
                candidateCount = 0,
                scannedCount = 0
            )
        )

        val total = imageRepository.getImageCount(folders)
        emit(
            OverlayScanState(
                progress = ScanProgress(ScanPhase.ANALYZING, 0, total),
                items = emptyList(),
                candidateCount = 0,
                scannedCount = 0
            )
        )

        if (total == 0) {
            emit(
                OverlayScanState(
                    progress = ScanProgress(ScanPhase.COMPLETE, 0, 0),
                    items = emptyList(),
                    candidateCount = 0,
                    scannedCount = 0
                )
            )
            return@flow
        }

        val collectedDetections = mutableListOf<OverlayDetection>()
        var offset = 0

        while (offset < total) {
            val batch = imageRepository.getImagesBatch(
                folders = folders,
                limit = BATCH_SIZE,
                offset = offset
            )
            if (batch.isEmpty()) break

            val cached = overlayRepository.getCachedDetections(
                imageIds = batch.map { it.id },
                modelVersion = modelVersion
            )

            val missing = batch.filter { it.id !in cached }
            val freshDetections = overlayRepository.detectOverlayCandidates(
                images = missing,
                modelVersion = modelVersion
            )
            if (freshDetections.isNotEmpty()) {
                overlayRepository.saveDetections(freshDetections)
            }

            collectedDetections += cached.values
            collectedDetections += freshDetections

            val candidates = buildReviewItems(
                detections = collectedDetections,
                reviewThreshold = reviewThreshold
            )

            val scannedCount = (offset + batch.size).coerceAtMost(total)
            emit(
                OverlayScanState(
                    progress = ScanProgress(
                        phase = ScanPhase.ANALYZING,
                        current = scannedCount,
                        total = total
                    ),
                    items = candidates,
                    candidateCount = candidates.size,
                    scannedCount = scannedCount
                )
            )

            offset += batch.size
        }

        val finalItems = buildReviewItems(
            detections = collectedDetections,
            reviewThreshold = reviewThreshold
        )
        emit(
            OverlayScanState(
                progress = ScanProgress(ScanPhase.COMPLETE, total, total),
                items = finalItems,
                candidateCount = finalItems.size,
                scannedCount = total
            )
        )
    }.flowOn(Dispatchers.IO)

    private fun buildReviewItems(
        detections: List<OverlayDetection>,
        reviewThreshold: Float
    ): List<OverlayReviewItem> {
        return detections
            .asSequence()
            .filter { it.refinedScore >= reviewThreshold }
            .map { detection ->
                OverlayReviewItem(
                    image = detection.image,
                    detection = detection,
                    rankScore = detection.refinedScore
                )
            }
            .sortedWith(
                compareByDescending<OverlayReviewItem> { it.rankScore }
                    .thenByDescending { it.detection.overlayCoverageRatio }
                    .thenBy { it.image.id }
            )
            .toList()
    }

    companion object {
        private const val BATCH_SIZE = 200
        private const val DEFAULT_REVIEW_THRESHOLD = 0.6f
        private const val DEFAULT_MODEL_VERSION = "overlay-bundle-v1"
    }
}
