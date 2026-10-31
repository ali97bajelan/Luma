# Luma portable backup — format version 1

Settings → Backup and restore exports saved application tables and referenced
photos using the system file picker. Choose backup to restore validates a file and
shows its creation time, event count, photo count, habit count, repetition count,
task count and terminal focus-session count before asking to replace the current
saved data. There is no merge mode.
Export current data first to retain it.

App Lock credentials and policy are device-local and are never archive members.
The fixed table and photo allowlist excludes retry state, in-memory grants,
restricted-capture drafts, action receipts, notification/widget runtime and Focus
runtime. Android cloud backup and device transfer are disabled for app data; the
lock store also lives under `noBackupFilesDir`. A fresh password is required for
export and for the restore commit when App Lock is enabled. Restore into a device
with an existing lock keeps that device's policy and returns it to a locked
state. A destination without a lock must explicitly choose to set one up or
continue without one before replacement is committed and restored content is
shown. The archive itself remains unencrypted.

Backups are unencrypted and contain private text, terminal focus history, task titles and notes, names and
photos. No automatic
upload occurs; the selected document provider controls the destination. No broad
storage permission is requested. The picker follows Android's
[Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files).
An interrupted or failed export can leave an incomplete destination file; success
is reported only after the archive and output stream complete. Missing or corrupt
referenced photos abort export rather than silently omitting them.

## Archive layout

- `manifest.properties`: Java Properties syntax. Required `format=luma-backup`,
  `format_version=1`, `schema_version=1`, `2`, `3`, `4`, `5`, `6` or `7`, `created_at` (UTC epoch
  milliseconds),
  and `file_count`. Every payload has `size.<path>` and `sha256.<path>` entries.
- `tables/<table>.tsv`: one file per application table. Header is the exact column
  list from that Room schema. Subsequent lines have a typed cell per column,
  separated by tabs and terminated by LF.
- `photos/<canonical-uuid>.img`: original private image bytes referenced by
  `media_attachment.relative_path`. Image type is validated by Android decoding;
  the extension is an internal filename, not an image format.

Typed cells preserve SQLite values without locale-dependent parsing:

| Prefix | Value |
| --- | --- |
| `N` | Null; the whole cell must be exactly `N` |
| `I` | Signed base-10 64-bit integer |
| `F` | Finite double using Java's decimal representation |
| `S` | Base64-encoded UTF-8 text; `S` alone is an empty string |

Unicode, newlines and tabs remain inside the encoded string. No SQL, schema DDL,
serialized JVM objects or archive-provided code is executed. SHA-256 detects
payload changes and corruption; it is not a signature or authenticity guarantee.

Schema 1 contains the original 17 application tables. Schema 2 adds
`usage_session` and `usage_session_window`, for 19 tables total. Schema 3 adds
`habit_settings`, `habit`, `habit_schedule`, `habit_log`, `habit_day_choice`,
`habit_reminder` and `habit_reminder_runtime`, for 26 tables total. Schema 4 appends
`todo_task`, for 27 tables total. Schema 5 keeps the same 27-table inventory and
adds nullable `due_time_minutes` to task rows in generated Room column order. A
non-null value is minutes since midnight from 0 through 1439; null means the task
has no selected time. A time requires a due date. Schema-4 task files retain their
exact eight-column legacy header and import with null time; schema 5 strictly
requires the new column, including when every row is null. Import uses generated
current Room columns with a version-aware legacy adapter, so older archives leave
newer tables empty while retaining their legacy data. Schema
1–3 archives contain no tasks; restoring one replaces the current task table with
zero rows and the preview warns that current tasks will be removed. Schema 3
preserves habit history and configuration, but restored reminders are disabled with
rotated revisions and fresh runtime marked for reconciliation. Schema 1/2
replacement clears any current habits.
Schema 6 appends `task_reminder`, for 28 tables total. It stores optional task
reminder configuration and short-lived action runtime. Restore disables task
reminders, rotates task and reminder revisions, and clears delivered-action and
snooze state. Schema 1–5 archives contain no task reminder rows.
Schema 7 appends `focus_session` and `focus_run`, for 30 tables total. Only
`FINISHED` and `INTERRUPTED` sessions and their closed runs are exported; paused
sessions require an explicit stop-or-exclude choice. Schema 1–6 contain no focus
rows, so restoring one replaces current focus history with an empty history.
Unknown format/schema versions fail safely; future versions require an explicit
reader. Task rows are validated for canonical UUIDs and civil dates, minute range,
the date/time pairing, title/note limits, and update/creation chronology. Schema 4
restores preserve task identities, content and timestamps while assigning null due
time and rotating revision tokens. Schema 5 preserves due minutes as well; both
versions rotate revision tokens so pre-restore writes cannot change replacement
data. Room schema 6 adds `due_time_minutes`; its 5→6 migration keeps every existing
task and gives it null time. Room schema 7 adds `task_reminder`; its 6→7 migration
creates no enabled reminder rows. Room schema 8 adds focus sessions, their runs and
device-local focus runtime/replay state. Only the two terminal-history tables are
portable; all focus runtime and widget runtime tables stay device-local. Task due times are floating civil values: changing time
zone does not convert the saved date or minute. Buckets remain based on the local
calendar date. A task reminder is created only after the user enables it and the
task has both a due date and time.

