# Settings, privacy, integrations and launcher widgets

All 52 cases below are **NOT RUN**. This is a source-derived execution script, not evidence that behavior passed. Priorities: P0 loss/privacy/identity; P1 core operation/recovery; P2 presentation/platform variation. Levels: MANUAL = installed app; DEVICE = installed app plus Android/launcher; HARNESS = existing automated test, isolated test data. A missing external prerequisite makes that case BLOCKED, never PASS.

## Baseline and fixtures

Use a disposable Android device/emulator and disposable files only. Start in English, Light, Asia/Tehran, Monday D=2026-11-16 at local noon. Disable automatic date/time if permitted. If that clock cannot be set, record the actual local date and shift **all** D-relative dates together; do not mix dates. Keep each entity type at or below 50 records; this script creates at most 10 tasks, 3 habits, 2 memories and 7 health day observations. Do not run scale tests or generate large archives.

Use the productionDebug QA build (application ID `com.luma.lifeos.qa`, minSdk 28, targetSdk 35). Keep release data separate. Record app commit/build, API, launcher/version, locale, time zone, clock, font/display scale and permission state. A case starts from its named fixture, not the previous case's mutations. Restore an emulator snapshot or recreate that fixture before each case. Screenshots must contain no real personal data; record a screenshot-blocked result without disabling protection. Save evidence as `<case>-<step>-<state>`; record actual result, counts, identities and issue link.

Navigation shorthand: **Settings** = More (`nav_more`) → Settings (`more_settings`). A tag in parentheses is an automation locator for the visible control, not text to type. Scroll the page until the named control is visible. Force-stop/relaunch means Android Settings → Apps → Luma → Force stop → launch its icon; do not clear storage unless the case explicitly requests reset.

Fixtures:

- **F0**: clean app sandbox; no user records; imports Off; app lock disabled; no Luma widgets; permissions denied. Launch once and choose English/Light in Settings → Appearance & language.
- **F1**: from F0 create two tasks in More → Tasks → New task: `SYS Task A`, due D, and `SYS Task B`, due D+1; no due times/reminders. Use title `todo_editor_title`, Change due date `todo_editor_change_due_date`, choose the date, Select `todo_editor_select_date`, Save `todo_editor_save`. Create two daily habits through Habits → add: `SYS Water` target 2, unit `cups`, start D; `SYS Walk` target 1, unit `walk`, start D; all seven weekdays; reminders Off. Name/target/unit locators are `habit_name`, `habit_target`, `habit_unit`; Save is `habit_save`. Create one Memory using quick capture → Memory: title `SYS Memory`, note `Disposable backup note`, at D 11:00, no photo. Verify exactly 2 tasks, 2 habits, 0 habit logs, 1 memory. Capture their displayed detail identities or archive IDs before testing. No focus session is open.
- **FB**: F1 plus successfully export `SYS-baseline.zip` into a disposable folder using BAK-001. Preserve an untouched host copy. The backup owns the F1 identities. Then edit Task A title to `SYS Task A live edit` and add `SYS Live-only` due D (3 live tasks, still 2 archive tasks). This is a same-ID collision fixture, not a merge fixture.
- **FW**: F1 plus launcher widgets: two Luma Tasks instances T1/T2 and one Luma Habits H1, saved through configuration; label their physical positions in evidence. No task complete; no logs. Do not guess widget IDs; when needed record IDs from Android Studio App Inspection → Database Inspector → `widget_instance`.
- **FL**: F1 plus lock set up as in LOCK-001: Password `Luma-QA-2026!`, whole app, Every time, screenshot protection On. Save the generated recovery code in an offline disposable QA note (never attach the code to an issue).
- **FU** external prerequisite: real Usage access settings available; no other user profile; a deterministic test app with package and label recorded. For exact numeric import or failure oracles use the named HARNESS case, not stopwatch estimates.
- **FH** external prerequisite: Health Connect supported/installed and a disposable provider app capable of writing **and deleting** Steps records in Asia/Tehran. Provider must contain no unrelated steps. Seed only D−1 09:00–09:10 = 100 steps and D 09:00–09:10 = 200 steps; note provider record IDs. Do not confuse a null aggregate with an explicit zero.
- **FX**: host file `SYS-invalid.zip` containing exactly ASCII `not a zip`; copy it to device Downloads. For checksum corruption, host-copy FB archive to `SYS-tampered.zip`, change one byte of `tables/todo_task.tsv` while rebuilding the ZIP, leave manifest bytes untouched, and copy to Downloads. Keep the archive tiny. If no safe ZIP editor is available use BAK-006's existing harness instead; do not improvise modifying the live database.

Persisted oracle: after each case reopen the relevant screen and, where specified, force-stop/relaunch. Exact identity/count checks use a fresh backup's TSVs (read-only host inspection) or the named existing test's repository assertions. App Inspection is optional for counts and must not mutate live tables. Exporting itself may require lock authorization. Cleanup code **C0** means restore F0/snapshot and remove exported test documents; **C1** means restore F1/snapshot; **CW** means remove all test widgets then restore F1; **CH** means delete the two provider records, revoke Luma's Health Connect access, turn imports Off and restore F0. Preserve failure evidence before cleanup.

## Source and test map

