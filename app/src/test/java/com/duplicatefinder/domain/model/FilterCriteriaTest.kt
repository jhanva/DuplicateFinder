package com.duplicatefinder.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterCriteriaTest {

    @Test
    fun `empty criteria has no active filters`() {
        val criteria = FilterCriteria.empty()

        assertFalse(criteria.hasActiveFilters)
    }

    @Test
    fun `folder filter marks criteria as active`() {
        val criteria = FilterCriteria(folders = listOf("Camera"))

        assertTrue(criteria.hasActiveFilters)
    }

    @Test
    fun `match type subset marks criteria as active`() {
        val criteria = FilterCriteria(matchTypes = listOf(MatchType.EXACT))

        assertTrue(criteria.hasActiveFilters)
    }
}
