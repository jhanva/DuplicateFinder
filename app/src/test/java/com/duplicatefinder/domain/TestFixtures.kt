package com.duplicatefinder.domain

import android.net.Uri
import com.duplicatefinder.domain.model.CachedImageHashes
import com.duplicatefinder.domain.model.CachedImageQuality
import com.duplicatefinder.domain.model.DuplicateGroup
import com.duplicatefinder.domain.model.FilterCriteria
import com.duplicatefinder.domain.model.ImageHashUpdate
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.CleaningPreview
import com.duplicatefinder.domain.model.DetectionStage
import com.duplicatefinder.domain.model.ImageQualityMetrics
import com.duplicatefinder.domain.model.ImageQualityUpdate
import com.duplicatefinder.domain.model.OverlayDetection
import com.duplicatefinder.domain.model.OverlayKind
import com.duplicatefinder.domain.model.OverlayPreviewDecision
import com.duplicatefinder.domain.model.OverlayRegion
import com.duplicatefinder.domain.model.PreviewStatus
import com.duplicatefinder.domain.model.ScanMode
import com.duplicatefinder.domain.model.ScanProgress
import com.duplicatefinder.domain.model.TrashItem
import com.duplicatefinder.domain.repository.ImageRepository
import com.duplicatefinder.domain.repository.OverlayCleaningRepository
import com.duplicatefinder.domain.repository.OverlayModelBundleInfo
import com.duplicatefinder.domain.repository.OverlayModelBundleRepository
import com.duplicatefinder.domain.repository.OverlayRepository
import com.duplicatefinder.domain.repository.QualityRepository
import com.duplicatefinder.domain.repository.SettingsRepository
import com.duplicatefinder.domain.repository.TrashRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.mockito.Mockito

fun testImage(
    id: Long,
    size: Long,
    dateModified: Long = id,
    name: String = "image_$id.jpg"
): ImageItem = ImageItem(
    id = id,
    uri = Mockito.mock(Uri::class.java),
    path = "/storage/$name",
    name = name,
    size = size,
    dateModified = dateModified,
    mimeType = "image/jpeg",
    width = 100,
    height = 100
)

fun testOverlayDetection(
    image: ImageItem,
    score: Float = 0.9f,
    modelVersion: String = "test-model-v1"
): OverlayDetection = OverlayDetection(
    image = image,
    preliminaryScore = score,
    refinedScore = score,
    overlayCoverageRatio = 0.1f,
    maskBounds = listOf(
        OverlayRegion(
            left = 0.1f,
            top = 0.1f,
            right = 0.3f,
            bottom = 0.2f,
            confidence = score,
            kind = OverlayKind.TEXT
        )
    ),
    maskConfidence = score,
    overlayKinds = setOf(OverlayKind.TEXT),
    stage = DetectionStage.STAGE_2_REFINED,
    modelVersion = modelVersion
)

fun testCleaningPreview(
    image: ImageItem,
    modelVersion: String = "test-model-v1"
): CleaningPreview = CleaningPreview(
    sourceImage = image,
    previewUri = Mockito.mock(Uri::class.java),
    modelVersion = modelVersion,
    generationTimeMs = 1200L,
    status = PreviewStatus.READY
)

open class BaseImageRepositoryFake : ImageRepository {
    override suspend fun getAllImages(folders: Set<String>): List<ImageItem> = emptyList()
    override suspend fun getImagesBatch(
        folders: Set<String>,
        limit: Int,
        offset: Int
    ): List<ImageItem> = emptyList()

    override fun scanImagesWithProgress(): Flow<ScanProgress> = flowOf()

    override suspend fun getImageById(id: Long): ImageItem? = null

    override suspend fun getImagesByIds(ids: List<Long>): List<ImageItem> = emptyList()

    override suspend fun calculateMd5Hash(image: ImageItem): String? = null

    override suspend fun calculatePerceptualHash(image: ImageItem): String? = null

    override suspend fun getCachedHashes(imageIds: List<Long>): Map<Long, CachedImageHashes> = emptyMap()

    override suspend fun saveHash(image: ImageItem, md5Hash: String?, perceptualHash: String?) = Unit

    override suspend fun saveHashes(updates: List<ImageHashUpdate>) = Unit

    override suspend fun findExactDuplicates(images: List<ImageItem>): List<DuplicateGroup> = emptyList()

    override suspend fun findSimilarImages(
        images: List<ImageItem>,
        threshold: Float
    ): List<DuplicateGroup> = emptyList()

    override suspend fun applyFilter(
        groups: List<DuplicateGroup>,
        criteria: FilterCriteria
    ): List<DuplicateGroup> = groups

    override suspend fun deleteImages(images: List<ImageItem>): Result<Int> = Result.success(0)

    override suspend fun getFolders(): List<String> = emptyList()

    override suspend fun getImageCount(folders: Set<String>): Int = 0
}