## Validation and replacement

Entry names must match the fixed table list or a canonical photo filename. Duplicate,
unknown, missing, absolute and traversing paths are rejected. Manifest inventory,
sizes and hashes must agree exactly. Limits: 2 GiB total expanded content, 512 MiB
per table, 25 MiB per photo, 32 MiB manifest, 100,000 payloads and 4 MiB per TSV line.
Metadata JSON nesting is limited to 64 levels before Android parsing. Validation
requires private staging disk space for extracted payloads and the validation DB.

Rows enter a separate generated Room database using bound statements. Column names,
nullability, SQLite value types, stable UUIDs, entity/domain invariants, foreign
keys, existing child/parent type/source, database integrity and exact photo
coverage are checked. Focus sessions and runs must be terminal, closed, linked,
canonical and nonnegative; a session without runs or per-session duration overflow
is rejected. Habit records must also satisfy their tracking-zone,
schedule, log, choice and reminder-runtime relationships. Incomplete event
envelopes remain supported. Native usage bounds remain independent of historical
event timestamps, as in the existing usage views. No live record is replaced
during preparation or when its confirmation is cancelled.

Export reads the fixed table inventory (filtering focus to terminal sessions/runs)
and copies photos within one Room transaction while holding
the shared photo-store mutex; this includes committed WAL data and does not copy
a live SQLite file. Archive compression uses independent snapshot files afterward.
Restore shares the mutex with imports, previews, leases and cleanup. It writes and
syncs photos to fresh UUID paths, syncs their directory, then replaces records in
one Room transaction. Existing photo files are never overwritten. A database failure
rolls back records and removes new files. Unreferenced old files are cleaned after
commit while preserving protected draft leases. Abandoned private staging from a
previous process is removed on the next backup operation.

Restore retains event/content identities, task titles and notes, civil due dates and
schema-5 due minutes, completion state and timestamps, links, source metadata, usage history,
terminal focus sessions/runs and referenced photo bytes. Photo paths change intentionally. Derived summaries become stale for
recalculation. Imports are disabled, usage device cursors
and clock anchors reset, and the saved Health Connect timezone is retained with an
advanced revision to reject old requests. Last-sync evidence and saved observations
remain. The user may re-enable imports through Settings; Android permissions,
app language and external person photo URI availability are not transferred.
Unsaved editor drafts are not backup payloads.

Schema 3 restores retain habit definitions, schedules, logs, choices and reminder
preferences. Reminder preferences are forced off, revisions rotate, and transient
reminder runtime is discarded so restore cannot replay an old notification action.
Schema 4 restores retain open and completed tasks with no due time. Schema 5 also
retains optional due minutes. Schema 6 includes task reminder rows but always
restores them disabled with fresh revisions and empty runtime. Task revision tokens rotate during publication.
Older schema 1–3 restores clear the live task table. Older schema 1–6 restores clear
the live focus history. Restored focus rows never include an open session or active
restriction; focus app selections and emergency markers remain device-local.
Replacement clears runtime, receipts and tombstones and rotates the focus database
generation in the same transaction as importing history, rejecting delayed writes
from the old generation. Current open sessions must be stopped or discarded in the
confirmation; cancelling preserves the session. Room schema 5 added device-local
widget instances and command tokens; Room schema 6 adds task due minutes; Room schema
8 adds focus data. Device-local tables are excluded from every archive inventory. A successful replacement restore revokes all widget
commands, hides per-instance details and clears widget feedback in the same
transaction as the content replacement. A failed or cancelled restore leaves widget
runtime state untouched. The portable archive schema is version 7. Device-only quiet
hours and lock-screen preview privacy are not included in backups.

Native transaction, image/provider behavior, concurrency, lifecycle and power-loss
checks remain pending device validation; see [VALIDATION.md](VALIDATION.md).

TD02/TD03 task-specific backup/persistence checks pass: task CRUD/migration tests
and backup persistence tests ran on disposable API 36 emulator `emulator-5562`.
Due-time implementation adds source tests for the 5→6 migration, minute persistence,
schema-5 round-trips, exact schema-4 task-header compatibility, and invalid minute
values. Those new tests and device checks were not run in this implementation pass
at the user's request. The earlier
complete `core:data` instrumentation suite still has two Timeline ordering/page
failures; detailed results and remaining acceptance are in
[`TODO_ACCEPTANCE_2026-10-19.md`](TODO_ACCEPTANCE_2026-10-19.md).
