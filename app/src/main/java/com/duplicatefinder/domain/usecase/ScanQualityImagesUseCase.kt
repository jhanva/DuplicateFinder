package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.model.ImageQualityItem
import com.duplicatefinder.domain.model.ImageQualityUpdate
import com.duplicatefinder.domain.model.QualityScanState
import com.duplicatefinder.domain.model.ScanPhase
import com.duplicatefinder.domain.model.ScanProgress
import com.duplicatefinder.domain.repository.ImageRepository
import com.duplicatefinder.domain.repository.QualityRepository
import com.duplicatefinder.util.image.ImageQualityScorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

class ScanQualityImagesUseCase @Inject constructor(
    private val imageRepository: ImageRepository,
    private val qualityRepository: QualityRepository
) {

    operator fun invoke(
        folders: Set<String>
    ): Flow<QualityScanState> = channelFlow {
        send(
            QualityScanState(
                progress = ScanProgress(ScanPhase.LOADING, 0, 0),
                items = emptyList()
            )
        )

        val total = imageRepository.getImageCount(folders)
        send(
            QualityScanState(
                progress = ScanProgress(ScanPhase.ANALYZING, 0, total),
                items = emptyList()
            )
        )

        if (total == 0) {
            send(
                QualityScanState(
                    progress = ScanProgress(ScanPhase.COMPLETE, 0, 0),
                    items = emptyList()
                )
            )
            return@channelFlow
        }

        val completed = AtomicInteger(0)
        val finalItems = mutableListOf<ImageQualityItem>()
        var offset = 0

        while (offset < total) {
            val batch = imageRepository.getImagesBatch(
                folders = folders,
                limit = BATCH_SIZE,
                offset = offset
            )
            if (batch.isEmpty()) break

            val cachedQuality = qualityRepository.getCachedQualities(batch.map { it.id })
            val processedItems = arrayOfNulls<ImageQualityItem>(batch.size)
            val updates = arrayOfNulls<ImageQualityUpdate>(batch.size)
            val workChannel = Channel<Int>(Channel.BUFFERED)

            val workers = List(parallelism()) {
                launch {
                    for (index in workChannel) {
                        val image = batch[index]
                        val cached = cachedQuality[image.id]
                        val cacheValid = cached != null &&
                            cached.dateModified == image.dateModified &&
                            cached.size == image.size

                        val validCached = if (cacheValid) cached else null
                        val item = if (validCached != null) {
                            ImageQualityItem(
                                image = image,
                                qualityScore = validCached.qualityScore,
                                sharpness = validCached.sharpness,
                                detailDensity = validCached.detailDensity,
                                blockiness = validCached.blockiness
                            )
                        } else {
                            val metrics = qualityRepository.calculateQualityMetrics(image)
                            if (metrics != null) {
                                val score = ImageQualityScorer.score(image, metrics)
                                updates[index] = ImageQualityUpdate(
                                    image = image,
                                    qualityScore = score,
                                    sharpness = metrics.sharpness,
                                    detailDensity = metrics.detailDensity,
                                    blockiness = metrics.blockiness
                                )
                                ImageQualityItem(
                                    image = image,
                                    qualityScore = score,
                                    sharpness = metrics.sharpness,
                                    detailDensity = metrics.detailDensity,
                                    blockiness = metrics.blockiness
                                )
                            } else {
                                null
                            }
                        }

                        processedItems[index] = item

                        val done = completed.incrementAndGet()
                        if (done % PROGRESS_STEP == 0 || done == total) {
                            trySend(
                                QualityScanState(
                                    progress = ScanProgress(
                                        phase = ScanPhase.ANALYZING,
                                        current = done,
                                        total = total,
                                        currentFile = image.name
                                    ),
                                    items = emptyList()
                                )
                            )
                        }
                    }
                }
            }

            batch.indices.forEach { workChannel.send(it) }
            workChannel.close()
            workers.joinAll()

            val updatesToSave = updates.filterNotNull()
            if (updatesToSave.isNotEmpty()) {
                qualityRepository.saveQualityScores(updatesToSave)
            }

            finalItems.addAll(processedItems.filterNotNull())
            offset += batch.size
        }

        val sorted = finalItems.sortedBy { it.qualityScore }
        send(
            QualityScanState(
                progress = ScanProgress(ScanPhase.COMPLETE, total, total),
                items = sorted
            )
        )
    }.flowOn(Dispatchers.Default)

    companion object {
        private const val BATCH_SIZE = 500
        private const val PROGRESS_STEP = 20

        private fun parallelism(): Int {
            val cores = Runtime.getRuntime().availableProcessors()
            return cores.coerceIn(2, 8)
        }
    }
}