- **S-SYS**: [settings hub](../../../app/src/main/java/com/luma/lifeos/ui/settings/SettingsRoute.kt), [appearance](../../../app/src/main/java/com/luma/lifeos/ui/theme/ThemeSettingsCard.kt), [language](../../../app/src/main/java/com/luma/lifeos/language/LanguageSettingsCard.kt), [calendar](../../../app/src/main/java/com/luma/lifeos/ui/settings/CalendarSettingsCard.kt), [week start](../../../app/src/main/java/com/luma/lifeos/ui/settings/WeekStartSettingsCard.kt), [activity startup](../../../app/src/main/java/com/luma/lifeos/MainActivity.kt), [manifest](../../../app/src/main/AndroidManifest.xml). Tests: [SettingsHubFlowTest](../../../app/src/androidTest/java/com/luma/lifeos/settings/SettingsHubFlowTest.kt), [ThemeSettingsTest](../../../app/src/androidTest/java/com/luma/lifeos/settings/ThemeSettingsTest.kt), [LanguageFlowTest](../../../app/src/androidTest/java/com/luma/lifeos/language/LanguageFlowTest.kt), [CalendarFlowTest](../../../app/src/androidTest/java/com/luma/lifeos/calendar/CalendarFlowTest.kt).
- **S-BAK**: [backup UI](../../../app/src/main/java/com/luma/lifeos/ui/backup/BackupSettingsCard.kt), [operation state](../../../app/src/main/java/com/luma/lifeos/ui/backup/BackupViewModel.kt), [archive validation](../../../core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupArchive.kt), [replace transaction](../../../core/data/src/main/kotlin/com/luma/lifeos/data/backup/RoomBackupService.kt), [domain validation](../../../core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupValidation.kt), [privacy/reset](../../../app/src/main/java/com/luma/lifeos/ui/privacy/PrivacySettingsRoute.kt), [OS reset boundary](../../../app/src/main/java/com/luma/lifeos/ui/privacy/AppDataReset.kt). Tests: [BackupSettingsFlowTest](../../../app/src/androidTest/java/com/luma/lifeos/backup/BackupSettingsFlowTest.kt), [BackupViewModelTest](../../../app/src/test/java/com/luma/lifeos/backup/BackupViewModelTest.kt), [BackupPersistenceTest](../../../core/data/src/androidTest/kotlin/com/luma/lifeos/data/backup/BackupPersistenceTest.kt), [BackupCodecTest](../../../core/data/src/test/kotlin/com/luma/lifeos/data/backup/BackupCodecTest.kt), [PrivacySettingsFlowTest](../../../app/src/androidTest/java/com/luma/lifeos/privacy/PrivacySettingsFlowTest.kt).
- **S-INT**: [usage UI](../../../app/src/main/java/com/luma/lifeos/ui/usage/UsageSettingsRoute.kt), [health UI](../../../app/src/main/java/com/luma/lifeos/ui/health/HealthSettingsCard.kt), [availability](../../../app/src/main/java/com/luma/lifeos/ui/integrations/IntegrationAvailability.kt), [usage sync](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/usage/UsageSyncer.kt), [health sync](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/health/HealthSyncer.kt), [health day identity](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/health/HealthModels.kt), [six-hour usage scheduling](../../../integration/usage/src/main/kotlin/com/luma/lifeos/integration/usage/UsageWorkScheduler.kt), [usage worker](../../../app/src/main/java/com/luma/lifeos/usage/UsageWorker.kt). Tests: [UsageSettingsFlowTest](../../../app/src/androidTest/java/com/luma/lifeos/usage/UsageSettingsFlowTest.kt), [HealthSettingsFlowTest](../../../app/src/androidTest/java/com/luma/lifeos/health/HealthSettingsFlowTest.kt), [UsageSyncerTest](../../../core/domain/src/test/kotlin/com/luma/lifeos/domain/usage/UsageSyncerTest.kt), [HealthSyncerTest](../../../core/domain/src/test/kotlin/com/luma/lifeos/domain/health/HealthSyncerTest.kt), [ForegroundRefreshFlowTest](../../../app/src/androidTest/java/com/luma/lifeos/integration/ForegroundRefreshFlowTest.kt).
- **S-WID**: [widget discovery](../../../app/src/main/java/com/luma/lifeos/ui/widgets/WidgetSettingsRoute.kt), [configuration](../../../app/src/main/java/com/luma/lifeos/widget/WidgetConfigurationActivity.kt), [rendering](../../../app/src/main/java/com/luma/lifeos/widget/WidgetRenderer.kt), [intents](../../../app/src/main/java/com/luma/lifeos/widget/WidgetIntents.kt), [command store](../../../core/data/src/main/kotlin/com/luma/lifeos/data/widget/RoomWidgetStore.kt), [command executor](../../../core/data/src/main/kotlin/com/luma/lifeos/data/widget/WidgetCommandExecutor.kt), [system refresh](../../../app/src/main/java/com/luma/lifeos/widget/WidgetSystemReceiver.kt). Tests: [WidgetIntentsTest](../../../app/src/androidTest/java/com/luma/lifeos/widget/WidgetIntentsTest.kt), [WidgetConfigurationContentTest](../../../app/src/androidTest/java/com/luma/lifeos/widget/WidgetConfigurationContentTest.kt), [WidgetRendererAcceptanceTest](../../../app/src/androidTest/java/com/luma/lifeos/widget/WidgetRendererAcceptanceTest.kt), [WidgetStoreAcceptanceTest](../../../core/data/src/androidTest/kotlin/com/luma/lifeos/data/WidgetStoreAcceptanceTest.kt).
- **S-LOCK**: [settings/enrollment](../../../app/src/main/java/com/luma/lifeos/ui/privacy/AppLockSettingsRoute.kt), [unlock gate](../../../app/src/main/java/com/luma/lifeos/ui/privacy/AppLockGate.kt), [recovery](../../../app/src/main/java/com/luma/lifeos/ui/privacy/AppLockRecoveryRoute.kt), [restricted capture](../../../app/src/main/java/com/luma/lifeos/ui/privacy/RestrictedCaptureRoute.kt), [credential rules](../../../app/src/main/java/com/luma/lifeos/lock/LockCredentialInputPolicy.kt), [retry rules](../../../app/src/main/java/com/luma/lifeos/lock/LockRetryPolicy.kt), [window privacy](../../../app/src/main/java/com/luma/lifeos/lock/LockWindowPrivacy.kt), [coverage/relock](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/lock/LockPolicy.kt), [navigation integration](../../../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt). Tests: [LockCredentialIntegrationTest](../../../app/src/androidTest/java/com/luma/lifeos/lock/LockCredentialIntegrationTest.kt), [LocalLockStoreTest](../../../app/src/androidTest/java/com/luma/lifeos/lock/LocalLockStoreTest.kt).

HARNESS execution prerequisite **QA-ENV-LOCK-HARNESS**: app instrumentation is BLOCKED pending source/test compatibility review, migration of stale LockCredentialIntegrationTest calls if required, and a successful productionDebug test-APK compile (see execution gaps). Build verification is NOT RUN; this is a source-level incompatibility finding, not an observed compiler failure. A method filter does not bypass compilation of the other androidTest sources. Do not report this prerequisite as resolved without compiling the test APK. Data-module/domain/app-unit commands remain independently specified. Compatible disposable connected device for instrumentation, Android SDK/Gradle dependencies available, no concurrent test run. Execute from repository root. Define these shell helpers in the QA terminal; they are documentation only and have not been executed:

```bash
app_case() { ./gradlew :app:connectedProductionDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$1"; }
data_case() { ./gradlew :core:data:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$1"; }
domain_case() { ./gradlew :core:domain:test --tests "$1"; }
app_unit_case() { ./gradlew :app:testProductionDebugUnitTest --tests "$1"; }
```

Use only the exact method filters below. Do not replace them with the entire WidgetStoreAcceptanceTest class: that class also contains a >500-row scale case outside this plan's limit. Existing tests may use their own historical/fixed clocks; record that as HARNESS clock evidence, without substituting those dates into manual fixtures.

## Appearance, language, calendar and startup

### SYS-001 — P1 · MANUAL · F1 · NOT RUN
Source: S-SYS. Settings return stack and selected day.

1. On Today select D−1; open Settings. Expected: Settings hub, no record mutation.
2. Open Appearance & language, Back; Backup, Back; Privacy, Back; Integrations, Back; Widgets, Back. Expected at each: focused page has its own Back; Back returns to hub; primary navigation/quick capture are absent on focused settings pages.
3. Back to More → Today. Expected: D−1 remains selected, not D.
Oracle: 2 tasks/2 habits/1 memory unchanged after relaunch. Evidence: hub, each page and returned date. Cleanup C1. Reference: SettingsHubFlowTest.focusedPageBackReturnsToHub.

### SYS-002 — P1 · DEVICE · F1 · NOT RUN
Source: S-SYS. Persist explicit Light and Dark.

