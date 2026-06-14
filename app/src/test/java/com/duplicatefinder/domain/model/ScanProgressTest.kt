package com.duplicatefinder.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanProgressTest {

    @Test
    fun `progress is the current over total ratio`() {
        val progress = ScanProgress(ScanPhase.HASHING, current = 25, total = 100)

        assertEquals(0.25f, progress.progress, 0f)
    }

    @Test
    fun `progress is zero when total is not known`() {
        val progress = ScanProgress(ScanPhase.LOADING, current = 0, total = 0)

        assertEquals(0f, progress.progress, 0f)
    }

    @Test
    fun `comparing phase is always indeterminate even with a full count`() {
        // DuplicatesViewModel copies the COMPLETE phase counts into COMPARING,
        // so current == total here; it must still render as indeterminate.
        val progress = ScanProgress(ScanPhase.COMPARING, current = 100, total = 100)

        assertTrue(progress.isIndeterminate)
    }

    @Test
    fun `comparing phase is indeterminate when count has not advanced`() {
        // ScanViewModel starts COMPARING at current = 0.
        val progress = ScanProgress(ScanPhase.COMPARING, current = 0, total = 100)

        assertTrue(progress.isIndeterminate)
    }

    @Test
    fun `hashing phase with a known total is determinate`() {
        val progress = ScanProgress(ScanPhase.HASHING, current = 10, total = 100)

        assertFalse(progress.isIndeterminate)
    }

    @Test
    fun `active phase without a known total is indeterminate`() {
        val progress = ScanProgress(ScanPhase.LOADING, current = 0, total = 0)

        assertTrue(progress.isIndeterminate)
    }

    @Test
    fun `terminal and idle phases are never indeterminate`() {
        assertFalse(ScanProgress(ScanPhase.IDLE, 0, 0).isIndeterminate)
        assertFalse(ScanProgress(ScanPhase.COMPLETE, 100, 100).isIndeterminate)
        assertFalse(ScanProgress(ScanPhase.ERROR, 0, 0).isIndeterminate)
    }

    @Test
    fun `isComplete reflects the complete phase`() {
        assertTrue(ScanProgress(ScanPhase.COMPLETE, 100, 100).isComplete)
        assertFalse(ScanProgress(ScanPhase.COMPARING, 100, 100).isComplete)
    }
}
