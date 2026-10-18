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
import com.luma.lifeos.data.habit.HabitDao
import com.luma.lifeos.data.habit.HabitTimelineSql
import com.luma.lifeos.data.habit.HabitDayChoiceEntity
import com.luma.lifeos.data.habit.HabitEntity
import com.luma.lifeos.data.habit.HabitLogEntity
import com.luma.lifeos.data.habit.HabitReminderEntity
import com.luma.lifeos.data.habit.HabitReminderRuntimeEntity
import com.luma.lifeos.data.habit.HabitScheduleEntity
import com.luma.lifeos.data.habit.HabitSettingsEntity

@Database(
    entities = [
        LifeEventEntity::class, MoodEntryEntity::class, EmotionEntity::class, MoodEmotionEntity::class,
        JournalEntryEntity::class, MemoryEntity::class, PersonEntity::class, EventPersonEntity::class,
        PlaceEntity::class, EventPlaceEntity::class, MediaAttachmentEntity::class, TagEntity::class,
        EventTagEntity::class, ActivityRecordEntity::class, AppUsageRecordEntity::class,
        DailySummaryEntity::class, IntegrationStateEntity::class,
        UsageSessionEntity::class, UsageSessionWindowEntity::class,
        HabitSettingsEntity::class, HabitEntity::class, HabitScheduleEntity::class, HabitLogEntity::class,
        HabitDayChoiceEntity::class, HabitReminderEntity::class, HabitReminderRuntimeEntity::class,
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
    abstract fun habits(): HabitDao
    abstract fun habitTimeline(): HabitTimelineSql

    companion object {
        const val VERSION = 3
        const val FILE_NAME = "luma.db"

        val MIGRATIONS: List<Migration> = listOf(
            object : Migration(1, 2) {
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
            },
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("""CREATE TABLE IF NOT EXISTS habit_settings (
                        setting_id INTEGER NOT NULL PRIMARY KEY CHECK (setting_id = 1),
                        tracking_zone_id TEXT NOT NULL
                    )""")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS habit (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL CHECK (length(name) BETWEEN 1 AND 100 AND name = trim(name)),
                        unit_label TEXT CHECK (unit_label IS NULL OR (length(unit_label) BETWEEN 1 AND 30 AND unit_label = trim(unit_label))),
                        start_date TEXT NOT NULL CHECK (date(start_date) IS NOT NULL AND date(start_date) = start_date),
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL CHECK (updated_at >= created_at)
                    )""")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_start_date ON habit(start_date)")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS habit_schedule (
                        id TEXT NOT NULL PRIMARY KEY,
                        habit_id TEXT NOT NULL,
                        effective_from TEXT NOT NULL CHECK (date(effective_from) IS NOT NULL AND date(effective_from) = effective_from),
                        target INTEGER NOT NULL CHECK (target BETWEEN 1 AND 999),
                        weekday_mask INTEGER NOT NULL CHECK (weekday_mask BETWEEN 1 AND 127),
                        state TEXT NOT NULL CHECK (state IN ('ACTIVE', 'PAUSED', 'ARCHIVED')),
                        FOREIGN KEY(habit_id) REFERENCES habit(id) ON DELETE CASCADE
                    )""")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_habit_schedule_habit_id_effective_from ON habit_schedule(habit_id, effective_from)")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS habit_log (
                        id TEXT NOT NULL PRIMARY KEY,
                        habit_id TEXT NOT NULL,
                        habit_date TEXT NOT NULL CHECK (date(habit_date) IS NOT NULL AND date(habit_date) = habit_date),
                        zone_id TEXT NOT NULL,
                        occurred_at INTEGER,
                        recorded_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL CHECK (updated_at >= recorded_at),
                        FOREIGN KEY(habit_id) REFERENCES habit(id) ON DELETE CASCADE
                    )""")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_log_habit_date_occurred_at_id ON habit_log(habit_date, occurred_at, id)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_log_habit_id_habit_date_id ON habit_log(habit_id, habit_date, id)")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS habit_day_choice (
                        habit_id TEXT NOT NULL,
                        habit_date TEXT NOT NULL CHECK (date(habit_date) IS NOT NULL AND date(habit_date) = habit_date),
                        skip_remaining INTEGER NOT NULL CHECK (skip_remaining IN (0, 1)),
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY(habit_id, habit_date),
                        FOREIGN KEY(habit_id) REFERENCES habit(id) ON DELETE CASCADE
                    )""")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS habit_reminder (
                        habit_id TEXT NOT NULL PRIMARY KEY,
                        enabled INTEGER NOT NULL CHECK (enabled IN (0, 1)),
                        mode TEXT NOT NULL CHECK (mode IN ('TIMES', 'INTERVAL')),
                        times_json TEXT NOT NULL,
                        interval_minutes INTEGER,
                        start_minute INTEGER,
                        end_minute INTEGER,
                        revision_token TEXT NOT NULL,
                        FOREIGN KEY(habit_id) REFERENCES habit(id) ON DELETE CASCADE
                    )""")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_habit_reminder_habit_id_revision_token ON habit_reminder(habit_id, revision_token)")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS habit_reminder_runtime (
                        habit_id TEXT NOT NULL PRIMARY KEY,
                        revision_token TEXT NOT NULL,
                        last_delivered_slot TEXT,
                        consumed_action_token TEXT,
                        snooze_slot TEXT,
                        snooze_until INTEGER,
                        needs_reconcile INTEGER NOT NULL CHECK (needs_reconcile IN (0, 1)),
                        CHECK ((snooze_slot IS NULL) = (snooze_until IS NULL)),
                        FOREIGN KEY(habit_id, revision_token) REFERENCES habit_reminder(habit_id, revision_token) ON DELETE CASCADE
                    )""")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_reminder_runtime_habit_id_revision_token ON habit_reminder_runtime(habit_id, revision_token)")
                }
            },
        )

        fun open(context: Context, name: String = FILE_NAME): LumaDatabase =
            Room.databaseBuilder(context.applicationContext, LumaDatabase::class.java, name)
                .addMigrations(*MIGRATIONS.toTypedArray())
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
