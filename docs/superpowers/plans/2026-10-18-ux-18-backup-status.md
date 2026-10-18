# Backup Export Status Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Truthful persistent last-export status and a reset-adjacent backup shortcut.
**Architecture:** Small private preference store injected into current export ViewModel; record after output closes.
**Tech Stack:** Kotlin coroutines, SharedPreferences, Clock, existing BackupService/SAF.
**Spec:** [Backup status design](../specs/2026-10-18-ux-18-backup-status-design.md)

## Global Constraints

- Keep minSdk 28, compileSdk 35, targetSdk 35, JDK 17, AGP 8.7.3, Kotlin 2.2.10 and Compose BOM 2025.08.01.
- Add no product dependency, database migration, permission, network access, telemetry or AI service.
- English, Persian and French remain available offline; preserve user-authored text, stored timestamps and timezone rules.
- UX-01, UX-02 and UX-12 are excluded; preserve their existing behavior.
- Minimum interactive touch target: 48 × 48 dp; do not reduce configured text scale.

## Review Focus

- Close/flush throws after bytes written: keep prior success (Task 1).
- Preference commit fails after archive success: separate message (Task 1).
- Picker cancel/unavailable leaves prior metadata (Task 2).
- Restore/relaunch must not import or erase local export history (Tasks 1–2).
- Backup shortcut cannot trigger reset/export and is absent without service (Task 2).

### Task 1: Durable export metadata

**Files:** Create app/src/main/java/com/luma/lifeos/ui/backup/BackupExportHistory.kt; modify BackupViewModel.kt and BackupSettingsCard.kt there. Modify app/src/test/java/com/luma/lifeos/backup/BackupViewModelTest.kt; add app/src/androidTest/java/com/luma/lifeos/backup/BackupExportHistoryTest.kt.
**Interfaces:** BackupExportHistory.lastSuccessfulExport(): Instant?, suspend recordSuccessfulExport(at: Instant). SharedPreferencesBackupExportHistory(context: Context). BackupViewModel(service: BackupService, io: CoroutineDispatcher = Dispatchers.IO, history: BackupExportHistory, clock: Clock = Clock.systemUTC()). Add lastSuccessfulExport: Instant? and historyWriteFailed: Boolean to BackupUiState. Update all constructor callers/fakes.

- [ ] Add tests using fixed “2026-10-18T12:00:00Z”: successful closed output records exactly once; failed open/write/flush/close leaves old timestamp; canceled export doesn't record; history persistence failure yields EXPORTED plus historyWriteFailed, retaining prior timestamp; duplicate busy export rejected; prepare/cancel/restore retain history.
- [ ] Run `bash gradlew :app:testDebugUnitTest --tests '*BackupViewModelTest' --offline`; new constructor/state assertions fail before implementation.
- [ ] Implement preference store with commit on IO; record only after stream use returns; isolate history-write catch from archive-write catch. Replace transient whole-state resets with copies preserving history. Preserve cancellation propagation and PreparedBackup cleanup.
- [ ] Add instrumented preference recreation test, invalid stored value fallback and check manual archive contains no backup_export_history metadata. Rerun JVM/device tests; expect exact values and no change to preview/replace semantics.
- [ ] Commit with `feat(backup): persist completed local export time`.

### Task 2: Honest status and reset shortcut

**Files:** Modify ui/backup/BackupSettingsCard.kt, ui/privacy/PrivacySettingsRoute.kt, ui/LifeOsApp.kt and values*/strings.xml. Extend device backup/BackupSettingsFlowTest.kt and privacy/PrivacySettingsFlowTest.kt.
**Interfaces:** Add onBackup: (() -> Unit)? = null to PrivacySettingsRoute/content; wire only when backup != null. BackupCardContent consumes Task 1 state without file-location metadata.

- [ ] Add UI tests for initial/success/prior-success-after-failure/history-write-failure text; warning adjacent to Export backup file; picker cancel unchanged; explicit restore preview/confirmation still required; backup shortcut navigates once and calls neither reset nor export; absent service hides shortcut.
- [ ] Run affected device classes and observe missing status/shortcut.
- [ ] Implement localized status/qualification/action labels and error distinction using UX-16 date/time policy; add privacy shortcut above Delete with existing reset state guards. Keep no URI/existence assertion.
- [ ] Run app JVM tests, APK builds and isolated backup/privacy/navigation device classes. Exercise actual SAF cancel/success/failure and restore only on disposable fixtures; record skipped provider checks. Verify preference survives relaunch and in-app restore.
- [ ] Commit with `feat(backup): explain export history near backup and reset actions`.

