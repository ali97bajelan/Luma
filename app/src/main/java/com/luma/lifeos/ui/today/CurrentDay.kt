package com.luma.lifeos.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

data class CurrentDay(val date: LocalDate, val zone: ZoneId)

private fun currentDay(): CurrentDay {
    val zone = ZoneId.systemDefault()
    return CurrentDay(LocalDate.now(zone), zone)
}

@Composable
fun rememberCurrentDay(): State<CurrentDay> {
    val owner = LocalLifecycleOwner.current
    return produceState(initialValue = currentDay(), owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Refresh immediately on resume, and notice midnight/time-zone changes while visible.
            while (true) {
                value = currentDay()
                delay(30_000)
            }
        }
    }
}
