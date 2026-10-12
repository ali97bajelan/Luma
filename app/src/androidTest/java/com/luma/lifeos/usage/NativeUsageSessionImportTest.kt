package com.luma.lifeos.usage

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.RoomUsageRepository
import com.luma.lifeos.domain.usage.*
import com.luma.lifeos.integration.usage.AndroidUsageSource
import java.time.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Read-only platform check; run after granting Usage access to the disposable validation app. */
class NativeUsageSessionImportTest {
    @Test fun nativeForegroundEventsPersistAndRetryWithoutDuplicatingVisits() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Normal instrumentation suites need no optional access.
        assumeTrue(context.packageName == "com.luma.lifeos.validation")
        val source = AndroidUsageSource(context)
        assumeTrue(source.access() == UsageAccess.GRANTED)
        val db = Room.inMemoryDatabaseBuilder(context, LumaDatabase::class.java).build()
        try {
            val repository = RoomUsageRepository(db)
            val integration = UsageSyncer(source, repository, object : UsageScheduler {
                override fun schedule() { }; override fun cancel() { }
            })
            integration.setEnabled(true)
            assertEquals(UsageSyncOutcome.SUCCESS, integration.importNow())
            val zone = ZoneId.systemDefault(); val date = LocalDate.now(zone)
            val first = repository.observeDaily(date, zone).first()!!
            assertEquals(UsageHistory.PARTIAL, first.history)
            assertTrue("Native events did not establish any app visits", first.sessions.isNotEmpty())
            assertTrue(first.apps.all { it.durationMillis > 0 })
            assertEquals(UsageSyncOutcome.SUCCESS, integration.importNow())
            val retry = repository.observeDaily(date, zone).first()!!
            assertEquals(retry.sessions.size, retry.sessions.map { it.identity }.distinct().size)
            first.sessions.filterNot { it.ongoing }.forEach { original ->
                val stored = retry.sessions.single { it.identity == original.identity }
                assertEquals(original.durationMillis, stored.durationMillis)
            }
            assertEquals(retry.sessions.sumOf { it.durationMillis }, retry.totalMillis)
            Unit
        } finally { db.close() }
    }
}
