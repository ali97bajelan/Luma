# Tasks acceptance record — 19 October 2026

TD07 is **In progress**. The focused task UI, localization, Room CRUD/migration,
task backup and app build checks below pass. Product/UX criteria are aggregate results: each is PASS only when every
specified condition has evidence. A passing sub-check does not promote an
otherwise incomplete criterion to PASS.

## Test environment and commands

- Disposable Android emulator `emulator-5562`; Android API 36; package
  `com.luma.lifeos`, instrumentation package `com.luma.lifeos.test`.
- Emulator display 1080×2400 at 420 dpi (about 411 dp wide), OS font scale 1.0.
  The Compose semantics test overrides its local configuration to 320 dp and
  font scale 2.0; it does not change emulator display settings.
- The language flow changes Luma's in-app language English → Persian → French →
  English and recreates the Activity after each selection. It does not change the
  Android system locale.
- App data was cleared only on the disposable emulator before the final run. The
  existing user emulator and its Luma data were not used.

**PASS — full domain/app/data JVM tests, app/data lint and app packaging:**

```sh
/home/bajelan/.gradle/wrapper/dists/gradle-9.1.0-bin/9agqghryom9wkf8r80qlhnts3/gradle-9.1.0/bin/gradle --offline --continue \
  :core:domain:test \
  :app:testDebugUnitTest \
  :core:data:testDebugUnitTest \
  :app:lintDebug \
  :core:data:lintDebug \
  :app:assembleDebug \
  :app:assembleDebugAndroidTest \
  --no-daemon --console=plain
```

The result was BUILD SUCCESSFUL. Results: domain 126/126 tests, app 223/223 and
data 24/24; no failures, errors or skipped tests. The app unit suite was then
forced to re-run with the same Gradle 9.1.0 binary using
`:app:testDebugUnitTest --rerun-tasks --no-daemon --console=plain` after the
translation resource change; all 223 passed.
App and data lint both pass. App debug and app instrumentation APKs assemble.

The French `habit_other_count` plural now includes the required `many` variant,
which the translation-catalog test checks. Persian and French `habit_unit_default`
resources suppress `ImpliedQuantity` with an XML comment because the app displays
the count separately from that unit-only label.

**PASS — focused emulator instrumentation, 4/4 tests:**

```sh
/home/bajelan/Android/Sdk/platform-tools/adb -s emulator-5562 shell am instrument -w \
  -e class 'com.luma.lifeos.todo.TodoAccessibilityTest,com.luma.lifeos.todo.TodoAccessibilitySemanticsTest,com.luma.lifeos.todo.TodoLanguageFlowTest' \
  com.luma.lifeos.test/androidx.test.runner.AndroidJUnitRunner
```

The MainActivity flow reaches More → Tasks, creates a task, completes and reopens
it, then deletes it after confirmation. It verifies that the next list control
remains actionable after row removal. Compose semantics cases check one checkbox
node, distinct body and status actions, a discoverable group heading, the 51-row
count and reachable Load More at simulated 320 dp/200% text, and next-row
actionability after completion. The language flow retains a dirty title, note and
civil due date through English/Persian/French Activity recreation and checks that
an empty-title validation message is exposed as a polite live region.

The language test waits for the recreated Activity's actual resource locale. It
does not force a second Activity recreation after Android changes the app locale;
the prior test version had an intermittent race because the persisted locale
could update before the Activity resources did. After this wait was corrected,
the full focused suite passed and the language case passed on two additional
repeats.

**PASS — data-module instrumentation compile and task persistence/migration:**

`gradle --offline :core:data:assembleDebugAndroidTest --no-daemon --console=plain`
passes after correcting `TodoPersistenceTest` to import
`kotlinx.coroutines.channels.Channel`. Direct execution on `emulator-5562` passed
`TodoPersistenceTest` and `TodoMigrationTest`, 7/7 tests total. The migration case
upgrades populated schema versions 1, 2 and 3 to 4 while retaining the event,
journal, photo reference, usage session and habit data.

**PASS — task backup and legacy replacement:**

`BackupPersistenceTest` passes 14/14 tests. `TodoBackupTest` passes 3/3 tests for
schema-4 open/completed/undated task round-trip, preview counts, revision-token
rotation, schema-1/2/3 replacement clearing current tasks, and cancelled or
malformed task restore retaining live data. The schema-1 fixture contains only
the schema-1 table inventory plus photos; earlier it retained schema-3/4 tables
inside an archive labeled schema 1.

