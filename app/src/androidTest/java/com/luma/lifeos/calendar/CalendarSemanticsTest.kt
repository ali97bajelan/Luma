package com.luma.lifeos.calendar

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luma.lifeos.MainActivity
import com.luma.lifeos.R
import com.luma.lifeos.language.localizedDate
import java.time.LocalDate
import java.time.format.FormatStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarSemanticsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun todayCellAnnouncesItsFullDateAndStateWithAReachableTarget() {
        compose.onNodeWithTag("nav_calendar").performClick()
        val today = LocalDate.now()
        val node = compose.onNodeWithTag("calendar_day_$today")
        node.assertIsSelected()
        val semantics = node.fetchSemanticsNode().config
        val description = semantics[SemanticsProperties.ContentDescription].joinToString(" ")
        assertTrue(description.contains(today.format(localizedDate(FormatStyle.FULL))))
        assertTrue(description.contains(compose.activity.getString(R.string.calendar_day_today_selected)))
        assertTrue(description.contains(compose.activity.resources.getQuantityString(R.plurals.calendar_tasks_count, 0, 0)))
        assertTrue(description.contains(compose.activity.resources.getQuantityString(R.plurals.calendar_memories_count, 0, 0)))

        val bounds = node.fetchSemanticsNode().boundsInRoot
        val minimumTarget = 48f * compose.activity.resources.displayMetrics.density
        assertTrue("Date target is narrower than 48 dp", bounds.width >= minimumTarget)
        assertTrue("Date target is shorter than 48 dp", bounds.height >= minimumTarget)
    }
}
