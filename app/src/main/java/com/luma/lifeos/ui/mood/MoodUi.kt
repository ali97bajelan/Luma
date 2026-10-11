package com.luma.lifeos.ui.mood

import androidx.annotation.StringRes
import com.luma.lifeos.R

@StringRes
fun moodScoreLabel(score: Int): Int = when (score) {
    1 -> R.string.mood_very_low
    2 -> R.string.mood_low
    3 -> R.string.mood_okay
    4 -> R.string.mood_good
    5 -> R.string.mood_great
    else -> error("Invalid mood score")
}
