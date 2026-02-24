package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.model.ImageHashUpdate
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.ScanMode
import com.duplicatefinder.domain.model.ScanPhase
import com.duplicatefinder.domain.model.ScanProgress
import com.duplicatefinder.domain.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

class ScanImagesUseCase @Inject constructor(
    private val imageRepository: ImageRepository
) {
    operator fun invoke(
        scanMode: ScanMode,
        folders: Set<String> = emptySet()
    ): Flow<Pair<ScanProgress, List<ImageItem>>> = channelFlow<Pair<ScanProgress, List<ImageItem>>> {
        send(ScanProgress(ScanPhase.LOADING, 0, 0) to emptyList<ImageItem>())

        val total = imageRepository.getImageCount(folders)
        val computeSimilar = scanMode == ScanMode.EXACT_AND_SIMILAR

        send(ScanProgress(ScanPhase.HASHING, 0, total) to emptyList<ImageItem>())

        if (total == 0) {
            send(ScanProgress(ScanPhase.COMPLETE, 0, 0) to emptyList<ImageItem>())
            return@channelFlow
        }

        val sizeCounts = mutableMapOf<Long, Int>()
        var offset = 0
        while (offset < total) {
            val batch = imageRepository.getImagesBatch(
                folders = folders,
                limit = BATCH_SIZE,
                offset = offset
            )
            if (batch.isEmpty()) break

            batch.forEach { image ->
                sizeCounts[image.size] = (sizeCounts[image.size] ?: 0) + 1
            }
            offset += batch.size
        }

        val finalImages = mutableListOf<ImageItem>()
        val completed = AtomicInteger(0)
        offset = 0
        while (offset < total) {
            val batch = imageRepository.getImagesBatch(
                folders = folders,
                limit = BATCH_SIZE,
                offset = offset
            )
            if (batch.isEmpty()) break

            val cachedHashes = imageRepository.getCachedHashes(batch.map { it.id })
            val hashedBatch = arrayOfNulls<ImageItem>(batch.size)
            val hashUpdates = arrayOfNulls<ImageHashUpdate>(batch.size)
            val workChannel = Channel<Int>(Channel.BUFFERED)

            val workers = List(HASH_PARALLELISM) {
                launch {
                    for (index in workChannel) {
                        val image = batch[index]

                        val cachedHash = cachedHashes[image.id]
                        val cacheValid = cachedHash != null &&
                            cachedHash.dateModified == image.dateModified &&
                            cachedHash.size == image.size

                        val shouldComputeMd5 = (sizeCounts[image.size] ?: 0) > 1

                        val md5 = if (cacheValid && cachedHash!!.md5Hash != null) {
                            cachedHash.md5Hash
                        } else if (shouldComputeMd5) {
                            imageRepository.calculateMd5Hash(image)
                        } else {
                            null
                        }

                        val pHash = if (computeSimilar) {
                            if (cacheValid && cachedHash!!.perceptualHash != null) {
                                cachedHash.perceptualHash
                            } else {
                                imageRepository.calculatePerceptualHash(image)
                            }
                        } else {
                            null
                        }

                        if (md5 != null || pHash != null) {
                            val shouldSave = !cacheValid ||
                                (computeSimilar && cachedHash!!.perceptualHash == null && pHash != null) ||
                                (shouldComputeMd5 && cachedHash!!.md5Hash == null && md5 != null)

                            if (shouldSave) {
                                hashUpdates[index] = ImageHashUpdate(
                                    image = image,
                                    md5Hash = md5,
                                    perceptualHash = pHash
                                )
                            }
                        }

                        hashedBatch[index] = image.copy(
                            md5Hash = md5,
                            perceptualHash = pHash
                        )

                        val done = completed.incrementAndGet()
                        if (done % PROGRESS_STEP == 0 || done == total) {
                            trySend(
                                ScanProgress(
                                    phase = ScanPhase.HASHING,
                                    current = done,
                                    total = total,
                                    currentFile = image.name
                                ) to emptyList<ImageItem>()
                            )
                        }
                    }
                }
            }

            batch.indices.forEach { workChannel.send(it) }
            workChannel.close()
            workers.joinAll()

            val updatesToSave = hashUpdates.filterNotNull()
            if (updatesToSave.isNotEmpty()) {
                imageRepository.saveHashes(updatesToSave)
            }

            val filteredBatch = hashedBatch.filterNotNull().let { items ->
                if (computeSimilar) items else items.filter { it.md5Hash != null }
            }
            finalImages.addAll(filteredBatch)
            offset += batch.size
        }

        send(
            ScanProgress(ScanPhase.COMPLETE, total, total) to finalImages.toList()
        )
    }.flowOn(Dispatchers.Default)

    companion object {
        private const val PROGRESS_STEP = 20
        private const val HASH_PARALLELISM = 4
        private const val BATCH_SIZE = 500
    }
}
