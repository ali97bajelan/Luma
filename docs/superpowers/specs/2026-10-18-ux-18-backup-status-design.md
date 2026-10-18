# UX-18: Durable last local export status

Status: implementation-ready proposal; product changes pending.
Source: [review](../../UI_UX_REVIEW_2026-10-18.md); [brief](../../UI_UX_IMPLEMENTATION_READY_2026-10-18.md). Consume UX-16 formatting, UX-13 header and UX-14 reset layout.

## Intent and approaches

Show the last known successful local export without implying that a file remains available or protected. Recommended: one app-private preference timestamp written after completed file close. A database/schema change is unnecessary; persisting URI/location adds grant/privacy complexity without proving file existence.

## Status and write contract

Initial: “No backup exported from this installation.”
After success: “Last successful export: [localized date/time]”.
Persistent qualification: “This records an export from Luma. The file may have been moved or deleted.” No green Protected badge, remote/cloud claim, verification claim or automatic-backup promise.

Record clock.instant() only after service.export completes and OutputStream.use returns successfully, including flush/close. Opening picker, URI selection, partial write, canceled picker, failed open/export/flush/close and coroutine cancellation before completion must not update timestamp. Preserve previous success on any export failure. Duplicate export while busy remains blocked.

Preference persistence failure is separate from archive-write failure: report “Backup file exported, but Luma could not save the export date.” Keep the prior durable timestamp, do not call the archive failed, and do not retry writing/exporting silently. Successful preference commit updates current display immediately and survives ViewModel recreation/relaunch.

Use app-private SharedPreferences file backup_export_history, key last_successful_export_epoch_millis. No URI, filename or content metadata stored. Absent/invalid timestamp maps to unknown; do not invent current time. Only reset/uninstall/clear-storage clears this installation metadata. In-app restore does not overwrite it, restore an old installation's timestamp, or count as export. Keep preference outside manual archive manifest/data. Android automatic backup/device transfer remain disabled.

## Presentation and restore protection

Rename export action “Export backup file”. Keep unencrypted/private-content warning directly adjacent to action; do not hide it inside About. Preserve preview counts, replace-all warning, explicit restore confirmation, preparation cancellation cleanup and failed-operation messages. Local export history persists through all transient BackupUiState resets.

Add “Export a backup first” shortcut near reset's explanatory text, above destructive Delete. It navigates to existing settings_backup; it does not open picker or start export. Only show when backup service exists; preserve reset confirmation regardless of export history. Return lands back on Privacy; no reset automatically follows export.

## Interfaces

Create ui/backup/BackupExportHistory.kt:
interface BackupExportHistory { fun lastSuccessfulExport(): Instant?; suspend fun recordSuccessfulExport(at: Instant) }
class SharedPreferencesBackupExportHistory(context: Context) : BackupExportHistory; use commit on IO and surface false as persistence failure.
Inject history and Clock into BackupViewModel, retaining its existing service and dispatcher injection. Add lastSuccessfulExport: Instant? and historyWriteFailed: Boolean to BackupUiState; copies for transient operation updates retain history.
Construct the store from application context in BackupSettingsCard's factory. Unit tests inject memory/failing implementations and fixed Clock; no Android preference calls in pure tests.

## Acceptance

No prior export/canceled picker/success/failed open/write/close/history-write failure; prior timestamp preserved; relaunch persists; restore does not change status. Reset clears metadata on disposable app only. No file-existence or remote-protection claim. Shortcut navigation cannot reset or export on its own.

