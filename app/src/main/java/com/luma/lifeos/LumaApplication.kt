package com.luma.lifeos

import android.app.Application
import com.luma.lifeos.data.LumaStorage

class LumaApplication : Application() {
    // Construction is lazy; the database opens off the main thread on the first suspend/Flow query.
    val storage: LumaStorage by lazy { LumaStorage(this) }
}
