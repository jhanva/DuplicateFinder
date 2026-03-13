package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.model.ResolutionReviewItem
import com.duplicatefinder.domain.model.ResolutionScanState
import com.duplicatefinder.domain.model.ScanPhase
import com.duplicatefinder.domain.model.ScanProgress
import com.duplicatefinder.domain.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class ScanResolutionImagesUseCase @Inject constructor(
    private val imageRepository: ImageRepository
) {

    operator fun invoke(
        folders: Set<String>
    ): Flow<ResolutionScanState> = flow {
        emit(
            ResolutionScanState(
                progress = ScanProgress(ScanPhase.LOADING, 0, 0),
                items = emptyList()
            )
        )

        val total = imageRepository.getImageCount(folders)
        emit(
            ResolutionScanState(
                progress = ScanProgress(ScanPhase.ANALYZING, 0, total),
                items = emptyList()
            )
        )

        if (total == 0) {
            emit(
                ResolutionScanState(
                    progress = ScanProgress(ScanPhase.COMPLETE, 0, 0),
                    items = emptyList()
                )
            )
            return@flow
        }

        val finalItems = mutableListOf<ResolutionReviewItem>()
        var offset = 0
        var processed = 0

        while (offset < total) {
            val batch = imageRepository.getImagesBatch(
                folders = folders,
                limit = BATCH_SIZE,
                offset = offset
            )
            if (batch.isEmpty()) break

            batch.forEach { image ->
                ResolutionReviewItem.from(image)?.let(finalItems::add)
                processed += 1

                if (processed % PROGRESS_STEP == 0 || processed == total) {
                    emit(
                        ResolutionScanState(
                            progress = ScanProgress(
                                phase = ScanPhase.ANALYZING,
                                current = processed,
                                total = total,
                                currentFile = image.name
                            ),
                            items = emptyList()
                        )
                    )
                }
            }

            offset += batch.size
        }

        emit(
            ResolutionScanState(
                progress = ScanProgress(ScanPhase.COMPLETE, processed, total),
                items = finalItems.sortedBy { it.pixelCount }
            )
        )
    }.flowOn(Dispatchers.Default)

    companion object {
        private const val BATCH_SIZE = 500
        private const val PROGRESS_STEP = 20
    }
}