1. Settings → Appearance & language → Dark (`theme_dark`). Expected: current page changes to obsidian scheme; Dark selected.
2. Force-stop/relaunch → same page. Expected: Dark still selected.
3. Choose Light (`theme_light`), relaunch. Expected: warm-paper Light, Light selected.
Oracle: record counts unchanged; no green palette fallback. Evidence: both palettes and selected radios. Cleanup C1.

### SYS-003 — P1 · DEVICE · F1 · NOT RUN
Source: S-SYS. Follow system.

1. Set Android display Dark; choose Follow system (`theme_system`) in Luma. Expected: Dark presentation, System selected.
2. Switch Android display to Light and resume Luma. Expected: Light presentation; System remains selected.
3. Relaunch. Expected: follows current Android Light.
Oracle: 2 tasks remain; saved theme is System. Evidence: Android mode plus app selection. Cleanup: return Android Light, C1.

### SYS-004 — P1 · DEVICE · F1 · NOT RUN
Source: S-SYS. French/English persistence.

1. Appearance & language → Français (`language_fr`). Expected: French labels; same settings section after recreation.
2. Back → More → Tasks; open SYS Task A. Expected: user title unchanged; date/controls localized, one task identity.
3. Relaunch → settings; choose English (`language_en`). Expected: English returns; French choice survived until explicit change.
Oracle: exactly 2 tasks with original names/due dates. Evidence: French settings, task and English return. Cleanup C1.

### SYS-005 — P1 · DEVICE · F1 · NOT RUN
Source: S-SYS. Persian RTL and independent preferences.

1. Set Gregorian calendar explicitly, Monday week start; choose فارسی (`language_fa`). Expected: RTL text/navigation, Persian labels, selected Gregorian preference retained.
2. Open Tasks and Task A. Expected: title still SYS Task A; due date represents D; editing controls reachable with keyboard.
3. Set font scale 200%, reopen Appearance & language and scroll to all options. Expected: no clipped primary actions; offline fonts render Persian glyphs; selected options announced by TalkBack.
Oracle: 2 tasks, no date conversion mutation. Evidence: RTL at normal/large text. Cleanup: normal font, English, C1.

### SYS-006 — P1 · MANUAL · F1 · NOT RUN
Source: S-SYS. Gregorian/Persian represent one date.

1. Appearance & language → Gregorian (`calendar_preference_gregorian`); open Calendar and select D. Expected: Gregorian date is Nov 16 2026 and Task A belongs to that day.
2. Choose Persian (`calendar_preference_persian`); return to the same date. Expected: same absolute day and same Task A; different calendar labels.
3. Relaunch and reopen settings. Expected: Persian remains selected; change back to Gregorian.
Oracle: Task A due ISO date stays 2026-11-16 in fresh backup TSV; count 2. Evidence: both calendar renderings and TSV date. Cleanup C1.

### SYS-007 — P2 · MANUAL · F1 · NOT RUN
Source: S-SYS. All supported week starts.

1. Appearance & language → select Saturday (`week_start_saturday`); open Calendar. Expected: first weekday column Saturday; Task A stays D.
2. Repeat for Sunday, Monday, Tuesday, Wednesday, Thursday, Friday. Expected after each: only selected radio active; grid starts that weekday and maintains date identities.
3. Select Monday, relaunch. Expected: Monday persists.
Oracle: Task A D, Task B D+1; counts unchanged. Evidence: seven first-column labels plus final persisted selection. Cleanup C1.

### SYS-008 — P1 · DEVICE · F0 · NOT RUN
Source: S-SYS. Clean/offline startup and unsupported URL.

1. Enable airplane mode; cold launch Luma. Expected: usable local Today, no mandatory account/onboarding/network gate and no permission requested without an action.
2. Navigate More → Settings → Appearance & language → Back. Expected: local navigation works.
3. From a QA terminal run `adb shell am start -a android.intent.action.VIEW -d 'https://example.invalid/luma-qa'`. Expected: Luma is not advertised as a handler; another handler/no-handler result is acceptable.
Oracle: 0 user records and no imports enabled after relaunch. Evidence: cold launch, permission state, intent resolution. Cleanup: airplane mode Off, C0. Manifest declares no general web deep-link filter; widget/notification entry points have separate contracts.

## Backup, replacement and reset

### BAK-001 — P0 · DEVICE · F1 · NOT RUN
Source: S-BAK. Export real portable backup.

1. Settings → Backup; read disclosure. Expected: explicitly says backup is not encrypted and includes private data.
2. Export (`backup_export`); choose disposable folder/name SYS-baseline.zip; Save in Android picker. Expected: busy state prevents duplicate Export/Choose actions; success only after output completes.
3. Host-inspect ZIP without editing. Expected: manifest format luma-backup, format_version 1, schema_version 14; tables and declared hashes; 2 todo rows, 2 habit rows, 1 memory.
4. Relaunch and reopen records. Expected: source records unchanged.
Oracle: exported IDs equal live IDs; no duplicate rows. Evidence: disclosure, success, archive inventory/counts (redact private contents). Cleanup C1; retain archive only for FB.

### BAK-002 — P1 · DEVICE · F1 · NOT RUN
Source: S-BAK. Export/restore picker cancellation.

1. Export → Android picker → Back without selecting. Expected: returns to Backup, no exported-success message for that attempt.
2. Choose backup (`backup_choose`) → Back. Expected: no replacement confirmation; Export and Choose usable.
3. Relaunch. Expected: all F1 records remain.
Oracle: 2 tasks/2 habits/1 memory; no new committed document (remove any provider-created empty file). Evidence: both cancellations. Cleanup C1.

### BAK-003 — P0 · DEVICE · FB · NOT RUN
Source: S-BAK. Preview cancellation never replaces.

1. Choose SYS-baseline.zip. Expected: replacement preview gives archive counts (2 tasks, 2 habits, 0 logs, 0 focus) and warns about replacing saved records/catalogs/imported history.
2. Tap Cancel (`backup_cancel`). Expected: preview closes; actions reenable.
3. Reopen Tasks. Expected: SYS Task A live edit and SYS Live-only still present.
Oracle: 3 live tasks with unchanged IDs; archive remains 2. Evidence: preview and post-cancel task list. Cleanup C1.

### BAK-004 — P0 · DEVICE · FB · NOT RUN
Source: S-BAK. Same IDs are replaced, never merged.

1. Choose SYS-baseline.zip → Replace (`backup_confirm`) once. Expected: busy state; no duplicate confirmation action.
2. Wait for restored result; open Tasks/Habits/Memory. Expected: SYS Task A original name restored, SYS Live-only absent, Task B present; 2 habits/1 memory.
3. Relaunch; export SYS-after-restore.zip and inspect identities. Expected: exactly archive IDs, 2 task rows, not 4/5; task revision tokens refreshed.
Oracle: old/live edits gone by design; stable entity IDs retained. Evidence: before/after TSV IDs/counts and success. Cleanup C1.

### BAK-005 — P0 · DEVICE · FB + FX · NOT RUN
Source: S-BAK. Invalid/corrupted archive rejection with recovery.

