package com.luma.lifeos.data

import com.luma.lifeos.domain.repository.PlaceRepository
import com.luma.lifeos.data.repository.RoomPlaceRepository
import android.net.Uri
import java.io.File
import com.luma.lifeos.data.media.ManagedPhotoFiles
import com.luma.lifeos.data.repository.RoomPhotoRepository
import com.luma.lifeos.domain.repository.PhotoRepository
import android.content.Context
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.RoomCatalogRepository
import com.luma.lifeos.data.repository.RoomDerivedDataRepository
import com.luma.lifeos.data.repository.RoomEventStore
import com.luma.lifeos.data.repository.RoomLifeEventRepository
import com.luma.lifeos.data.repository.RoomMoodRepository
import com.luma.lifeos.domain.repository.PersonRepository
import com.luma.lifeos.data.repository.RoomPersonRepository
import com.luma.lifeos.domain.repository.MemoryRepository
import com.luma.lifeos.data.repository.RoomMemoryRepository
import com.luma.lifeos.domain.repository.JournalRepository
import com.luma.lifeos.data.repository.RoomJournalRepository
import com.luma.lifeos.domain.repository.MoodRepository
import com.luma.lifeos.domain.repository.LifeEventRepository

/** One database per process, owned by the application composition root. */
class LumaStorage(context: Context) {
    private val database = LumaDatabase.open(context)
    val reviews: com.luma.lifeos.domain.repository.WeeklyReviewRepository = com.luma.lifeos.data.repository.RoomWeeklyReviewRepository(database)
    val search: com.luma.lifeos.domain.repository.SearchRepository = com.luma.lifeos.data.repository.RoomSearchRepository(database)
    private val photoFiles = ManagedPhotoFiles(File(context.filesDir, "photos"))
    private val photoMutex = kotlinx.coroutines.sync.Mutex()
    val backup: com.luma.lifeos.domain.backup.BackupService = com.luma.lifeos.data.backup.RoomBackupService(
        context.applicationContext, database, photoFiles, File(context.cacheDir, "backups"), photoMutex)
    val photos: PhotoRepository = RoomPhotoRepository(database, photoFiles, photoMutex) { source ->
        val uri = Uri.parse(source)
        require(uri.scheme == "content") { "Choose an image with the system picker" }
        requireNotNull(context.contentResolver.openInputStream(uri)) { "Photo source is unavailable" }
    }
    val timeline: com.luma.lifeos.domain.repository.TimelineRepository = com.luma.lifeos.data.repository.RoomTimelineRepository(database)
    val usage: com.luma.lifeos.domain.usage.UsageRepository = com.luma.lifeos.data.repository.RoomUsageRepository(database)
    val health: com.luma.lifeos.domain.health.HealthRepository = com.luma.lifeos.data.repository.RoomHealthRepository(database)
    val summaries: com.luma.lifeos.domain.repository.DailySummaryRepository = com.luma.lifeos.data.repository.RoomDailySummaryRepository(database)
    val lifeEvents: LifeEventRepository = RoomLifeEventRepository(database)
    val moods: MoodRepository = RoomMoodRepository(database)
    val journals: JournalRepository = RoomJournalRepository(database)
    val memories: MemoryRepository = RoomMemoryRepository(database)
    val places: PlaceRepository = RoomPlaceRepository(database)
    val people: PersonRepository = RoomPersonRepository(database)
    val events = RoomEventStore(database)
    val catalog = RoomCatalogRepository(database)
    val derived = RoomDerivedDataRepository(database)
}
