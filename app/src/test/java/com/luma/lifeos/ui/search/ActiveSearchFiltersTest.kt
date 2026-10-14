package com.luma.lifeos.ui.search

import com.luma.lifeos.domain.search.SearchOption
import com.luma.lifeos.domain.search.SearchOptions
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveSearchFiltersTest {
    private val first = UUID(0, 1)
    private val second = UUID(0, 2)

    @Test fun projectionKeepsEverySelectedIdInStableCategoryOrder() {
        val input = SearchInput(
            from = LocalDate.parse("2026-10-12"),
            through = LocalDate.parse("2026-10-13"),
            tagIds = setOf(second, first),
            personIds = setOf(second, first),
            placeId = first,
        )
        val options = SearchOptions(
            tags = listOf(SearchOption(second, "Family"), SearchOption(first, "Work")),
            people = listOf(SearchOption(second, "Alex"), SearchOption(first, "Alex")),
            places = listOf(SearchOption(first, "Home")),
        )

        val filters = activeSearchFilters(input, options)

        assertEquals(
            listOf(
                SearchFilterKey(SearchFilterKind.FROM),
                SearchFilterKey(SearchFilterKind.THROUGH),
                SearchFilterKey(SearchFilterKind.TAG, first),
                SearchFilterKey(SearchFilterKind.TAG, second),
                SearchFilterKey(SearchFilterKind.PERSON, first),
                SearchFilterKey(SearchFilterKind.PERSON, second),
                SearchFilterKey(SearchFilterKind.PLACE, first),
            ),
            filters.map { it.key },
        )
        assertEquals("Work", filters[2].name)
        assertEquals("Family", filters[3].name)
        assertEquals("Home", filters.last().name)
    }

    @Test fun sameNamesUseDistinctKeys() {
        val options = SearchOptions(people = listOf(SearchOption(first, "Alex"), SearchOption(second, "Alex")))

        val filters = activeSearchFilters(SearchInput(personIds = setOf(first, second)), options)

        assertEquals(2, filters.size)
        assertEquals(setOf(first, second), filters.mapNotNull { it.key.id }.toSet())
        assertTrue(filters.all { it.duplicateName })
    }

    @Test fun missingOptionsKeepRemovableDescriptors() {
        val filters = activeSearchFilters(
            SearchInput(tagIds = setOf(first), personIds = setOf(second), placeId = first),
            SearchOptions(),
        )

        assertEquals(listOf(first, second, first), filters.map { it.key.id })
        assertTrue(filters.all { it.name == null })
        assertTrue(filters.all { it.key.id != null })
    }

    @Test fun activeFilterCountIncludesDateEndpointsAndCatalogSelections() {
        assertEquals(5, SearchInput(
            from = LocalDate.parse("2026-10-12"), through = LocalDate.parse("2026-10-13"),
            tagIds = setOf(first), personIds = setOf(second), placeId = first,
        ).activeFilterCount)
        assertEquals(0, SearchInput(text = "query").activeFilterCount)
    }
}