1. Choose SYS-invalid.zip. Expected: invalid-backup message; no preview or Replace.
2. Choose SYS-tampered.zip. Expected: checksum rejection before live replacement, no partial rows.
3. Choose untouched SYS-baseline.zip, cancel preview. Expected: valid preview returns; retry path usable.
Oracle: live Task A live edit and Live-only remain, 3 tasks after relaunch. Evidence: both rejected files/hash, error and successful retry preview. Cleanup C1; delete bad files.

### BAK-006 — P0 · HARNESS · isolated test DB · NOT RUN
Source: S-BAK. Checksummed invalid domain/reference rows rejected.

1. Run `data_case 'com.luma.lifeos.data.backup.BackupPersistenceTest#checksummedInvalidDomainRowsAndReferencesAreRejectedBeforeReplacement'`. Expected: selected test runs, no unrelated scale suite.
2. Inspect test result/assertions. Expected: malformed domain data and broken references rejected even with valid archive checksums; original live records survive.
Oracle: existing repository assertions are authoritative; 0 selected-test failures. Evidence: command, XML/report and assertion names. Cleanup: test teardown; C0 if installed app state changed. No production injection menu is assumed.

### BAK-007 — P0 · HARNESS · isolated test DB · NOT RUN
Source: S-BAK. Commit failure preserves existing files/rows.

1. Run `data_case 'com.luma.lifeos.data.backup.BackupPersistenceTest#failedCommitPreservesCurrentRecordsAndPhotos'`. Expected: existing trigger-based failure seam executes.
2. Inspect result. Expected: rejected replacement retains current records/photos, does not publish partial replacement.
3. Run `data_case 'com.luma.lifeos.data.backup.BackupPersistenceTest#previouslyRestoredPhotosCanBeExportedAndValidatedAgain'`. Expected: restored photos remain exportable/valid.
Oracle: selected persistence assertions pass; row/file identity preserved. Evidence: both reports. Cleanup: built-in teardown. Physical out-of-space failure is a separate BLOCKED environmental variant until a safe disposable volume is supplied.

### BAK-008 — P1 · HARNESS · BackupViewModel fake service · NOT RUN
Source: S-BAK. Duplicate/failing operations close resources and recover.

1. Run `app_unit_case 'com.luma.lifeos.backup.BackupViewModelTest.duplicateExportIsIgnoredAndStreamsCloseOnFailure'`. Expected: duplicate ignored, failed output closed.
2. Run `app_unit_case 'com.luma.lifeos.backup.BackupViewModelTest.confirmationRunsOnceAndFailureExposesRetryWithoutRetainingPreview'`. Expected: one restore attempt; failure releases preview and permits fresh selection.
3. Run `app_unit_case 'com.luma.lifeos.backup.BackupViewModelTest.exportSuccessIsReportedOnlyAfterOutputCompletes'`. Expected: no premature success.
Oracle: no failed selected tests; export/restore counters asserted in harness. Evidence: three reports. Cleanup: test-owned dispatcher/resources.

### BAK-009 — P1 · DEVICE · F1, disposable focus session · NOT RUN
Source: S-BAK. Paused focus export choices.

1. More → Focus; start a stopwatch session, wait 5 seconds, Pause; open Backup → Export. Expected: paused-session disclosure; Cancel leaves session paused.
2. Export → Exclude paused focus (`backup_exclude_paused_focus`); save SYS-excluded.zip. Expected: session remains paused; archive has 0 focus sessions.
3. Export → Stop and include (`backup_stop_focus_export`); save SYS-included.zip. Expected: session becomes terminal; archive has exactly 1 focus session and its run.
Oracle: 2 tasks/2 habits unchanged; no open focus run in portable archive. Evidence: pause, both choices, archive counts. Cleanup C1 and remove documents. A running session must produce focus-action failure rather than an invalid archive; pause before retry.

### BAK-010 — P0 · DEVICE · F1 · NOT RUN
Source: S-BAK. Privacy reset cancellation and actual reset.

1. Settings → Privacy → App settings (`privacy_app_settings`). Expected: Android's Luma app-details page, not deletion; Back returns.
2. Delete all local data (`privacy_delete`) → Cancel (`privacy_cancel`). Expected: all F1 data remains.
3. Delete → Delete and reset Luma (`privacy_confirm`). Expected: Android stops/clears app; accepted-request status alone is not proof of erasure.
4. Launch icon. Expected: clean local state: 0 tasks/habits/memories, imports Off, lock absent, settings reset; exported external backups still exist.
Oracle: verify fresh lists after process restart; sandbox clear is destructive and intentional. Evidence: cancellation counts and fresh-launch state. Cleanup C0; remove external QA backups separately.

### BAK-011 — P0 · HARNESS · isolated backup DB · NOT RUN
Source: S-BAK. Restore saved history with imports disabled.

1. Run `data_case 'com.luma.lifeos.data.backup.BackupPersistenceTest#restoreDisablesImportsAndRetainsSavedUsageHistory'`. Expected: restored history retained but automatic usage import Off.
2. Run `data_case 'com.luma.lifeos.data.backup.BackupPersistenceTest#healthRestoreRetainsImportZoneAndRejectsOldRequestsAfterReenable'`. Expected: durable health zone preserved, old request revision cannot commit after replacement/reenable.
Oracle: exact repository state assertions pass, no invented extra history. Evidence: selected reports. Cleanup: teardown. Manual follow-up after BAK-004: open Integrations; imports must be Off even if FB was exported with enabled imports.

## Usage and Health Connect integrations

### INT-001 — P1 · DEVICE · F0 + FU · NOT RUN
Source: S-INT. Opt-in is independent of Android permission.

1. Settings → Integrations. Expected: Automatic app usage imports Off; Android access Not allowed; no saved-history browser on this settings page.
2. Turn On (`usage_enabled`). Expected: import preference On, access still Not allowed, Import now absent.
3. Grant access (`usage_grant`) → explanation → Cancel (`permission_cancel`). Expected: no Android settings opened; On preference remains, permission denied.
Oracle: 0 usage values fabricated; no access grant from switch alone. Evidence: separate preference/access statuses and cancel. Cleanup: switch Off, C0.

### INT-002 — P1 · DEVICE · F0 + FU · NOT RUN
Source: S-INT. Grant, foreground refresh, revoke and regrant.

1. Turn usage imports On; Grant → Continue (`permission_continue`); enable Luma in Android Usage access. Expected: explicit OS action required.
2. Back to Luma. Expected: access updates to Allowed on resume without needing process restart; Import now available.
3. Revoke in Android settings; return. Expected: Not allowed, Import now absent, Grant/Refresh usable.
4. Regrant and tap Refresh (`usage_refresh`). Expected: Allowed; Import now returns.
Oracle: preference remains On through permission changes; saved data (if any) never deleted. Evidence: four status states. Cleanup: Off/revoke, C0.

### INT-003 — P0 · HARNESS · UsageSettingsFlowTest fixture · NOT RUN
Source: S-INT. Exact usage value and opt-out retention.

1. Run `app_case 'com.luma.lifeos.usage.UsageSettingsFlowTest#optionalAccessImportAndOptOutKeepDurableDailyValues'`. Expected: fake provider returns exactly 5,125 ms for one app/day.
2. Inspect assertions. Expected: import stores 5,125 ms once, opt-out survives UI restoration, lastSync and saved report retained; settings has no daily browser.
Oracle: fresh repository still reports 5,125 ms, not 0 or 10,250. Evidence: test report. Cleanup: in-memory DB teardown.

