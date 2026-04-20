package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.BaseOverlayCleaningRepositoryFake
import com.duplicatefinder.domain.testCleaningPreview
import com.duplicatefinder.domain.testImage
import com.duplicatefinder.domain.model.OverlayPreviewDecision
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplyOverlayPreviewDecisionUseCaseTest {

    @Test
    fun `keep cleaned replaces original via cleaning repository`() = runBlocking {
        val repository = BaseOverlayCleaningRepositoryFake()
        val image = testImage(id = 1, size = 100)

        ApplyOverlayPreviewDecisionUseCase(repository)(
            image = image,
            preview = testCleaningPreview(image),
            decision = OverlayPreviewDecision.KEEP_CLEANED_REPLACE_ORIGINAL
        )

        assertEquals(OverlayPreviewDecision.KEEP_CLEANED_REPLACE_ORIGINAL, repository.lastDecision)
    }

    @Test
    fun `delete all discards preview and moves original out of gallery`() = runBlocking {
        val repository = BaseOverlayCleaningRepositoryFake()
        val image = testImage(id = 1, size = 100)

        ApplyOverlayPreviewDecisionUseCase(repository)(
            image = image,
            preview = testCleaningPreview(image),
            decision = OverlayPreviewDecision.DELETE_ALL
        )

        assertEquals(OverlayPreviewDecision.DELETE_ALL, repository.lastDecision)
    }

    @Test
    fun `skip keeps original and discards preview`() = runBlocking {
        val repository = BaseOverlayCleaningRepositoryFake()
        val image = testImage(id = 1, size = 100)

        ApplyOverlayPreviewDecisionUseCase(repository)(
            image = image,
            preview = testCleaningPreview(image),
            decision = OverlayPreviewDecision.SKIP_KEEP_ORIGINAL
        )

        assertEquals(OverlayPreviewDecision.SKIP_KEEP_ORIGINAL, repository.lastDecision)
    }
}
