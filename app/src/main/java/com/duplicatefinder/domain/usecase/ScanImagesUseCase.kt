package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.model.ImageHashUpdate
import com.duplicatefinder.domain.model.ImageItem
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
    operator fun invoke(): Flow<Pair<ScanProgress, List<ImageItem>>> = channelFlow {
        send(ScanProgress(ScanPhase.LOADING, 0, 0) to emptyList())

        val images = imageRepository.getAllImages()
        val total = images.size
        val cachedHashes = imageRepository.getCachedHashes(images.map { it.id })
        val sizeCounts = images.groupingBy { it.size }.eachCount()

        send(ScanProgress(ScanPhase.HASHING, 0, total) to emptyList())

        if (total == 0) {
            send(ScanProgress(ScanPhase.COMPLETE, 0, 0) to emptyList())
            return@channelFlow
        }

        val hashedImages = arrayOfNulls<ImageItem>(total)
        val hashUpdates = arrayOfNulls<ImageHashUpdate>(total)
        val completed = AtomicInteger(0)
        val workChannel = Channel<Int>(Channel.BUFFERED)

        val workers = List(HASH_PARALLELISM) {
            launch {
                for (index in workChannel) {
                    val image = images[index]

                    val cachedHash = cachedHashes[image.id]
                    val cacheValid = cachedHash != null &&
                        cachedHash.dateModified == image.dateModified &&
                        cachedHash.size == image.size

                    val shouldComputeMd5 = (sizeCounts[image.size] ?: 0) > 1

                    val md5 = if (cacheValid) {
                        cachedHash!!.md5Hash
                    } else if (shouldComputeMd5) {
                        imageRepository.calculateMd5Hash(image)
                    } else {
                        null
                    }

                    val pHash = if (cacheValid && cachedHash!!.perceptualHash != null) {
                        cachedHash.perceptualHash
                    } else {
                        imageRepository.calculatePerceptualHash(image)
                    }

                    if (md5 != null) {
                        val shouldSave = !cacheValid ||
                            (cachedHash!!.perceptualHash == null && pHash != null)

                        if (shouldSave) {
                            hashUpdates[index] = ImageHashUpdate(
                                image = image,
                                md5Hash = md5,
                                perceptualHash = pHash
                            )
                        }
                    }

                    hashedImages[index] = image.copy(
                        md5Hash = md5,
                        perceptualHash = pHash
                    }

                    val done = completed.incrementAndGet()
                    if (done % PROGRESS_STEP == 0 || done == total) {
                        trySend(
                            ScanProgress(
                                phase = ScanPhase.HASHING,
                                current = done,
                                total = total,
                                currentFile = image.name
                            ) to emptyList()
                        )
                    }
                }
            }
        }

        images.indices.forEach { workChannel.send(it) }
        workChannel.close()
        workers.joinAll()

        val updatesToSave = hashUpdates.filterNotNull()
        if (updatesToSave.isNotEmpty()) {
            imageRepository.saveHashes(updatesToSave)
        }

        send(
            ScanProgress(ScanPhase.COMPLETE, total, total) to
                hashedImages.filterNotNull()
        )
    }.flowOn(Dispatchers.Default)

    companion object {
        private const val PROGRESS_STEP = 20
        private const val HASH_PARALLELISM = 4
    }
}