### INT-004 — P0 · HARNESS · UsageSettingsFlowTest fixture · NOT RUN
Source: S-INT. Empty/revoked usage is unknown, not zero.

1. Run `app_case 'com.luma.lifeos.usage.UsageSettingsFlowTest#emptyAndRevokedAccessDoNotProduceZeroUsageOrDiscardSavedData'`. Expected: empty provider response shows no-new-data and retains null checkpoint/report initially.
2. Inspect subsequent seeded/revoked assertions. Expected: saved 5,125 ms persists after revocation; Refresh/Grant remain usable.
Oracle: no synthetic zero or duplicate usage row. Evidence: report/assertions. Cleanup: teardown.

### INT-005 — P1 · HARNESS · usage fake failure seam · NOT RUN
Source: S-INT. Import failure and retry controls.

1. Run `app_case 'com.luma.lifeos.usage.UsageSettingsFlowTest#importFailuresKeepAccessRefreshAndRetentionControlsUsable'`. Expected: visible import-failed message, Retry import and Refresh enabled, retention disclosure accessible.
2. Run `domain_case 'com.luma.lifeos.domain.usage.UsageSyncerTest.transientAccessFailuresRemainRetryableAndNeverAdvanceCheckpoint'`. Expected: transient failure does not advance checkpoint; subsequent permitted attempt can recover.
Oracle: selected assertions pass; no failed-import data committed. Evidence: UI and domain reports. Cleanup: test teardown.

### INT-006 — P0 · HARNESS · usage controlled query/clock seams · NOT RUN
Source: S-INT. Cancellation, opt-out and reboot safety.

1. Run `domain_case 'com.luma.lifeos.domain.usage.UsageSyncerTest.disablingDuringANativeQueryDoesNotWaitOrCommitThePendingImport'`. Expected: Off cancels/prevents late commit.
2. Run `domain_case 'com.luma.lifeos.domain.usage.UsageSyncerTest.cancellationPropagatesWithoutMarkingTheIntegrationFailed'`. Expected: cancellation is not a failed import.
3. Run `domain_case 'com.luma.lifeos.domain.usage.UsageSyncerTest.rebootQuarantinesOldNativeIntervalsWithoutInventingZeroUsage'`. Expected: persisted clock epoch rejects old intervals.
Oracle: history/checkpoint assertions unchanged on rejected work. Evidence: three reports. Cleanup: test-owned state. Real background scheduler runs are not accelerated by changing wall time.

### INT-007 — P1 · DEVICE · F0 + FH · NOT RUN
Source: S-INT. Health permission cancel/deny/grant.

1. Integrations → Steps On (`health_enabled`), Grant (`health_access`) → Cancel explanation. Expected: no OS permission prompt; access Not allowed.
2. Grant → Continue; deny/back out of OS Health Connect prompt. Expected: Not allowed; no import and no saved step event.
3. Repeat and grant Steps read permission. Expected: access Allowed on return, Import available.
Oracle: On preference alone does not create steps; existing manual records unaffected. Evidence: permission decisions/status. Cleanup CH.

### INT-008 — P0 · DEVICE · F0 + FH · NOT RUN
Source: S-INT. Seven-day refresh corrects one identity.

1. Grant Steps read, turn imports On, Import (`health_import`). Expected: D−1=100 and D=200 steps appear in Timeline; import zone Asia/Tehran displayed.
2. In provider replace only D's 200 record with 80 for same interval; import again. Expected: D becomes 80, D−1 remains 100.
3. Import a third time, relaunch. Expected: no additional copies.
Oracle: at most one health event per non-null day; sourceReference `health:steps:daily:Asia/Tehran:2026-11-16` stable, value 80 not 280/360; total non-null fixture days 2. Null other days are not zero events. Evidence: provider IDs, day detail and export/sourceReference. Cleanup CH.

### INT-009 — P0 · DEVICE · F0 + FH, INT-008 imported · NOT RUN
Source: S-INT. Health opt-out/revocation retain saved steps and zone.

1. Turn Steps Off. Expected: Off and management still available; saved D/D−1 events remain.
2. Change device zone to UTC; turn Steps On and import. Expected: original import zone Asia/Tehran retained; no second UTC identity for same fixture day.
3. Revoke Luma Steps permission in Health Connect; resume. Expected: Not allowed, Import absent; Manage/Refresh usable; saved values remain.
Oracle: same 2 imported day identities and values after relaunch. Evidence: retained zone, status and event IDs. Cleanup: restore Asia/Tehran; CH.

### INT-010 — P0 · HARNESS · health controlled response seam · NOT RUN
Source: S-INT. Partial failure, zero/null and cancellation.

1. Run `domain_case 'com.luma.lifeos.domain.health.HealthSyncerTest.partialAndDuplicateResponsesCannotDeleteSavedHistory'`. Expected: incomplete/conflicting batch rejected atomically.
2. Run `domain_case 'com.luma.lifeos.domain.health.HealthSyncerTest.nullAndZeroRemainDistinctAndSuccessfulEmptyReadCommits'`. Expected: explicit zero is valid, null remains unknown.
3. Run `domain_case 'com.luma.lifeos.domain.health.HealthSyncerTest.optOutCancelsPendingProviderQuery'`. Expected: late query cannot repopulate data after Off.
Oracle: repository/checkpoint assertions pass, no partial deletion. Evidence: reports. Cleanup: teardown.

### INT-011 — P1 · HARNESS + DEVICE · unsupported provider fixture · NOT RUN
Source: S-INT. Unavailable/needs-update states and ordinary background scheduling.

1. Run `domain_case 'com.luma.lifeos.domain.health.HealthSyncerTest.deniedUnsupportedAndUpdateRequiredNeverQueryOrCommit'`. Expected: denied/unavailable/update-required statuses perform no provider query/commit.
2. On disposable device without Health Connect support, open Integrations. Expected: unavailable explanation, ordinary settings/navigation usable. If provider requires update, Install action may open store; Back preserves data and preference.
3. On supported FU device enable usage imports, inspect Android Studio Background Task Inspector. Expected: one unique periodic usage work with 6-hour interval (KEEP), no network constraint; Off cancels it. Do not wait six hours or assert immediate execution.
Oracle: no Health periodic worker contract; no user-record deletion; unique work count 1 while enabled/0 active after cancel. Evidence: state and work inspector. Cleanup C0. DEVICE branches BLOCKED when those platform states/inspector are unavailable; record them separately.

## Launcher widgets

### WID-001 — P1 · DEVICE · F1, pin-capable launcher · NOT RUN
Source: S-WID. Discovery and cancel pin/configuration.

1. Settings → Widgets. Expected: Habits/Tasks examples, Add buttons, privacy/cache warning; no live instances initially.
2. Add Luma Tasks; cancel launcher pin prompt. Expected: no active configured Tasks widget.
3. Add again; approve pin; configuration → Cancel. Expected: no successful configured instance; launcher may retain/remove an unconfigured placeholder—record launcher behavior.
4. Use launcher widget picker to add Tasks and Save. Expected: one live configured widget with F1 task rows.
Oracle: records unchanged; only saved/live instance enumerated after resume. Evidence: each prompt/configuration. Cleanup CW. Unsupported pin launchers must show unsupported guidance; complete via picker.