The TD02/TD03 follow-up reran `:core:data:testDebugUnitTest` (24/24 pass), built
the data/app Android test APKs, and ran focused checks on `emulator-5562` (API 36):
`TodoPersistenceTest`, `TodoMigrationTest`, `TodoBackupTest`,
`BackupPersistenceTest`, and `HabitBackupTest` passed 29/29 total. App unit tests
for `BackupViewModelTest` and `TodoRestoreInvalidationTest` passed 7/7; the
`BackupSettingsFlowTest` preview/count/warning flow passed 3/3. Room schema 4 was
exported and checked against the entity and indexes.

The follow-up instrumentation commands used only the disposable API 36 device:

```sh
/home/bajelan/Android/Sdk/platform-tools/adb -s emulator-5562 shell am instrument -w \
  -e class 'com.luma.lifeos.data.TodoPersistenceTest,com.luma.lifeos.data.TodoMigrationTest,com.luma.lifeos.data.backup.TodoBackupTest,com.luma.lifeos.data.backup.BackupPersistenceTest,com.luma.lifeos.data.HabitBackupTest' \
  com.luma.lifeos.data.test/androidx.test.runner.AndroidJUnitRunner

/home/bajelan/Android/Sdk/platform-tools/adb -s emulator-5562 shell am instrument -w \
  -e class 'com.luma.lifeos.backup.BackupSettingsFlowTest' \
  com.luma.lifeos.test/androidx.test.runner.AndroidJUnitRunner
```

The callbacks/draft JVM check used `:app:testDebugUnitTest --tests
com.luma.lifeos.backup.BackupViewModelTest --tests
com.luma.lifeos.ui.todo.TodoRestoreInvalidationTest`; all 7 tests passed.

**FAIL — full core:data instrumentation suite:**

The complete data test APK was run directly with ADB on `emulator-5562`:

```sh
/home/bajelan/Android/Sdk/platform-tools/adb -s emulator-5562 shell am instrument -w \
  com.luma.lifeos.data.test/androidx.test.runner.AndroidJUnitRunner
```

It ran 139 tests; 137 passed and two `TimelinePersistenceTest` cases failed on
expected ordering/page IDs (`shortAndMissingUsageAreExcludedBeforePagingWhileLongUsageRemains`
and `indexedMixedPagesAreBoundedDeterministicAndExcludeOtherDays`). Neither test
touches the Tasks repository, but the full data suite is not green. The focused
task persistence, migration and backup suites all pass within that run.

**FAIL — full app instrumentation suite:**

After clearing only `com.luma.lifeos` on the disposable emulator, the complete
app instrumentation APK was run directly:

```sh
/home/bajelan/Android/Sdk/platform-tools/adb -s emulator-5562 shell am instrument -w \
  com.luma.lifeos.test/androidx.test.runner.AndroidJUnitRunner
```

It ran 154 tests and reported 12 failures: one Habit capture case, three Health
Connect settings cases, three Weekly Review cases, one Daily Summary case, and
four Usage settings cases. No `TodoAccessibilityTest`,
`TodoAccessibilitySemanticsTest` or `TodoLanguageFlowTest` case failed in this
full run. The failures are recorded separately from the Tasks-specific results;
their causes were not investigated as part of TD07.

**BLOCKED — Gradle-managed connected app test task:**

`:app:connectedDebugAndroidTest` could not resolve
`com.android.tools.utp:android-test-plugin-host-additional-test-output:31.7.3`
in the available repository/cache. The focused app tests and complete app/data
test APKs were therefore run directly using `adb` on the disposable emulator.
The full app and data suites have the failures listed above.

## Product acceptance criteria

