package com.luma.lifeos.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import java.time.LocalDate

@Stable
internal class LumaCalendarDatePickerState(
    initialDate: LocalDate?,
    displayedDate: LocalDate,
    val minDate: LocalDate,
    val maxDate: LocalDate,
) {
    init {
        require(!minDate.isAfter(maxDate)) { "Picker minimum date must not follow maximum date" }
    }

    var selectedDate: LocalDate? by mutableStateOf(initialDate?.takeIf(::isWithinBounds))
        private set

    var displayedDate: LocalDate by mutableStateOf(displayedDate.coerceIn(minDate, maxDate))
        private set

    fun isWithinBounds(date: LocalDate): Boolean =
        !date.isBefore(minDate) && !date.isAfter(maxDate)

    fun isSelectable(date: LocalDate, predicate: (LocalDate) -> Boolean): Boolean =
        isWithinBounds(date) && predicate(date)

    fun canConfirm(predicate: (LocalDate) -> Boolean): Boolean =
        selectedDate?.let { isSelectable(it, predicate) } == true

    fun selectDate(date: LocalDate) {
        if (isWithinBounds(date)) {
            selectedDate = date
            displayedDate = date
        }
    }

    fun clearSelection() {
        selectedDate = null
    }

    fun setDisplayedDate(date: LocalDate) {
        displayedDate = date.coerceIn(minDate, maxDate)
    }

    companion object {
        private const val NO_SELECTION = Long.MIN_VALUE

        fun saver(minDate: LocalDate, maxDate: LocalDate) =
            listSaver<LumaCalendarDatePickerState, Long>(
                save = { state ->
                    listOf(
                        state.selectedDate?.toEpochDay() ?: NO_SELECTION,
                        state.displayedDate.toEpochDay(),
                    )
                },
                restore = { saved ->
                    LumaCalendarDatePickerState(
                        initialDate = saved[0].takeUnless { it == NO_SELECTION }?.let(LocalDate::ofEpochDay),
                        displayedDate = LocalDate.ofEpochDay(saved[1]),
                        minDate = minDate,
                        maxDate = maxDate,
                    )
                },
            )
    }
}

@Composable
internal fun rememberLumaCalendarDatePickerState(
    initialDate: LocalDate?,
    displayedDate: LocalDate,
    minDate: LocalDate,
    maxDate: LocalDate,
): LumaCalendarDatePickerState {
    val saver = remember(minDate, maxDate) {
        LumaCalendarDatePickerState.saver(minDate, maxDate)
    }
    return rememberSaveable(minDate, maxDate, saver = saver) {
        LumaCalendarDatePickerState(initialDate, displayedDate, minDate, maxDate)
    }
}

internal fun calendarPickerBounds(yearRange: IntRange): Pair<LocalDate, LocalDate> {
    require(!yearRange.isEmpty()) { "Picker year range must not be empty" }
    val firstYear = yearRange.first.coerceIn(1, 9999)
    val lastYear = yearRange.last.coerceIn(firstYear, 9999)
    return LocalDate.of(firstYear, 1, 1) to LocalDate.of(lastYear, 12, 31)
}
