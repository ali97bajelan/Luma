package com.luma.lifeos.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.luma.lifeos.data.dao.CatalogDao
import com.luma.lifeos.data.calendar.CalendarDao
import com.luma.lifeos.data.focus.FocusCommandReceiptEntity
import com.luma.lifeos.data.focus.FocusDao
import com.luma.lifeos.data.focus.FocusBreakEntity
import com.luma.lifeos.data.focus.FocusDeletedSessionEntity
import com.luma.lifeos.data.focus.FocusRunEntity
import com.luma.lifeos.data.focus.FocusRuntimeEntity
import com.luma.lifeos.data.focus.FocusSessionEntity
import com.luma.lifeos.data.focus.FocusStorageStateEntity
import com.luma.lifeos.data.focus.FocusGoalSettingsEntity
import com.luma.lifeos.data.focus.FocusInvitationDeliveryEntity
import com.luma.lifeos.data.focus.FocusInvitationDao
import com.luma.lifeos.data.focus.FocusInvitationSettingsEntity
import com.luma.lifeos.data.focus.FocusGoalVersionEntity
import com.luma.lifeos.data.focus.FocusProductCommandReceiptEntity
import com.luma.lifeos.data.focus.FocusProductDao
import com.luma.lifeos.data.focus.FocusTagAliasEntity
import com.luma.lifeos.data.focus.FocusTagCatalogStateEntity
import com.luma.lifeos.data.focus.FocusTagEntity
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
import com.luma.lifeos.data.todo.TodoDao
import com.luma.lifeos.data.todo.TodoEntity
import com.luma.lifeos.data.todo.TodoCommandDao
import com.luma.lifeos.data.todo.TodoCommandEntity
import com.luma.lifeos.data.todo.TodoCommandMemberEntity
import com.luma.lifeos.data.todo.TaskReminderEntity
import com.luma.lifeos.data.todo.TaskReminderDao
import com.luma.lifeos.data.widget.WidgetCommandEntity
import com.luma.lifeos.data.widget.WidgetDao
import com.luma.lifeos.data.widget.WidgetInstanceEntity

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
        TodoEntity::class, TodoCommandEntity::class, TodoCommandMemberEntity::class,
        TaskReminderEntity::class,
        WidgetInstanceEntity::class, WidgetCommandEntity::class,
        FocusSessionEntity::class, FocusRunEntity::class, FocusRuntimeEntity::class, FocusBreakEntity::class,
        FocusCommandReceiptEntity::class, FocusDeletedSessionEntity::class, FocusStorageStateEntity::class,
        FocusTagEntity::class, FocusGoalSettingsEntity::class, FocusGoalVersionEntity::class,
        FocusProductCommandReceiptEntity::class, FocusTagCatalogStateEntity::class, FocusTagAliasEntity::class,
        FocusInvitationSettingsEntity::class, FocusInvitationDeliveryEntity::class,
    ],
    version = LumaDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(StorageConverters::class)
abstract class LumaDatabase : RoomDatabase() {
    abstract fun calendar(): CalendarDao
    abstract fun search(): com.luma.lifeos.data.dao.SearchDao
    abstract fun lifeEvents(): LifeEventDao
    abstract fun content(): EventContentDao
    abstract fun catalog(): CatalogDao
    abstract fun derived(): DerivedDataDao
    abstract fun usageSessions(): UsageSessionDao
    abstract fun habits(): HabitDao
    abstract fun habitTimeline(): HabitTimelineSql
    abstract fun todos(): TodoDao
    abstract fun todoCommands(): TodoCommandDao
    abstract fun taskReminders(): TaskReminderDao
    abstract fun widgets(): WidgetDao
    abstract fun focus(): FocusDao
    abstract fun focusProducts(): FocusProductDao
    abstract fun focusInvitations(): FocusInvitationDao

