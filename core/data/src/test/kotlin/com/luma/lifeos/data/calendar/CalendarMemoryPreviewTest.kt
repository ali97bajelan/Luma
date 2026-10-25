package com.luma.lifeos.data.calendar

import com.luma.lifeos.data.repository.calendarMemoryPreview
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarMemoryPreviewTest {
    @Test fun restoredWhitespaceOnlyMarkerCannotHideSavedJournalContent() {
        assertEquals("Saved journal body", calendarMemoryPreview("\n\t\u00a0\u2003", "Saved journal body", null, null))
        assertEquals("Mood note", calendarMemoryPreview("\t", null, "Mood note", "Event note"))
    }

    @Test fun savedMarkerNoteTakesPrecedenceAndCollapsesUnicodeWhitespace() {
        assertEquals("Why it matters", calendarMemoryPreview("  Why\u2003it\nmatters  ", "Journal body", "Mood note", "Event note"))
        assertEquals("Event note", calendarMemoryPreview(null, null, "\u00a0", "Event note"))
        assertEquals("", calendarMemoryPreview(null, "\n", "\t", null))
    }

    @Test fun previewLimitCountsUnicodeCodePointsWithoutSplittingEmoji() {
        assertEquals("😀".repeat(160), calendarMemoryPreview("😀".repeat(170), null, null, null))
    }
}
