# T17 — Portable local backup and restore

Implement TASKS.md T17 in the existing offline Android app. Settings lets the user
export a ZIP through CreateDocument and choose a ZIP through OpenDocument. The
archive is unencrypted; the UI explains that it includes private text and photos.
No account, network permission, automatic upload, or broad storage permission.

## Format and compatibility

Format version 1: ZIP with manifest.properties, tables/<known-table>.tsv, and
photos/<canonical-uuid>.img. The manifest declares format, format version, database
schema version, creation time, and size/SHA-256 for every payload. TSV files use
column-name headers and typed cells: N for null, I for signed integer, F for finite
double, S followed by Base64 UTF-8 text. This preserves null versus empty, multiline
Unicode, stable IDs, timestamps, relations, source metadata and usage history.
Only schema versions 1 and 2 are accepted; v1 lacks the two v2 session tables.
Import into the current generated Room schema applies that additive compatibility
without executing archive SQL. Unknown format/schema versions fail without writes.

All 19 application tables are exported from one Room transaction. Referenced
photo files are required; missing files fail export explicitly. Unreferenced draft
files and external person photo URIs are not copied. Person photo_uri values are
retained as metadata, without promising availability on a different device.

## Restore and concurrency

Validate entry names, duplicates, bounded expanded sizes, hashes, required tables,
column schemas, value types, entity invariants, child/parent type/source agreement, references and exact photo coverage
in private staging before showing a replacement confirmation. No merge mode: user
confirms replacement of current saved records. Dismissal discards staging.

The production photo store and backup engine share a mutex. Export holds it around
its database snapshot and photo copies. Restore publishes fsynced photos to fresh
UUID paths, then replaces known application tables in one Room transaction. On
failure the database rolls back and the new files are removed; old files are never
overwritten. On success derived summaries become stale and integration imports
are disabled with device-specific cursors reset; saved imported records remain.
Existing draft leases are preserved. Room invalidation refreshes active screens.

## Validation

Pure JVM tests cover TSV and archive round trips, traversal, duplicate/extra/missing
entries, corrupt hashes, unsupported versions and expanded-size bounds. Room device
tests cover full relation/photo round trip, replacement, v1 compatibility, malformed
rows, and injected transaction failure retaining old records/photos. UI tests cover
confirmation cancellation and action/status states. Run all existing JVM suites,
debug/test APK builds and lint. Record unavailable device checks explicitly.

Compatibility: native usage bucket dates may differ from historical event timestamps;
retain both independently, matching the existing T16 usage semantics.
