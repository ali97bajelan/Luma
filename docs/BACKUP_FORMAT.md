# Luma portable backup — format version 1

Settings → Backup and restore exports saved application tables and referenced
photos using the system file picker. Choose backup to restore validates a file and
shows its creation time, event count and photo count before asking to replace the
current saved data. There is no merge mode. Export current data first to retain it.

Backups are unencrypted and contain private text, names and photos. No automatic
upload occurs; the selected document provider controls the destination. No broad
storage permission is requested. The picker follows Android's
[Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files).
An interrupted or failed export can leave an incomplete destination file; success
is reported only after the archive and output stream complete. Missing or corrupt
referenced photos abort export rather than silently omitting them.

## Archive layout

- `manifest.properties`: Java Properties syntax. Required `format=luma-backup`,
  `format_version=1`, `schema_version=1` or `2`, `created_at` (UTC epoch milliseconds),
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

Schema 2 includes all 19 application tables: life_event, emotion, person, place,
tag, mood_entry, journal_entry, memory, activity_record, app_usage_record,
media_attachment, mood_emotion, event_person, event_place, event_tag, daily_summary,
integration_state, usage_session and usage_session_window. Schema 1 omits the final
two tables. Import into the generated current Room schema provides that additive
compatibility, keeping older aggregate usage without inventing sessions. Unknown
format/schema versions fail safely; future versions require an explicit reader.
The live database schema remains version 2.

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
coverage are checked. Incomplete event envelopes remain supported. Native usage bounds remain independent of historical event timestamps, as in
the existing usage views. No live record
is replaced during preparation or when its confirmation is cancelled.

Export reads all tables and copies photos within one Room transaction while holding
the shared photo-store mutex; this includes committed WAL data and does not copy
a live SQLite file. Archive compression uses independent snapshot files afterward.
Restore shares the mutex with imports, previews, leases and cleanup. It writes and
syncs photos to fresh UUID paths, syncs their directory, then replaces records in
one Room transaction. Existing photo files are never overwritten. A database failure
rolls back records and removes new files. Unreferenced old files are cleaned after
commit while preserving protected draft leases. Abandoned private staging from a
previous process is removed on the next backup operation.

Restore retains event/content identities, text, timestamps, links, source metadata,
usage history and referenced photo bytes. Photo paths change intentionally. Derived
summaries become stale for recalculation. Imports are disabled, usage device cursors
and clock anchors reset, and the saved Health Connect timezone is retained with an
advanced revision to reject old requests. Last-sync evidence and saved observations
remain. The user may re-enable imports through Settings; Android permissions,
app language and external person photo URI availability are not transferred.
Unsaved editor drafts are not backup payloads.

Native transaction, image/provider behavior, concurrency, lifecycle and power-loss
checks remain pending device validation; see [VALIDATION.md](VALIDATION.md).