class FakeSettingsRepository(
    threshold: Float = 0.9f
) : SettingsRepository {
    private val similarityThresholdState = MutableStateFlow(threshold)
    private val autoDeleteDaysState = MutableStateFlow(30)
    private val darkModeState = MutableStateFlow(false)
    private val excludedFoldersState = MutableStateFlow(emptySet<String>())
    private val scanFoldersState = MutableStateFlow(emptySet<String>())
    private val lastScanTimestampState = MutableStateFlow(0L)
    private val lastDuplicateCountState = MutableStateFlow(0)
    private val lastPotentialSavingsState = MutableStateFlow(0L)
    private val scanModeState = MutableStateFlow(ScanMode.EXACT_AND_SIMILAR)

    override val similarityThreshold: Flow<Float> = similarityThresholdState
    override val autoDeleteDays: Flow<Int> = autoDeleteDaysState
    override val isDarkMode: Flow<Boolean> = darkModeState
    override val excludedFolders: Flow<Set<String>> = excludedFoldersState
    override val scanFolders: Flow<Set<String>> = scanFoldersState
    override val lastScanTimestamp: Flow<Long> = lastScanTimestampState
    override val lastDuplicateCount: Flow<Int> = lastDuplicateCountState
    override val lastPotentialSavings: Flow<Long> = lastPotentialSavingsState
    override val scanMode: Flow<ScanMode> = scanModeState

    override suspend fun setSimilarityThreshold(threshold: Float) {
        similarityThresholdState.value = threshold
    }

    override suspend fun setAutoDeleteDays(days: Int) {
        autoDeleteDaysState.value = days
    }

    override suspend fun setDarkMode(enabled: Boolean) {
        darkModeState.value = enabled
    }

    override suspend fun setExcludedFolders(folders: Set<String>) {
        excludedFoldersState.value = folders
    }

    override suspend fun setScanFolders(folders: Set<String>) {
        scanFoldersState.value = folders
    }

    override suspend fun setLastScanTimestamp(timestamp: Long) {
        lastScanTimestampState.value = timestamp
    }

    override suspend fun setLastScanSummary(
        timestamp: Long,
        duplicateCount: Int,
        potentialSavings: Long
    ) {
        lastScanTimestampState.value = timestamp
        lastDuplicateCountState.value = duplicateCount
        lastPotentialSavingsState.value = potentialSavings
    }

    override suspend fun setScanMode(mode: ScanMode) {
        scanModeState.value = mode
    }
}

open class BaseQualityRepositoryFake : QualityRepository {
    override suspend fun getCachedQualities(imageIds: List<Long>): Map<Long, CachedImageQuality> = emptyMap()
    override suspend fun saveQualityScores(updates: List<ImageQualityUpdate>) = Unit
    override suspend fun calculateQualityMetrics(image: ImageItem): ImageQualityMetrics? = null
}

open class BaseOverlayRepositoryFake : OverlayRepository {
    var detectCallCount: Int = 0

    override suspend fun getCachedDetections(
        imageIds: List<Long>,
        modelVersion: String
    ): Map<Long, OverlayDetection> = emptyMap()

    override suspend fun detectOverlayCandidates(
        images: List<ImageItem>,
        modelVersion: String
    ): List<OverlayDetection> {
        detectCallCount += 1
        return images.map { image -> testOverlayDetection(image = image, modelVersion = modelVersion) }
    }

    override suspend fun saveDetections(detections: List<OverlayDetection>) = Unit
}

open class BaseOverlayModelBundleRepositoryFake : OverlayModelBundleRepository {
    var activeBundleInfo: OverlayModelBundleInfo? = null
    var downloadResult: Result<OverlayModelBundleInfo> = Result.failure(
        IllegalStateException("Bundle download not configured")
    )
    var downloadCallCount: Int = 0

    override suspend fun getActiveBundleInfo(): OverlayModelBundleInfo? = activeBundleInfo

    override suspend fun ensureBundleAvailable(): OverlayModelBundleInfo? = activeBundleInfo

    override suspend fun downloadBundle(): Result<OverlayModelBundleInfo> {
        downloadCallCount += 1
        return downloadResult
    }
}

open class BaseOverlayCleaningRepositoryFake : OverlayCleaningRepository {
    var generatedPreview: CleaningPreview? = null
    var lastDecision: OverlayPreviewDecision? = null
    var generateCallCount: Int = 0
    var discardCallCount: Int = 0

    override suspend fun generatePreview(
        image: ImageItem,
        detection: OverlayDetection,
        bundleInfo: OverlayModelBundleInfo
    ): Result<CleaningPreview> {
        generateCallCount += 1
        return Result.success(
            generatedPreview ?: testCleaningPreview(
                image = image,
                modelVersion = bundleInfo.bundleVersion
            )
        )
    }

    override suspend fun applyDecision(
        image: ImageItem,
        preview: CleaningPreview,
        decision: OverlayPreviewDecision
    ): Result<Unit> {
        lastDecision = decision
        return Result.success(Unit)
    }

    override suspend fun discardPreview(preview: CleaningPreview): Result<Unit> {
        discardCallCount += 1
        return Result.success(Unit)
    }
}

open class BaseTrashRepositoryFake : TrashRepository {
    override fun getTrashItems(): Flow<List<TrashItem>> = flowOf(emptyList())

    override suspend fun getTrashItemById(id: Long): TrashItem? = null

    override suspend fun moveToTrash(images: List<ImageItem>): Result<Int> = Result.success(images.size)

    override suspend fun restoreFromTrash(items: List<TrashItem>): Result<Int> = Result.success(items.size)

    override suspend fun deletePermanently(items: List<TrashItem>): Result<Int> = Result.success(items.size)

    override suspend fun emptyTrash(): Result<Int> = Result.success(0)

    override suspend fun deleteExpiredItems(): Result<Int> = Result.success(0)

    override suspend fun getTrashSize(): Long = 0L

    override suspend fun getTrashItemCount(): Int = 0
}
