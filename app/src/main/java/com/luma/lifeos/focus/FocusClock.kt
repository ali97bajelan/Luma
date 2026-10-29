package com.luma.lifeos.focus

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.luma.lifeos.domain.focus.FocusTimeSample
import java.time.Instant

/** Supplies raw clock readings; a missing boot count is deliberately treated as unknown. */
fun interface FocusClock {
    fun sample(): FocusTimeSample
}

class AndroidFocusClock(context: Context) : FocusClock {
    private val resolver = context.applicationContext.contentResolver

    override fun sample(): FocusTimeSample {
        val bootId = try {
            Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT).toString()
        } catch (_: Settings.SettingNotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        }
        return FocusTimeSample(
            wall = Instant.now(),
            elapsedMillis = SystemClock.elapsedRealtime(),
            bootId = bootId,
        )
    }
}