### WID-002 — P1 · DEVICE · FW · NOT RUN
Source: S-WID. Reconfiguration targets exact live instance.

1. Settings → Widgets. Expected: two Tasks instances distinguished by ordinal and one Habits instance.
2. Configure second Tasks → Cancel. Expected: T1/T2 remain; no record mutation.
3. Configure second Tasks → Save; resume hub. Expected: same instance count 3, not 4; only selected instance configuration updated.
Oracle: `widget_instance` IDs stable, provider kind correct. Current config only explains content and offers Save/Cancel; no mode/theme/privacy chooser is expected. Evidence: ordinals, instance IDs and launcher. Cleanup CW.

### WID-003 — P0 · DEVICE · FW · NOT RUN
Source: S-WID. Distinct header/row destinations and cold/warm launch.

1. Force-stop Luma; tap Tasks widget title. Expected: Tasks list, no task mutation.
2. Home; tap Task A row body. Expected: Task A editor/detail, not Task B; due D.
3. Home; tap H1 Water body. Expected: SYS Water detail for D; no log added.
4. Home; tap capture header on T1, dismiss; tap capture on H1. Expected each: same date-free quick capture entry, not row completion/logging; warm singleTop delivery consumed once.
Oracle: 2 open tasks, 0 logs; no duplicate editor on rotation/back. Evidence: four destinations and identities. Cleanup CW.

### WID-004 — P0 · DEVICE · FW · NOT RUN
Source: S-WID. Complete correct task and undo from widget.

1. Tap separate complete action for SYS Task A on T1. Expected: feedback names correct task; A leaves open rows; B unchanged; T2 refreshes.
2. Tap Undo in T1 feedback. Expected: A returns open; B unchanged.
3. Relaunch app → Tasks. Expected: both open, only original 2 identities.
Oracle: one completedAt transition then reversal; no new tasks. Evidence: T1/T2 before/after, in-app status. Cleanup CW.

### WID-005 — P0 · DEVICE · FW · NOT RUN
Source: S-WID. Log beyond target and undo exact log.

1. Tap H1 Water add/log action once. Expected: Water 1/2, Walk 0/1.
2. Tap Water twice more, waiting for each feedback/refresh. Expected: 3 logs (3/2 allowed), Walk unchanged.
3. Undo most recent Water feedback. Expected: Water 2/2, exactly 2 logs retained.
Oracle: habit_log count Water=2, Walk=0 after relaunch; target reached does not disable further logging. Evidence: each progress and history. Cleanup CW.

### WID-006 — P0 · DEVICE · FW · NOT RUN
Source: S-WID. Multiple widget actions retain identity.

1. Complete A on T1; wait; Undo there. Expected: both Task widgets show A open.
2. Complete B on T2; wait; Undo there. Expected: both widgets show B open; A unchanged.
3. Log Water on H1; reopen Task widgets. Expected: task feedback/actions never mutate Water/Walk; Water count 1.
Oracle: 2 open tasks, Water=1, Walk=0; T1/T2/H1 retain their own IDs. Evidence: position-to-ID map and action sequence. Cleanup CW.

### WID-007 — P1 · DEVICE · F1 plus 8 extra tasks · NOT RUN
Source: S-WID. Scroll/resize with bounded list.

1. Create SYS Scroll 01…08, all due D, no times/reminders (10 tasks total); add Tasks widget at launcher maximum practical size. Expected: scrollable collection/overflow affordance.
2. Swipe only inside rows to reach each of the 10 titles, then tap SYS Scroll 08 body. Expected: correct task opens, no implicit completion.
3. Resize to smallest launcher-supported bound and back. Expected: compact recovery/list navigation remains reachable; expanded collection returns, no stale empty action.
Oracle: exactly 10 open tasks after relaunch; no rows lost. Evidence: dimensions/screenshots, tenth-row destination. Cleanup CW then delete extra 8 or restore F1. Do not use >50/500-row scale fixtures.

### WID-008 — P1 · DEVICE · F0 · NOT RUN
Source: S-WID. Empty-library actions and first refresh.

1. Add/save Habits and Tasks widgets. Expected: empty-library creation action for each, not fictional preview records.
2. Tap Tasks create action; create SYS First task due D. Expected: exactly one task; widget refresh shows its real title.
3. Tap Habits create action; create SYS First habit daily target1 start D. Expected: exactly one habit; widget shows 0/1.
Oracle: 1 task/1 habit/0 logs; no example-data rows stored. Evidence: empty state and first saved row. Cleanup CW/C0.

### WID-009 — P1 · HARNESS · renderer failure fixture · NOT RUN
Source: S-WID. Failure shell offers safe refresh.

1. Run `app_case 'com.luma.lifeos.widget.WidgetRendererAcceptanceTest#taskReadFailureOffersRefreshAndClearsUnsafeRows'`. Expected: read failure renders recovery refresh and clears previously unsafe rows.
2. Run `app_case 'com.luma.lifeos.widget.WidgetRendererAcceptanceTest#retryNamesTaskAndUndoneClearsActionBandOnReapply'`. Expected: Retry names correct task; undone reapply removes obsolete action band.
Oracle: RemoteViews assertions pass; no fabricated count. Evidence: test reports. Cleanup: teardown. Physical storage failure variant BLOCKED until a safe store-failure setup is supplied; no arbitrary production fault injection assumed.

### WID-010 — P0 · HARNESS · isolated widget command store · NOT RUN
Source: S-WID. Stale/missing commands and item/date binding.

1. Run `data_case 'com.luma.lifeos.data.WidgetStoreAcceptanceTest#collectionMutationTokenIsBoundToItsExactItemAndDate'`. Expected: altered item/date cannot redirect mutation.
2. Run `data_case 'com.luma.lifeos.data.WidgetStoreAcceptanceTest#collectionCommandsKeepConflictAndMissingResultsBoundToTheirRecords'`. Expected: conflicting/deleted record yields correct recovery result; unrelated row unchanged.
3. Run `app_case 'com.luma.lifeos.widget.WidgetIntentsTest#collectionFillInsKeepNavigationAndMutationBoundToRecordIdentity'`. Expected: malformed UUID rejected.
Oracle: selected tests only, exact identities unchanged on rejected command. Evidence: reports. Cleanup: isolated teardown. Do not run full WidgetStoreAcceptanceTest class.

### WID-011 — P1 · DEVICE · FW · NOT RUN
Source: S-WID. Reboot, date/zone/locale and deletion lifecycle.

1. Reboot device and unlock Android; view launcher. Expected: three widgets remain and refresh safely; no pending mutation replays.
2. Change date to D+1, return launcher; tap Refresh. Expected: dated habit/task scope updates for new local day, no D logs invented.
3. Change zone to UTC then back Asia/Tehran; change app language Persian then English. Expected: refresh/localized labels, no mixed stale identity or wrong-day log.
4. Remove T2 from launcher; Settings → Widgets after resume. Expected: only T1/H1 listed; remaining instance taps work.
Oracle: original 2 tasks/2 habits/0 logs; removed widget has no live configuration entry. Evidence: reboot/time/zone/instance inventory. Cleanup: baseline clock/locale, CW.

