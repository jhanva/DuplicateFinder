package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.ScanPhase
import com.duplicatefinder.domain.model.ScanProgress
import com.duplicatefinder.domain.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class ScanImagesUseCase @Inject constructor(
    private val imageRepository: ImageRepository
) {
    operator fun invoke(): Flow<Pair<ScanProgress, List<ImageItem>>> = flow {
        emit(ScanProgress(ScanPhase.LOADING, 0, 0) to emptyList())

        val images = imageRepository.getAllImages()
        val total = images.size
        val cachedHashes = imageRepository.getCachedHashes(images.map { it.id })

        emit(ScanProgress(ScanPhase.HASHING, 0, total) to emptyList())

        val hashedImages = mutableListOf<ImageItem>()

        images.forEachIndexed { index, image ->
            val cachedHash = cachedHashes[image.id]

            val hashedImage = if (cachedHash != null &&
                cachedHash.dateModified == image.dateModified &&
                cachedHash.size == image.size
            ) {
                image.copy(
                    md5Hash = cachedHash.md5Hash,
                    perceptualHash = cachedHash.perceptualHash
                )
            } else {
                val md5 = imageRepository.calculateMd5Hash(image)
                val pHash = imageRepository.calculatePerceptualHash(image)

                if (md5 != null) {
                    imageRepository.saveHash(image, md5, pHash)
                }

                image.copy(md5Hash = md5, perceptualHash = pHash)
            }

            hashedImages.add(hashedImage)

            if (index % PROGRESS_STEP == 0 || index == total - 1) {
                emit(
                    ScanProgress(
                        phase = ScanPhase.HASHING,
                        current = index + 1,
                        total = total,
                        currentFile = image.name
                    ) to emptyList()
                )
            }
        }

        emit(ScanProgress(ScanPhase.COMPLETE, total, total) to hashedImages)
    }.flowOn(Dispatchers.Default)

    companion object {
        private const val PROGRESS_STEP = 20
    }
}