| Criterion | Status | Evidence and remaining work |
| --- | --- | --- |
| TODO-AC-01 | NOT RUN | Empty-title validation retains an editable draft. Exact title/note boundaries, Unicode limits, duplicate titles, edit identity/timestamps and complete create/edit persistence acceptance remain unverified. |
| TODO-AC-02 | NOT RUN | Due date survives an in-app locale change and Activity recreation. No-date default, Today prefill, clear/past/future dates, travel and midnight behavior remain unverified. |
| TODO-AC-03 | NOT RUN | Complete/reopen actions are exercised. Timestamp idempotence, completed edits and blocking status changes while dirty remain unverified. |
| TODO-AC-04 | NOT RUN | A mocked 50-row list exposes total 51 and Load More. Room-backed paging, deterministic ordering and mutation-without-duplicate coverage remain unverified. |
| TODO-AC-05 | NOT RUN | More → Tasks is exercised. Today, Quick Capture, five-tab/back-stack and capture-origin regressions remain unverified. |
| TODO-AC-06 | NOT RUN | Today eligibility, three-row cap, full count and historical-date hiding were not tested. |
| TODO-AC-07 | NOT RUN | Distinct checkbox/body actions and successful completion/reopen are exercised. Failed writes, retry/Undo revisions, commit announcements and create retry behavior remain unverified. |
| TODO-AC-08 | NOT RUN | Confirmed deletion succeeds. Cancel, dirty Back, missing targets and delete retry behavior remain unverified. |
| TODO-AC-09 | PASS | `TodoMigrationTest` upgrades populated schema 1–3 databases to 4; task backup tests round-trip schema-4 rows, verify preview count/revision rotation, clear tasks on schema-1/2/3 replacement, and retain live data after cancelled or malformed restore. |
| TODO-AC-10 | NOT RUN | Focused JVM checks cover success-only restore callback delivery and ensure a retained editor draft cannot write to a same-UUID restored task. End-to-end shell-to-route invalidation and isolated app-data reset remain unverified; no destructive reset was performed. |
| TODO-AC-11 | NOT RUN | Simulated 320 dp/200% text and localized form behavior pass their focused checks. Full layout matrix, ≥48 dp target measurement, status/overdue color independence and native screen-reader labels/traversal remain unverified. |
| TODO-AC-12 | NOT RUN | Dirty-draft Activity recreation and validation retention are exercised. Retry states, empty Open/Completed actions and full view restoration remain unverified. |
| TODO-AC-13 | NOT RUN | No task CRUD isolation checks against events, habits, Timeline, summaries, search, Review, network or permissions were run. |

## UX acceptance criteria

| Criterion | Status | Evidence and remaining work |
| --- | --- | --- |
| UX-TODO-01 | NOT RUN | More → Tasks works. Today origin, Quick Capture restoration and navigation/back-stack state remain unverified. |
| UX-TODO-02 | NOT RUN | Group heading, mocked 50-row list and Load More reachability pass. Repository ordering and growing observed prefixes remain unverified. |
| UX-TODO-03 | NOT RUN | One checkbox semantics node, separate body/status actions, and complete/reopen flows pass. Physical target dimensions and all row-label combinations remain unverified. |
| UX-TODO-04 | NOT RUN | Today task preview behavior was not exercised. |
| UX-TODO-05 | NOT RUN | Quick Capture date defaults and large-text record choices were not exercised. |
| UX-TODO-06 | NOT RUN | Required-title validation and retained editable text pass. Full title/note boundaries, dirty dialogs, save and delete error behavior remain unverified. |
| UX-TODO-07 | NOT RUN | Localized due-date display survives Activity recreation. Picker confirm/cancel/clear, timezone travel and dirty-state combinations remain unverified. |
| UX-TODO-08 | NOT RUN | Completion/reopen are exercised. Completed-edit preservation, dirty status guard and duplicate-tap handling remain unverified. |
| UX-TODO-09 | NOT RUN | Activity recreation retains the tested draft. Back/IME/picker cancellation, process death and full draft decision paths remain unverified. |
| UX-TODO-10 | NOT RUN | Confirmed deletion succeeds. Cancel/failure retention and backup wording remain unverified. |
| UX-TODO-11 | NOT RUN | Concurrent conflict, reload/missing states and stale-write rejection were not exercised. |
| UX-TODO-12 | NOT RUN | Successful row actions and validation feedback are exercised. Scoped failures, retries, Undo and native focus behavior remain unverified. |
| UX-TODO-13 | NOT RUN | Automated semantics checks cover a simulated narrow/large-text layout. Native TalkBack, keyboard/IME, themes and the full locale/width/font matrix were not tested. |
| UX-TODO-14 | NOT RUN | Translation catalog tests and a dirty-draft flow across EN/FA/FR pass. Persian numerals, Gregorian date rules, RTL mixed-direction rendering and all catalog-format cases are not fully accepted here. |
| UX-TODO-15 | NOT RUN | Backup settings Compose checks cover task counts and the legacy zero-task replacement warning; data tests cover schema-4 restore and cancelled/malformed restore. Native document-picker flow, integrated shell invalidation and isolated reset remain unverified. |

## Manual/native and performance checks

These checks remain **NOT RUN**: native TalkBack speech and focus traversal;
keyboard/IME and picker behavior; mixed-direction Persian content; dark/light
themes; date clearing; color-independent overdue state; export/restore; full
app-data reset; task CRUD isolation from existing data; and a 10,000-task fixture
with repository p50/p95 timing against the ≤300 ms initial-prefix target. No
native focus result is inferred from Compose semantics. The test host reported
`Focused=false` even for a minimal Material button after `RequestFocus`, so the
focused suite checks that the next control remains actionable and leaves native
TalkBack as a separate manual acceptance item.

TD07 remains **In progress** until the full app/data suites are green, the
remaining criteria are run, and any
unresolved exception is explicitly accepted. Do not mark the Tasks feature
release-ready from the focused results above.