### WID-012 — P1 · DEVICE · FW + FL policy protecting Tasks/Habits · NOT RUN
Source: S-WID + S-LOCK. Launcher privacy and accessible presentation.

1. With Tasks/Habits protected, Home and refresh widgets. Expected: preview-free locked state; no SYS titles/counts leaked even after in-app unlock; tapping opens authorized app flow.
2. Disable lock via authenticated settings, then view widgets in Light/Dark at 100% and 200% font; repeat Persian RTL. Expected: full accessible header/action names, distinct ≥48dp targets, readable independent launcher palette, list reachable after resize.
3. Enable lock again while launcher shows previously populated rows. Expected after refresh: rows cleared/redacted; no background complete/log accepted.
Oracle: 2 tasks/2 habits/0 logs throughout; protect screenshots cannot sanitize launcher caches retroactively—verify current RemoteViews and retain cache limitation evidence. Cleanup CW/C0. Evidence: locked/unlocked states, bounds/locale/scale; no secret credential screenshots.

## App lock and restricted capture

### LOCK-001 — P0 · MANUAL · F1 · NOT RUN
Source: S-LOCK. Enrollment is atomic and recovery acknowledgment required.

1. Settings → App lock; choose whole app, Every time, screenshot protection On; method Password. Enter Luma-QA-2026! and mismatching confirmation. Expected: Continue disabled.
2. Match confirmation; Continue (`app_lock_setup_continue`). Expected: generated recovery code; final Continue disabled until saved-code acknowledgment.
3. Record code offline; acknowledge and Continue (`app_lock_recovery_confirm`). Expected: enabled lock, no secret visible afterward.
4. Home/reopen → Unlock with password. Expected: F1 intact.
Oracle: lock enabled only after complete enrollment; 2 tasks/2 habits/1 memory. Evidence: control enable states without exposing code. Cleanup C0 or keep FL snapshot.

### LOCK-002 — P1 · MANUAL · F1 · NOT RUN
Source: S-LOCK. Current credential entry boundaries and digit canonicalization.

1. In App lock setup choose Password; enter/confirm `abc`. Expected: Continue disabled (3 code points).
2. Enter/confirm `abcd`; then 32 ASCII `a` characters. Expected each: Continue enabled. Enter/confirm 33 ASCII `a` characters. Expected: Continue disabled. Do not complete these enrollments.
3. Choose PIN; enter/confirm Persian digits `۱۳۵۷`; complete recovery acknowledgment as LOCK-001. Expected: enrolled PIN.
4. Lock now; enter ASCII `1357`. Expected: unlock succeeds, representing the same canonical PIN; F1 records intact.
Oracle: current rules are 4–32 code points for password and 4–32 decimal digits for PIN; passwords preserve spaces/Unicode without normalization. Only one final enrollment; 2 tasks/2 habits/1 memory. Evidence: length/control states without visible real secrets, method and unlock result. Cleanup C0. The historical LockCredentialIntegrationTest length vectors use obsolete 8–128 assumptions and must not be used as current acceptance criteria.

### LOCK-003 — P0 · MANUAL · FL · NOT RUN
Source: S-LOCK. Wrong credential/cancel/background leave content private.

1. Cold launch; Unlock; enter Luma-QA-Wrong!, submit. Expected: incorrect feedback; no protected record titles/contents displayed.
2. Cancel prompt. Expected: preview-free gate persists; browse remains locked.
3. Unlock; enter correct password but press Home before submitting; return. Expected: prompt cancelled/secret cleared; no automatic unlock.
4. Reopen prompt and enter correct password. Expected: browse access granted once to intended destination.
Oracle: records unchanged; no unlock grant from cancellation. Evidence: gate/error/cleared field without secret. Cleanup C0.

### LOCK-004 — P0 · HARNESS · lock clock/store seam · NOT RUN
**BLOCKED prerequisite:** migrate LockCredentialIntegrationTest to current typed credential API; source inspection finds its calls do not match current source; build verification is NOT RUN. The exact entry below is a target after that repair, not a runnable claim today.
Source: S-LOCK. Shared cooldown survives recreation/reboot.

1. Run `app_case 'com.luma.lifeos.lock.LockCredentialIntegrationTest#sharedCooldownPersistsAcrossServiceRecreationAndBootChange'`. Expected: credential and recovery failures share counter; fifth failure starts 30-second cooldown, persistence/boot changes cannot reset protection.
2. Inspect report and source assertions. Expected: bounded schedule (doubling, max 900 seconds), correct remaining delay handling, no immediate bypass.
Oracle: exact fake-clock/store assertions; no production wall-clock guessing. Evidence: report. Cleanup: test store teardown. No hundreds of credential attempts.

### LOCK-005 — P0 · DEVICE · FL · NOT RUN
Source: S-LOCK. Relock and selected coverage.

1. Unlock → Settings → App lock; authenticate settings; select selected sections (`app_lock_coverage_selected`), only Memories; Save policy (`app_lock_save_policy`), authenticate confirmation. Expected: one selected protected section; cannot save zero selected sections.
2. Lock now (`app_lock_lock_now`); open More → Tasks and Places. Expected: unprotected content accessible; opening Memory requires unlock.
3. Authenticate settings; select After 1 minute; save/authenticate. Unlock Memory; Home 30 seconds then return. Expected: grant retained.
4. Home for 65 seconds then return. Expected: Memory gated; no private preview. Relaunch also gates.
Oracle: 2 tasks/1 memory unchanged; policy persists. Evidence: selected policy/elapsed timing/gate. Cleanup C0. Use a stopwatch, not date/time change.

### LOCK-006 — P0 · DEVICE · FL · NOT RUN
Source: S-LOCK. Screenshot and Recents privacy.

1. Unlock and open SYS Memory with screenshot protection On; attempt Android screenshot. Expected: blocked/redacted protected content.
2. Home → Recents. Expected: protected task preview absent/redacted on API33+; API28–32 pause uses secure task snapshot.
3. Authenticate App lock settings; turn screenshot protection Off, Save policy/authenticate; return to unlocked Memory and screenshot. Expected: foreground screenshot allowed; Recents still protected while lock enabled.
4. Open credential/recovery prompt; attempt screenshot. Expected: secret UI always secure regardless of foreground choice.
Oracle: record counts unchanged; security state reapplied after resume/rotation. Evidence: blocked screenshot messages/Recents without secrets; record API. Cleanup C0; delete allowed QA screenshot.

### LOCK-007 — P0 · MANUAL · FL · NOT RUN
Source: S-LOCK. Change method invalidates old credential; cancel is safe.

