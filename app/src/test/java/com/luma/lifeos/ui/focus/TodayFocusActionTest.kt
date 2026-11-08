package com.luma.lifeos.ui.focus

import com.luma.lifeos.R
import com.luma.lifeos.domain.focus.FocusState
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayFocusActionTest {
    @Test
    fun idleActionStartsFocus() {
        assertEquals(R.string.today_focus_action, todayFocusActionLabel(null))
    }

    @Test
    fun runningActionReturnsToTimer() {
        assertEquals(R.string.today_focus_return_running, todayFocusActionLabel(FocusState.RUNNING))
    }

    @Test
    fun pausedActionReturnsToPausedSession() {
        assertEquals(R.string.today_focus_return_paused, todayFocusActionLabel(FocusState.PAUSED))
    }
}
