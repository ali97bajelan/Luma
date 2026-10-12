package com.luma.lifeos.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.luma.lifeos.data.dao.CatalogDao
import com.luma.lifeos.data.dao.DerivedDataDao
import com.luma.lifeos.data.dao.EventContentDao
import com.luma.lifeos.data.dao.LifeEventDao
import com.luma.lifeos.data.dao.UsageSessionDao
import com.luma.lifeos.data.entity.*

@Database(
    entities = [
        LifeEventEntity::class, MoodEntryEntity::class, EmotionEntity::class, MoodEmotionEntity::class,
        JournalEntryEntity::class, MemoryEntity::class, PersonEntity::class, EventPersonEntity::class,
        PlaceEntity::class, EventPlaceEntity::class, MediaAttachmentEntity::class, TagEntity::class,
        EventTagEntity::class, ActivityRecordEntity::class, AppUsageRecordEntity::class,
        DailySummaryEntity::class, IntegrationStateEntity::class,
        UsageSessionEntity::class, UsageSessionWindowEntity::class,
    ],
    version = LumaDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(StorageConverters::class)
abstract class LumaDatabase : RoomDatabase() {
    abstract fun search(): com.luma.lifeos.data.dao.SearchDao
    abstract fun lifeEvents(): LifeEventDao
    abstract fun content(): EventContentDao
    abstract fun catalog(): CatalogDao
    abstract fun derived(): DerivedDataDao
    abstract fun usageSessions(): UsageSessionDao

    companion object {
        const val VERSION = 2
        const val FILE_NAME = "luma.db"

        val MIGRATIONS: List<Migration> = listOf(object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS usage_session (
                    package_name TEXT NOT NULL, started_at INTEGER NOT NULL, ended_at INTEGER NOT NULL,
                    app_label TEXT, ongoing INTEGER NOT NULL, PRIMARY KEY(package_name, started_at))""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_usage_session_started_at ON usage_session(started_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_usage_session_ended_at ON usage_session(ended_at)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS usage_session_window (
                    query_start INTEGER NOT NULL, query_end INTEGER NOT NULL, complete INTEGER NOT NULL,
                    PRIMARY KEY(query_start, query_end))""")
                db.execSQL("UPDATE daily_summary SET freshness = 'STALE'")
            }
        })

        fun open(context: Context, name: String = FILE_NAME): LumaDatabase =
            Room.databaseBuilder(context.applicationContext, LumaDatabase::class.java, name)
                .addMigrations(*MIGRATIONS.toTypedArray())
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
