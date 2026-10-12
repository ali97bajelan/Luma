package com.luma.lifeos.integration.health

import android.content.Context
import com.luma.lifeos.domain.health.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

interface HealthPlatform {
    suspend fun access(): HealthAccess
    suspend fun steps(window: StepWindow): Long?
}

class AndroidHealthSource(private val platform: HealthPlatform) : HealthSource {
    constructor(context: Context) : this(HealthConnectPlatform(context.applicationContext))
    override suspend fun access(): HealthAccess = withContext(Dispatchers.IO) {
        try { platform.access() } catch (_: SecurityException) { HealthAccess.REQUIRED }
    }
    override suspend fun query(windows: List<StepWindow>): List<StepObservation> = withContext(Dispatchers.IO) {
        require(windows.size in 1..7 && windows.map { it.sourceReference }.distinct().size == windows.size)
        windows.map { window ->
            currentCoroutineContext().ensureActive()
            val access = access()
            if (access != HealthAccess.GRANTED) throw HealthAccessException(access)
            val total = try { platform.steps(window) } catch (_: SecurityException) { throw HealthAccessException(HealthAccess.REQUIRED) }
            StepObservation(window, total)
        }.also {
            val after = access()
            if (after != HealthAccess.GRANTED) throw HealthAccessException(after)
        }
    }
}