    companion object {
        const val VERSION = 17
        const val FILE_NAME = "luma.db"

        val MIGRATIONS: List<Migration> = listOf(
            object : Migration(16, 17) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE todo_task ADD COLUMN parent_id TEXT REFERENCES todo_task(id) ON DELETE CASCADE")
                    db.execSQL("ALTER TABLE todo_task ADD COLUMN sibling_position INTEGER CHECK (sibling_position IS NULL OR sibling_position >= 0)")
                    db.execSQL("ALTER TABLE todo_task ADD COLUMN family_revision_token TEXT NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000'")
                    db.execSQL("""UPDATE todo_task SET family_revision_token =
                        lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-' ||
                        lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(2))) || '-' ||
                        lower(hex(randomblob(6)))""")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_todo_task_parent_id_sibling_position ON todo_task(parent_id, sibling_position)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_task_parent_id_completed_at_sibling_position ON todo_task(parent_id, completed_at, sibling_position)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_task_completed_at_importance_code_due_date_due_time_minutes_created_at_id ON todo_task(completed_at, importance_code, due_date, due_time_minutes, created_at, id)")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS todo_family_command (
                        id TEXT NOT NULL PRIMARY KEY,
                        kind TEXT NOT NULL,
                        state TEXT NOT NULL,
                        target_id TEXT NOT NULL,
                        root_id TEXT NOT NULL,
                        completed INTEGER,
                        family_revision_before TEXT NOT NULL,
                        family_revision_after TEXT,
                        process_token TEXT,
                        payload_version INTEGER NOT NULL DEFAULT 1,
                        command_id TEXT,
                        source_command_id TEXT,
                        child_count INTEGER NOT NULL DEFAULT 0,
                        changed_task_count INTEGER NOT NULL DEFAULT 0,
                        open_child_count INTEGER NOT NULL DEFAULT 0,
                        reopens_parent INTEGER NOT NULL DEFAULT 0,
                        confirmation_required INTEGER NOT NULL DEFAULT 0,
                        undo_available INTEGER NOT NULL DEFAULT 0,
                        pinned INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        terminal_at INTEGER
                    )""")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_todo_family_command_command_id ON todo_family_command(command_id)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_family_command_state_pinned_created_at ON todo_family_command(state, pinned, created_at)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_family_command_source_command_id ON todo_family_command(source_command_id)")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS todo_family_command_member (
                        command_id TEXT NOT NULL,
                        task_id TEXT NOT NULL,
                        expected_revision TEXT NOT NULL,
                        previous_completed_at INTEGER,
                        will_change INTEGER NOT NULL,
                        resulting_revision TEXT,
                        resulting_completed_at INTEGER,
                        PRIMARY KEY(command_id, task_id),
                        FOREIGN KEY(command_id) REFERENCES todo_family_command(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_family_command_member_task_id ON todo_family_command_member(task_id)")
                    db.execSQL("""CREATE TRIGGER IF NOT EXISTS todo_task_validate_family_insert
                        BEFORE INSERT ON todo_task
                        WHEN ((NEW.parent_id IS NULL) != (NEW.sibling_position IS NULL))
                          OR (NEW.sibling_position IS NOT NULL AND NEW.sibling_position < 0)
                          OR (NEW.parent_id IS NOT NULL AND NOT EXISTS (
                              SELECT 1 FROM todo_task AS parent
                              WHERE parent.id = NEW.parent_id AND parent.parent_id IS NULL
                                AND parent.completed_at IS NULL
                          ))
                        BEGIN SELECT RAISE(ABORT, 'Invalid task family relationship'); END""")
                    db.execSQL("""CREATE TRIGGER IF NOT EXISTS todo_task_validate_family_update
                        BEFORE UPDATE OF parent_id, sibling_position ON todo_task
                        WHEN ((NEW.parent_id IS NULL) != (NEW.sibling_position IS NULL))
                          OR (NEW.sibling_position IS NOT NULL AND NEW.sibling_position < 0)
                          OR (NEW.parent_id IS NOT NULL AND NOT EXISTS (
                              SELECT 1 FROM todo_task AS parent
                              WHERE parent.id = NEW.parent_id AND parent.parent_id IS NULL
                          ))
                          OR (NEW.parent_id IS NOT NULL AND NEW.parent_id != OLD.parent_id AND EXISTS (
                              SELECT 1 FROM todo_task AS parent
                              WHERE parent.id = NEW.parent_id AND parent.completed_at IS NOT NULL
                          ))
                          OR (NEW.parent_id IS NOT NULL AND OLD.parent_id IS NULL AND EXISTS (
                              SELECT 1 FROM todo_task AS child WHERE child.parent_id = OLD.id
                          ))
                        BEGIN SELECT RAISE(ABORT, 'Invalid task family relationship'); END""")
                    db.execSQL("""CREATE TRIGGER IF NOT EXISTS todo_task_reject_completed_root_with_open_children
                        BEFORE UPDATE OF completed_at ON todo_task
                        WHEN NEW.parent_id IS NULL AND NEW.completed_at IS NOT NULL
                          AND EXISTS (
                              SELECT 1 FROM todo_task AS child
                              WHERE child.parent_id = NEW.id AND child.completed_at IS NULL
                          )
                        BEGIN SELECT RAISE(ABORT, 'Completed task cannot have open subtasks'); END""")
                    db.execSQL("""CREATE TRIGGER IF NOT EXISTS todo_task_reject_open_child_under_completed_root
                        BEFORE UPDATE OF completed_at ON todo_task
                        WHEN NEW.parent_id IS NOT NULL AND NEW.completed_at IS NULL
                          AND EXISTS (
                              SELECT 1 FROM todo_task AS parent
                              WHERE parent.id = NEW.parent_id AND parent.completed_at IS NOT NULL
                          )
                        BEGIN SELECT RAISE(ABORT, 'Open subtask requires an open parent'); END""")
                }
            },
            object : Migration(15, 16) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE todo_task ADD COLUMN importance_code INTEGER NOT NULL DEFAULT 0")
                }
            },
            object : Migration(14, 15) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Nullable linked task fields keep all existing session and run rows intact.
                    db.execSQL("ALTER TABLE focus_session ADD COLUMN task_id TEXT REFERENCES todo_task(id) ON DELETE SET NULL")
                    db.execSQL("ALTER TABLE focus_session ADD COLUMN task_origin INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_session_task_id ON focus_session(task_id)")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS focus_invitation_settings (
                        singleton_id INTEGER NOT NULL PRIMARY KEY CHECK (singleton_id = 1),
                        enabled INTEGER NOT NULL DEFAULT 0 CHECK (enabled IN (0, 1)),
                        revision INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0),
                        weekday_mask INTEGER NOT NULL DEFAULT 0 CHECK (weekday_mask BETWEEN 0 AND 127),
                        minute_of_day INTEGER NOT NULL DEFAULT 540 CHECK (minute_of_day BETWEEN 0 AND 1439),
                        CHECK (enabled = 0 OR weekday_mask != 0)
                    )""")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS focus_invitation_delivery (
                        database_generation TEXT NOT NULL,
                        schedule_revision INTEGER NOT NULL CHECK (schedule_revision >= 0),
                        local_date TEXT NOT NULL,
                        due_at INTEGER NOT NULL,
                        state TEXT NOT NULL DEFAULT 'PENDING',
                        snoozed_due_at INTEGER,
                        action_token TEXT,
                        PRIMARY KEY(database_generation, schedule_revision, local_date)
                    )""")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_invitation_delivery_due_at ON focus_invitation_delivery(due_at)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_focus_invitation_delivery_action_token ON focus_invitation_delivery(action_token)")
                    db.execSQL("INSERT OR IGNORE INTO focus_invitation_settings(singleton_id, enabled, revision, weekday_mask, minute_of_day) VALUES (1, 0, 0, 0, 540)")
                }
            },
            object : Migration(13, 14) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("""CREATE TABLE IF NOT EXISTS focus_tag_catalog_state (
                        singleton_id INTEGER NOT NULL PRIMARY KEY CHECK (singleton_id = 1),
                        revision INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0)
                    )""")
                    db.execSQL("INSERT OR IGNORE INTO focus_tag_catalog_state(singleton_id, revision) VALUES (1, 0)")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS focus_tag_alias (
                        normalized_name TEXT NOT NULL PRIMARY KEY,
                        former_tag_id TEXT NOT NULL,
                        target_tag_id TEXT NOT NULL,
                        FOREIGN KEY(target_tag_id) REFERENCES focus_tag(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_tag_alias_target_tag_id ON focus_tag_alias(target_tag_id)")
                }
            },
            object : Migration(12, 13) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE focus_session ADD COLUMN outcome TEXT")
                    db.execSQL("ALTER TABLE focus_session ADD COLUMN next_step TEXT")
                }
            },
            object : Migration(11, 12) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("""CREATE TABLE IF NOT EXISTS focus_goal_settings (
                        singleton_id INTEGER NOT NULL PRIMARY KEY CHECK (singleton_id = 1),
                        enabled INTEGER NOT NULL DEFAULT 0 CHECK (enabled IN (0, 1)),
                        settings_revision INTEGER NOT NULL DEFAULT 0 CHECK (settings_revision >= 0),
                        visible_from_monday TEXT,
                        visible_from_zone_id TEXT,
                        CHECK ((visible_from_monday IS NULL) = (visible_from_zone_id IS NULL)),
                        CHECK (enabled = 0 OR visible_from_monday IS NOT NULL)
                    )""")
                    db.execSQL("INSERT OR IGNORE INTO focus_goal_settings(singleton_id, enabled, settings_revision) VALUES (1, 0, 0)")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS focus_goal_version (
                        id TEXT NOT NULL PRIMARY KEY,
                        settings_id INTEGER NOT NULL DEFAULT 1,
                        revision INTEGER NOT NULL CHECK (revision > 0),
                        effective_monday TEXT NOT NULL,
                        zone_id TEXT NOT NULL,
                        target_minutes INTEGER NOT NULL CHECK (target_minutes BETWEEN 1 AND 10080),
                        preferred_weekday_mask INTEGER NOT NULL CHECK (preferred_weekday_mask BETWEEN 1 AND 127),
                        FOREIGN KEY(settings_id) REFERENCES focus_goal_settings(singleton_id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_focus_goal_version_zone_id_effective_monday ON focus_goal_version(zone_id, effective_monday)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_goal_version_settings_id ON focus_goal_version(settings_id)")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS focus_product_command_receipt (
                        database_generation TEXT NOT NULL,
                        command_id TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        result_type TEXT NOT NULL,
                        PRIMARY KEY(database_generation, command_id)
                    )""")
                }
            },
            object : Migration(10, 11) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE focus_session ADD COLUMN timer_mode TEXT NOT NULL DEFAULT 'COUNT_UP'")
                    db.execSQL("ALTER TABLE focus_session ADD COLUMN target_millis INTEGER")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS focus_break_runtime (
                        singleton_id INTEGER NOT NULL PRIMARY KEY,
                        duration_millis INTEGER NOT NULL CHECK (duration_millis BETWEEN 60000 AND 3600000),
                        started_elapsed_millis INTEGER NOT NULL CHECK (started_elapsed_millis >= 0),
                        boot_id TEXT,
                        database_generation TEXT NOT NULL,
                        revision INTEGER NOT NULL CHECK (revision >= 0)
                    )""")
                }
            },
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("""CREATE TABLE IF NOT EXISTS focus_tag (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        normalized_name TEXT NOT NULL
                    )""")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_focus_tag_normalized_name ON focus_tag(normalized_name)")
                    db.execSQL("ALTER TABLE focus_session ADD COLUMN name TEXT")
                    db.execSQL("ALTER TABLE focus_session ADD COLUMN tag_id TEXT REFERENCES focus_tag(id) ON DELETE SET NULL")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_session_tag_id ON focus_session(tag_id)")
                }
            },
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
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("""CREATE TABLE IF NOT EXISTS todo_task (
                        id TEXT NOT NULL, title TEXT NOT NULL, note TEXT, due_date TEXT,
                        completed_at INTEGER, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
                        revision_token TEXT NOT NULL, PRIMARY KEY(id)
                    )""")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_task_completed_at_due_date_created_at_id ON todo_task(completed_at, due_date, created_at, id)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_task_completed_at_id ON todo_task(completed_at, id)")
                }
            },
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("""CREATE TABLE IF NOT EXISTS widget_instance (
                        widget_id INTEGER NOT NULL, kind TEXT NOT NULL, show_details INTEGER NOT NULL,
                        feedback_json TEXT, PRIMARY KEY(widget_id)
                    )""")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS widget_command (
                        token TEXT NOT NULL, widget_id INTEGER NOT NULL, action TEXT NOT NULL,
                        payload_json TEXT NOT NULL, fingerprint TEXT NOT NULL, status TEXT NOT NULL,
                        outcome_json TEXT, created_at INTEGER NOT NULL, PRIMARY KEY(token),
                        FOREIGN KEY(widget_id) REFERENCES widget_instance(widget_id) ON DELETE CASCADE
                    )""")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_widget_command_widget_id_status_fingerprint ON widget_command(widget_id, status, fingerprint)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_widget_command_created_at ON widget_command(created_at)")
                }
            },
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE todo_task ADD COLUMN due_time_minutes INTEGER")
                    db.execSQL("DROP INDEX IF EXISTS index_todo_task_completed_at_due_date_created_at_id")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_task_completed_at_due_date_due_time_minutes_created_at_id ON todo_task(completed_at, due_date, due_time_minutes, created_at, id)")
                }
            },
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("""CREATE TABLE IF NOT EXISTS task_reminder (
                        task_id TEXT NOT NULL PRIMARY KEY,
                        enabled INTEGER NOT NULL CHECK (enabled IN (0, 1)),
                        task_revision TEXT NOT NULL,
                        revision_token TEXT NOT NULL,
                        consumed_action_token TEXT,
                        last_delivered_slot TEXT,
                        snooze_slot TEXT,
                        snooze_until INTEGER,
                        needs_reconcile INTEGER NOT NULL CHECK (needs_reconcile IN (0, 1)),
                        CHECK ((snooze_slot IS NULL) = (snooze_until IS NULL)),
                        FOREIGN KEY(task_id) REFERENCES todo_task(id) ON DELETE CASCADE
                    )""")
                }
            },
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("""CREATE TABLE IF NOT EXISTS focus_session (
                        id TEXT NOT NULL PRIMARY KEY,
                        state TEXT NOT NULL,
                        revision INTEGER NOT NULL,
                        started_at_seconds INTEGER NOT NULL,
                        started_at_nanos INTEGER NOT NULL,
                        ended_at_seconds INTEGER,
                        ended_at_nanos INTEGER,
                        created_at_seconds INTEGER NOT NULL,
                        created_at_nanos INTEGER NOT NULL,
                        updated_at_seconds INTEGER NOT NULL,
                        updated_at_nanos INTEGER NOT NULL,
                        terminal_reason TEXT,
                        recovered_or_uncertain INTEGER NOT NULL,
                        open_slot INTEGER
                    )""")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS focus_run (
                        id TEXT NOT NULL PRIMARY KEY,
                        session_id TEXT NOT NULL,
                        ordinal INTEGER NOT NULL,
                        started_at_seconds INTEGER NOT NULL,
                        started_at_nanos INTEGER NOT NULL,
                        ended_at_seconds INTEGER,
                        ended_at_nanos INTEGER,
                        credited_millis INTEGER NOT NULL,
                        raw_started_at_seconds INTEGER NOT NULL,
                        raw_started_at_nanos INTEGER NOT NULL,
                        raw_ended_at_seconds INTEGER,
                        raw_ended_at_nanos INTEGER,
                        open_session_id TEXT,
                        FOREIGN KEY(session_id) REFERENCES focus_session(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS focus_runtime (
                        slot_id INTEGER NOT NULL PRIMARY KEY,
                        session_id TEXT NOT NULL,
                        run_id TEXT NOT NULL,
                        run_started_elapsed_millis INTEGER NOT NULL,
                        checkpoint_elapsed_millis INTEGER NOT NULL,
                        checkpoint_wall_seconds INTEGER NOT NULL,
                        checkpoint_wall_nanos INTEGER NOT NULL,
                        boot_id TEXT,
                        process_instance_id TEXT NOT NULL,
                        recovery_required INTEGER NOT NULL,
                        revision INTEGER NOT NULL,
                        FOREIGN KEY(session_id) REFERENCES focus_session(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(run_id, session_id) REFERENCES focus_run(id, session_id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS focus_command_receipt (
                        sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        command_id TEXT NOT NULL,
                        database_generation TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        result_type TEXT NOT NULL,
                        result_session_id TEXT,
                        result_invalid_reason TEXT,
                        created_at_seconds INTEGER NOT NULL,
                        created_at_nanos INTEGER NOT NULL
                    )""")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS focus_deleted_session (
                        session_id TEXT NOT NULL PRIMARY KEY,
                        database_generation TEXT NOT NULL
                    )""")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS focus_storage_state (
                        singleton_id INTEGER NOT NULL PRIMARY KEY,
                        generation_id TEXT NOT NULL
                    )""")

                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_focus_session_open_slot ON focus_session(open_slot)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_session_state_started_at_seconds_started_at_nanos ON focus_session(state, started_at_seconds, started_at_nanos)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_focus_run_session_id_ordinal ON focus_run(session_id, ordinal)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_focus_run_id_session_id ON focus_run(id, session_id)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_focus_run_open_session_id ON focus_run(open_session_id)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_run_started_at_seconds_started_at_nanos ON focus_run(started_at_seconds, started_at_nanos)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_run_ended_at_seconds_ended_at_nanos ON focus_run(ended_at_seconds, ended_at_nanos)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_focus_runtime_session_id ON focus_runtime(session_id)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_focus_runtime_run_id ON focus_runtime(run_id)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_runtime_run_id_session_id ON focus_runtime(run_id, session_id)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_focus_command_receipt_database_generation_command_id ON focus_command_receipt(database_generation, command_id)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_deleted_session_database_generation ON focus_deleted_session(database_generation)")
                    db.execSQL(
                        "INSERT OR IGNORE INTO focus_storage_state(singleton_id, generation_id) VALUES (1, ?)",
                        arrayOf(java.util.UUID.randomUUID().toString()),
                    )
                }
            },
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    normalizeLegacyJournals(db)
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
