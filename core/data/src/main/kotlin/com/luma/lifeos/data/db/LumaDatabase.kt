package com.luma.lifeos.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.luma.lifeos.data.dao.CatalogDao
import com.luma.lifeos.data.dao.DerivedDataDao
import com.luma.lifeos.data.dao.EventContentDao
import com.luma.lifeos.data.dao.LifeEventDao
import com.luma.lifeos.data.entity.*

@Database(
    entities = [
        LifeEventEntity::class, MoodEntryEntity::class, EmotionEntity::class, MoodEmotionEntity::class,
        JournalEntryEntity::class, MemoryEntity::class, PersonEntity::class, EventPersonEntity::class,
        PlaceEntity::class, EventPlaceEntity::class, MediaAttachmentEntity::class, TagEntity::class,
        EventTagEntity::class, ActivityRecordEntity::class, AppUsageRecordEntity::class,
        DailySummaryEntity::class, IntegrationStateEntity::class,
    ],
    version = LumaDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(StorageConverters::class)
abstract class LumaDatabase : RoomDatabase() {
    abstract fun lifeEvents(): LifeEventDao
    abstract fun content(): EventContentDao
    abstract fun catalog(): CatalogDao
    abstract fun derived(): DerivedDataDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "luma.db"

        /** Version 1 is the initial schema. Add real, data-preserving upgrades here from v2. */
        val MIGRATIONS: List<Migration> = emptyList()

        fun open(context: Context, name: String = FILE_NAME): LumaDatabase =
            Room.databaseBuilder(context.applicationContext, LumaDatabase::class.java, name)
                .addMigrations(*MIGRATIONS.toTypedArray())
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
