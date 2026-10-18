# Room schema history

`LumaDatabase` started at version 1 and exports its schema through the Room Gradle
plugin. Commit the compiler-generated JSON snapshots for each released version;
do not hand-author identity hashes or edit an exported historical version.

Versions 1, 2 and 3 have compiler-generated JSON snapshots. Include all three in
version control. Room's Gradle plugin makes the schema directory available to
instrumentation tests after the current build exports its schema.

When changing an existing table, increment `LumaDatabase.VERSION`, register a
data-preserving `Migration` in `LumaDatabase.MIGRATIONS`, and retain every older
schema. Extend `SchemaMigrationTest` with representative data for the changed
tables. Its baseline test opens version 1 through the full registered migration
chain and validates that the original journal content remains readable.

Version 1 has no predecessor and therefore has no upgrade migration. Production
uses no destructive migration fallback; unsupported versions fail without deleting
records. The test sources cover exported-schema reopening and this failure policy.

Version 2 adds separate app-session observations and query-window metadata. The
1→2 migration retains all existing tables and content, adds the indexed session
tables, and marks derived summary caches stale. The migration test checks old
journal content and new session writes across database reopening.

Version 3 adds seven habit tables for the fixed tracking zone, habit definitions,
effective-dated schedules, individual logs, day choices, reminder configuration
and bounded reminder runtime. The 2→3 migration is additive and keeps reminders
disabled until the user enables them. `HabitMigrationTest` checks both 1→3 and
2→3 upgrades without dropping legacy rows.
