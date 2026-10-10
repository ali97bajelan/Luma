# Room schema history

`LumaDatabase` starts at version 1 and exports its schema through the Room Gradle
plugin. The first build generates `1.json` under this directory, in the plugin's
variant/database package folders. Commit the compiler-generated JSON files after
that build; do not hand-author identity hashes or edit an exported historical version.

No Gradle or compiler commands were run during T03 implementation, at the user's
request, so the generated version 1 JSON is still pending. Room's Gradle plugin
also makes the applicable schema directory available to instrumentation tests.

When changing an existing table, increment `LumaDatabase.VERSION`, register a
data-preserving `Migration` in `LumaDatabase.MIGRATIONS`, and retain every older
schema. Extend `SchemaMigrationTest` with representative data for the changed
tables. Its baseline test opens version 1 through the full registered migration
chain and validates that the original journal content remains readable.

Version 1 has no predecessor and therefore has no upgrade migration. Production
uses no destructive migration fallback; unsupported versions fail without deleting
records. The test sources cover exported-schema reopening and this failure policy.
