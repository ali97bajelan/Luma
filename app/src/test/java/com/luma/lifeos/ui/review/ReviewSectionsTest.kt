package com.luma.lifeos.ui.review

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewSectionsTest {
    @Test fun allReportSectionsAreAvailableAndHabitsDependOnTheRepository() {
        val alwaysAvailable = listOf("overview", "counts", "mood", "memories", "catalog", "usage", "activity")

        assertEquals(alwaysAvailable, reviewSections(includeHabits = false).map { it.key })
        assertEquals(alwaysAvailable + "habits", reviewSections(includeHabits = true).map { it.key })
    }
}