1. Authenticate App lock settings → Change method (`app_lock_change_password`); cancel action. Expected: original password still unlocks.
2. Reopen Change method; prove current password; choose PIN; enter/confirm 1357; confirm action (`app_lock_action_confirm`). Expected: current method PIN.
3. Lock now; try old password route. Expected: stored PIN method shown; old credential cannot unlock; PIN 1357 succeeds.
4. Authenticate settings → Disable (`app_lock_disable`), cancel; then repeat and confirm current PIN. Expected: cancelled disable retains lock; confirmed disable removes gate.
Oracle: F1 data unchanged; method survives relaunch; no stale authorization accepted. Evidence: method labels/status only. Cleanup C0.

### LOCK-008 — P0 · MANUAL · FL, offline recovery code retained · NOT RUN
Source: S-LOCK. Recovery replaces credential/code only after acknowledgment.

1. Locked gate → Unlock → Forgot credential (`app_lock_forgot_password`); enter wrong recovery code plus matching new Password Luma-QA-New2026!; Continue (`app_lock_recovery_continue`). Expected: prospective replacement recovery code appears; no replacement is committed yet. Acknowledge it and Finish (`app_lock_recovery_finish`). Expected: recovery error; original credential/code still valid. Discard this uncommitted displayed code.
2. Enter original recovery code and matching replacement credential again; Continue. Expected: a fresh prospective replacement recovery code, Finish disabled before acknowledgment.
3. Record the fresh code offline; acknowledge → Finish; Lock now. Expected: new password unlocks; original password is rejected. In a subsequent recovery attempt with the original code, complete its prospective-code acknowledgment/Finish to verify original code rejection, then discard that uncommitted prospective code.
Oracle: no records erased; exactly 2 tasks/2 habits/1 memory. Evidence: success/error states with codes redacted. Cleanup C0. Failure/interrupted-write harness: LockCredentialIntegrationTest.recoveryFailureInterruptedWriteAndSuccessPreserveOldThenReplaceCredentials.

### LOCK-009 — P0 · MANUAL · FL · NOT RUN
Source: S-LOCK + S-BAK. Forgotten-code erase has two confirmations.

1. Forgot credential → no recovery code (`app_lock_forgot_no_code`). Expected: erase warning, no immediate reset.
2. Continue first warning, cancel final warning. Expected: FL records and lock still present.
3. Repeat first Continue then final erase (`app_lock_erase_final_confirm`). Expected: OS clears disposable sandbox/stops app.
4. Relaunch. Expected: 0 tasks/habits/memories, lock absent; external backup unaffected.
Oracle: actual fresh launch confirms erasure; accepted callback alone insufficient. Privacy page's ordinary Delete button is disabled while lock enabled (`privacy_reset_locked`); do not assume ordinary delete bypasses lock. Evidence: two warning stages and clean launch. Cleanup C0.

### LOCK-010 — P0 · DEVICE · FL · NOT RUN
Source: S-LOCK. Restricted capture saves privately, cancellation and draft resume.

1. From locked gate tap Add new entry; choose Task (`restricted_capture_kind_task`); enter title SYS Private task. Expected: capture editor shows no existing private task/catalog previews.
2. Cancel (`restricted_capture_cancel`) → Keep editing. Expected: title retained; no committed task.
3. Save (`restricted_capture_save`) once. Expected: Saved acknowledgment then return; no browse unlock. Unlock normally → Tasks. Expected: exactly 3 tasks, one SYS Private task.
4. Lock again; Add new entry → Memory; title SYS Private draft, note Draft only; rotate/background/return. Expected: no browse leak; if session remains editable, cancel → confirm discard; if returned to private draft list, select resume (`restricted_capture_resume_0`) and satisfy unlock request before reading retained content, then discard.
Oracle: only 1 committed new task; no second memory from draft/discard; F1 memory remains 1. Evidence: acknowledgment, gated return, resumed/discarded draft IDs without secret text. Cleanup C0. Journal is intentionally omitted from restricted capture kind chooser; legacy journal entry is routed to Memory.

## Execution gaps and limits to resolve

- App instrumentation harness is currently a **source-level compatibility gap**: LockCredentialIntegrationTest calls obsolete untyped `enable`/`recover` signatures and asserts 8–128 password lengths, while current service requires credential type and current input policy uses 4–32. No compatibility overload was found. QA-ENV-LOCK-HARNESS: all `app_case` commands are BLOCKED pending a test-host compatibility check and migration if required; method filtering alone cannot avoid compilation. LOCK-004 is explicitly blocked; its cooldown source contract remains specified. This plan did not execute a build to confirm compiler output. LOCK-002 supplies current manual boundary coverage. The historical interrupted-write/recovery test reference in LOCK-008 is likewise blocked until migrated.
- No first-run onboarding screen or general web deep-link filter exists in inspected current sources. SYS-008 tests observed startup/manifest contract; widget routes are exercised by real taps and WidgetIntentsTest. Reminder/focus notification entry coverage belongs to the feature scenario file.
- Current backup UI/archive is **unencrypted** schema 14 ZIP, full replacement. Do not execute roadmap encryption/passphrase/freshness UX as if implemented. Invalid schema, duplicate archive entry, traversal and truncated/hash failure contracts are additionally covered by BackupCodecTest; run selected methods after reviewing fixture sizes if expanding this plan.
- There is no production fault-injection menu. Deterministic failure/cancellation/collision cases above use existing fake service, staged DB trigger, controlled provider/clock or RemoteViews harness. Native picker unavailable, physical low disk, configuration load/save I/O failure and manufacturer launcher crash variants are **BLOCKED prerequisites** until a disposable safe environment/repro is supplied; do not claim they were tested via UI.
- During tiny manual backups, an operation may finish before a Back/cancel-and-leave interaction. For an observable busy-screen departure case a controllably slow SAF provider is required (**BLOCKED**, no supplied provider fixture). Current UI has Stay/Cancel and leave during export/prepare, may warn that output remains if provider cannot delete it, and restore confirmation starts a noncancellable replacement. Do not force-kill live production restore to manufacture a failure. BAK-007/008 cover atomicity/resources through executable seams; crash-during-commit end-to-end needs a dedicated crash harness.
- FH requires trustworthy aggregate Steps seeding/deletion. OEM/provider deduplication can change raw-to-aggregate values; if unable to isolate 100/200→80 exactly, mark INT-008/009 BLOCKED and use the existing harness for exact numbers. No Health periodic background worker is present; usage schedules six-hour work. Execution deadlines depend on Android WorkManager and are not wall-clock guarantees.
- Widget instances are counted from live launcher IDs; cancellation/placeholder/pinning support differs by launcher. Current configuration exposes no user mode/privacy/theme toggles. Widget preview redaction is policy-based even while an in-app grant is active. Launcher caching is outside retroactive erasure guarantees; do not attach cached sensitive content.
- LOCK-010 covers Task/Memory capture, draft cancel/resume, and one private save. Restricted Mood/Habit field validation, photo picker/camera cancellation and the 20-photo limit require additional scoped cases; do not claim full restricted-capture type coverage. PIN/pattern visual enrollment and accessibility traces are not fully covered by password-oriented manual cases; service/input contract coverage is explicit in LOCK-002.
- Settings calendar/week-start save failures have retry UI, language save failure has error UI. No production failure switch was found; dedicated storage-write failure/retry UI cases remain BLOCKED until an injectable settings-host harness is supplied. Do not mutate preferences files behind the running app.
