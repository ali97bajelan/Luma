# Tasks acceptance record — 19 October 2026

TD07 is **In progress**. Focused task UI, localization, Room persistence/migration,
backup, isolation, scale, app builds, and most native UI checks pass. The full app
and data instrumentation suites still have failures outside the Tasks tests, and
native TalkBack speech/focus traversal could not be verified. Product/UX criteria are
aggregate results: each is PASS only when every specified condition has evidence.
A passing sub-check does not promote an otherwise incomplete criterion to PASS.

## Test environment and commands

- Disposable Android emulator `emulator-5562`; Android API 36; package
  `com.luma.lifeos`, instrumentation package `com.luma.lifeos.test`.
- Emulator display 1080×2400 at 420 dpi (about 411 dp wide), OS font scale 1.0.
  The Compose semantics test overrides its local configuration to 320 dp and
  font scale 2.0; it does not change emulator display settings.
- The language flow changes Luma's in-app language English → Persian → French →
  English and recreates the Activity after each selection. It does not change the
  Android system locale.
- Android app data was cleared only on the disposable emulator before test runs
  and again for the final reset check. The existing user emulator and its Luma
  data were not used.

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

The recorded full-module build was successful. Results: domain 126/126 tests and
data 24/24; no failures, errors or skipped tests. The app suite has since gained
the TD04–TD06 ViewModel/state tests and passes 244/244 on a full
`:app:testDebugUnitTest` run. An earlier forced app rerun, before those additions,
passed 223/223. App and data lint both pass; app debug and instrumentation APKs
assemble. The final aggregate Gradle rerun after adding the TD07 data acceptance
tests also passed (232 tasks; nine executed, 223 up-to-date): [final build output](../.superpowers/sdd/2026-10-19-todo-list/td07-build-final.log).

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

**PASS — TD04–TD06 follow-up JVM and UI flows:**

The new app tests cover stable create operation IDs/revisions and explicit
retry after ambiguous results; conflict/missing-task draft retention; live
50→100 list prefixes, retry and revision-safe completion Undo; Today eligibility,
full counts, completion backfill, date regrouping and scoped errors. Six editor
Compose flows, one navigation flow, two Today flows and two Today semantics
flows passed 11/11 together on disposable `emulator-5562` (API 36):

```sh
/home/bajelan/Android/Sdk/platform-tools/adb -s emulator-5562 shell am instrument -w \
  -e class 'com.luma.lifeos.todo.TodoEditorFlowTest,com.luma.lifeos.todo.TodoNavigationTest,com.luma.lifeos.todo.TodayTasksFlowTest,com.luma.lifeos.todo.TodayTasksSemanticsTest' \
  com.luma.lifeos.test/androidx.test.runner.AndroidJUnitRunner
```

The editor flows exercise title-only save, picker cancel/same-date confirmation,
date clearing, dirty status guarding, delete cancel/confirm and dirty close. The
navigation flow verifies an undated Task from historical Quick Capture returns
to its Timeline origin and that editor completion Undo is consumed once. Today
flows verify contextual Add defaults to today, empty-state Add/View all actions,
completion Undo, and hiding/restoring the card on both future and historical
dates. Today semantics tests verify three visible rows/full count and that a task
load error does not hide events, habits or summary. Adjacent `AppNavigationTest`,
`HabitsNavigationTest`, `TodayScreenTest`, `DayNavigationTest` and
`EntryHabitCountsFlowTest` regressions passed 22/22; task accessibility and
language regressions passed 4/4.

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

The final complete APK run included the TD07 isolation, duplicate-title and
scale acceptance cases and ran 145 tests; 143 passed and two
`TimelinePersistenceTest` cases failed on
expected ordering/page IDs (`shortAndMissingUsageAreExcludedBeforePagingWhileLongUsageRemains`
and `indexedMixedPagesAreBoundedDeterministicAndExcludeOtherDays`). Neither test
touches the Tasks repository, but the full data suite is not green. The focused
task persistence, migration, backup, isolation and scale cases all pass within
that run. [Full data output](../.superpowers/sdd/2026-10-19-todo-list/td07-data-full-device-final.log).

**FAIL — full app instrumentation suite:**

After clearing only `com.luma.lifeos` on the disposable emulator, the complete
app instrumentation APK was run directly:

```sh
/home/bajelan/Android/Sdk/platform-tools/adb -s emulator-5562 shell am instrument -w \
  com.luma.lifeos.test/androidx.test.runner.AndroidJUnitRunner
```

It ran 166 tests and reported 14 failures: two capture cases
(`PeopleCaptureTest.journalKeepsDraftAcrossNewPeopleAndHistoryReflectsExplicitRemoval`
and `PlaceCaptureTest.journalDraftSurvivesPlaceCreationAndSelectionCanBeRenamedAndCleared`),
one Habit capture case, three Health Connect settings cases, three Weekly
Review cases, one Daily Summary case, and four Usage settings cases. No `TodoAccessibilityTest`,
`TodoAccessibilitySemanticsTest` or `TodoLanguageFlowTest` case failed in this
full run. The failures are recorded separately from the Tasks-specific results;
their causes were not investigated as part of TD07. [Full app output](../.superpowers/sdd/2026-10-19-todo-list/td07-app-full-device-current.log).

**BLOCKED — Gradle-managed connected app test task:**

`:app:connectedDebugAndroidTest` could not resolve
`com.android.tools.utp:android-test-plugin-host-additional-test-output:31.7.3`
in the available repository/cache. The focused app tests and complete app/data
test APKs were therefore run directly using `adb` on the disposable emulator.
The full app and data suites have the failures listed above.

## Failure register and checks still needed

This register records the observed failures from the final full APK runs. It is
not a diagnosis: none of these failures has yet been reproduced individually or
traced to product code, test assumptions, fixture state or environment. They are
**unresolved, not accepted exceptions**. The full logs linked above contain the
complete stack traces.

### Full data instrumentation: 2 failures

| Test | Observed failure | Follow-up |
| --- | --- | --- |
| `TimelinePersistenceTest.shortAndMissingUsageAreExcludedBeforePagingWhileLongUsageRemains` (`TimelinePersistenceTest.kt:139`) | Expected IDs `[201, 300]`; received `[300, 201]`. | Check the test's ordering contract against the Room query and fixture timestamps; run the case alone and the class to determine whether ordering is deterministic. |
| `TimelinePersistenceTest.indexedMixedPagesAreBoundedDeterministicAndExcludeOtherDays` (`TimelinePersistenceTest.kt:45`) | Expected first-page IDs `[0..49]`; received `[2000, 1999, …, 1951]`. | Check fixture timestamps/IDs against the intended sort direction and page contract; run the case alone and the class, then rerun the full data APK. |

These are Timeline persistence checks and do not exercise Tasks. Do not change
the expected order until the Timeline contract and fixture setup have been
compared with the actual query.

### Full app instrumentation: 14 failures

| Test | Observed failure | Follow-up |
| --- | --- | --- |
| `PeopleCaptureTest.journalKeepsDraftAcrossNewPeopleAndHistoryReflectsExplicitRemoval` (`PeopleCaptureTest.kt:21`, helper line 103) | `performScrollTo()` could not find test tag `people_picker_create`. | Capture the active screen's semantics tree; check route/state and whether the picker action's tag or test setup changed. |
| `PlaceCaptureTest.journalDraftSurvivesPlaceCreationAndSelectionCanBeRenamedAndCleared` (`PlaceCaptureTest.kt:20`, helper line 103) | `performScrollTo()` could not find test tag `place_picker_create`. | Capture the active screen's semantics tree; check route/state and whether the picker action's tag or test setup changed. |
| `HabitCaptureFlowTest.noHabitAndNoHabitScheduledAreDifferentStatesAndFutureLogIsDisabled` (`HabitCaptureFlowTest.kt:164,179`) | The assertion for a disabled future log could not find tag `habit_log_64abb4d9-b7ce-4fa5-9bfe-641aee7e64c0`. | Inspect the rendered semantics and scheduled-habit state for this fixture; verify whether the row is absent or its test identifier changed. |
| `HealthSettingsFlowTest.revokedAccessKeepsSavedStepsAndManagementUsable` (`HealthSettingsFlowTest.kt:84`) | `awaitText` timed out after 5 seconds. | Record the awaited text, screen semantics and Health Connect test state at timeout; rerun the case in isolation. |
| `HealthSettingsFlowTest.missingUnsupportedAndRevokedDataLeaveCoreSettingsUsable` (`HealthSettingsFlowTest.kt:65`) | `awaitText` timed out after 5 seconds. | Record the awaited text, screen semantics and Health Connect test state at timeout; rerun the case in isolation. |
| `HealthSettingsFlowTest.permissionStatusImportsCorrectionsAndOptOutPersistAcrossRecreation` (`HealthSettingsFlowTest.kt:45`) | `awaitText` timed out after 5 seconds. | Record the awaited text, screen semantics and Health Connect test state at timeout; rerun the case in isolation. |
| `WeeklyReviewFlowTest.overviewKeepsMissingAndRecordedZeroDistinct` (`WeeklyReviewFlowTest.kt:161`) | Expected text containing `Unavailable`; the `review_overview_mood` node contained `Mood, No check-ins saved for this week`. | Compare the expected empty-state copy with the current Review contract and fixture; confirm which state the test is meant to assert. |
| `WeeklyReviewFlowTest.sectionSelectionRestoresWithoutChangingTheSelectedWeek` (`WeeklyReviewFlowTest.kt:260`) | Click failed because test tag `review_sources_entries` was absent. | Capture the rendered semantics and selected Review section/week before the click; check fixture state and tag contract. |
| `WeeklyReviewFlowTest.overviewShowsSavedSamplesActivitySourceAndLinkedCatalogCounts` (`WeeklyReviewFlowTest.kt:196`) | Expected text containing `Unavailable`; the `review_overview_usage` node contained `Saved app usage, Loading saved data`. | Inspect the usage source state and loading completion in the fixture; determine whether this is a product state issue, async wait issue or stale assertion. |
| `DailySummaryFlowTest.realMetricsRefreshAndOmitUnavailableMoodMeans` (`DailySummaryFlowTest.kt:60`) | `awaitText` timed out after 5 seconds. | Record the awaited text, screen semantics and metric source state at timeout; rerun the case in isolation. |
| `UsageSettingsFlowTest.accessManagementIsAvailableWhileImportsAreOffAndExplanationCanBeCancelled` (`UsageSettingsFlowTest.kt:48`) | `awaitText` timed out after 5 seconds. | Record the awaited text, screen semantics and Usage Access state at timeout; rerun the case in isolation. |
| `UsageSettingsFlowTest.importFailuresKeepAccessRefreshAndRetentionControlsUsable` (`UsageSettingsFlowTest.kt:105`) | `awaitText` timed out after 5 seconds. | Record the awaited text, screen semantics and Usage Access state at timeout; rerun the case in isolation. |
| `UsageSettingsFlowTest.emptyAndRevokedAccessDoNotProduceZeroUsageOrDiscardSavedData` (`UsageSettingsFlowTest.kt:86`) | `awaitText` timed out after 5 seconds. | Record the awaited text, screen semantics and Usage Access state at timeout; rerun the case in isolation. |
| `UsageSettingsFlowTest.optionalAccessImportAndOptOutKeepDurableDailyValues` (`UsageSettingsFlowTest.kt:57`) | `awaitText` timed out after 5 seconds. | Record the awaited text, screen semantics and Usage Access state at timeout; rerun the case in isolation. |

### TD07 follow-up checklist

- [ ] Rerun each failing app test by class on a freshly cleared disposable API 36
  emulator. Save the awaited text or semantics tree and relevant logcat at the
  point of failure; compare isolated runs with the full-suite run to check for
  order dependence or state leakage.
- [ ] Rerun both Timeline failures individually and as a class. Trace expected
  ordering and page membership through fixture timestamps, SQL ordering and the
  documented Timeline contract before deciding whether code or assertions need
  a change.
- [ ] For each result, classify the evidence as a product regression, a stale
  test/fixture contract, an asynchronous wait/setup issue or an environment
  issue. Fix product defects; change tests only after verifying the intended
  behavior. Then rerun the affected class and the complete app/data APK suites.
- [ ] Resolve whether the Gradle-managed connected-test task is required in this
  environment: make its uncached UTP host-output dependency available, or record
  the approved direct-ADB APK workflow as the supported check. Rerun the chosen
  workflow and preserve its output.
- [ ] With native TalkBack active, verify spoken labels/actions and traversal
  across Tasks list rows, completion/reopen, editor, validation, dialogs and
  after row removal. The current evidence only confirms that the initial focus
  ring appeared and that three automated cases passed while TalkBack was active.
- [ ] Finish the remaining partial/not-run acceptance checks: native layout and
  target-size combinations; due-date behavior through device-zone travel and
  dirty-state combinations; failed-write/retry states; retained-route
  invalidation after restore; remaining editor/list restoration states; and
  OS permission/network isolation checks. Update the criteria matrix from the
  resulting evidence.
- [ ] Keep TD07 **In progress** until the suite failures are fixed or explicitly
  accepted, all required acceptance gaps have evidence or explicit acceptance,
  and the chosen integrated test workflow is recorded. No exception is accepted
  by this register.

**PASS — task CRUD isolation and 10,000-task scale:**

`TodoIsolationAcceptanceTest` seeds an event, mood entry, habit and habit log,
then creates, updates, completes, reopens and deletes a task. After each write it
compares unrelated table counts, Timeline rows/counts, daily summary values,
weekly Review values and sources, Search results and the habit day. All remained
unchanged. Network traffic and Android permission state are outside this Room
fixture and were not exercised.

`TodoScaleAcceptanceTest` inserts 10,000 Room tasks in a file-backed database,
then confirms the repository returns 50 Open rows and 3 Today rows while
reporting full counts (9,600 Open; 7,800 eligible Today). On
`sdk_gphone64_x86_64`, API 36, the final full data instrumentation run recorded:
Open p50 8.10 ms / p95 10.89 ms; Today p50 1.66 ms / p95 2.76 ms. Open p95 is
below the proposed 300 ms target. The test logs these measurements and asserts
the bounded row counts and Open p95 target; it does not instrument UI first-frame
rendering.

Source: [isolation test](../core/data/src/androidTest/kotlin/com/luma/lifeos/data/TodoIsolationAcceptanceTest.kt),
[scale test](../core/data/src/androidTest/kotlin/com/luma/lifeos/data/TodoScaleAcceptanceTest.kt).

## Product acceptance criteria

| Criterion | Status | Evidence and remaining work |
| --- | --- | --- |
| TODO-AC-01 | PASS | Domain tests cover whitespace/multiline rejection, 200/201-code-point titles and 4,000/4,001-code-point notes; language flow retains the invalid draft; persistence covers identity/creation time across reopen. `duplicateTitlesRemainIndependentTasksWhenEitherOneIsEdited` verifies duplicate titles remain separate after an edit. |
| TODO-AC-02 | PARTIAL | Due date survives EN/FA/FR Activity recreation; native English and Persian pickers selected civil dates; date clearing and Today prefill are exercised. State tests cover midnight/time-zone regrouping; native future-date selection, real zone travel and combined dirty-state acceptance remain unverified. |
| TODO-AC-03 | PASS | Room tests verify completion/reopen, repeated-completion timestamp/revision idempotence and actual completion time; editor tests verify completed edits preserve status and dirty fields block status changes. |
| TODO-AC-04 | PASS | Domain/data tests verify deterministic open/completed order and exact 50/100 prefixes; list-state tests grow live 50→100 results through insertion, deletion and reordering without stale duplicates or omissions. |
| TODO-AC-05 | PASS | More → Tasks, Today contextual Add and historical Timeline Quick Capture Task reach the shared Tasks/editor flow. Tests verify origin return, selected event date retention, shared editor completion Undo, and the existing primary-navigation regressions. |
| TODO-AC-06 | PASS | State tests verify the three-row cap, full eligible count and exclusion of future/undated/completed rows; device flows hide the card on future and historical browsed dates and restore it on Today. Midnight/zone date rules are covered by the state test. |
| TODO-AC-07 | PARTIAL | Distinct checkbox/body actions and successful completion/reopen are exercised; task repository retry/revision behavior has JVM/device coverage. Native TalkBack announcements and the full failed-write/retry matrix remain unverified. |
| TODO-AC-08 | PASS | Compose flows verify cancel/confirmed deletion and dirty Back; editor tests cover a missing target; persistence tests verify stale writes fail and a repeated delete after success is harmless. Native Persian confirmation Cancel also retained the task. |
| TODO-AC-09 | PASS | `TodoMigrationTest` upgrades populated schema 1–3 databases to 4; task backup tests round-trip schema-4 rows, verify preview count/revision rotation, clear tasks on schema-1/2/3 replacement, and retain live data after cancelled or malformed restore. |
| TODO-AC-10 | PARTIAL | Restore preview and native SAF export/restore succeeded; restored task survived process death. `pm clear com.luma.lifeos` on the disposable emulator returned Tasks to zero and app settings to defaults. Success-only callback/draft JVM checks pass; retained-route invalidation is not fully verified end-to-end. |
| TODO-AC-11 | PARTIAL | Simulated 320 dp/200% text and localized checks pass. Native English/Persian layouts expose status and overdue as text beside a separate checkbox. Full layout/target-size matrix and screen-reader labels/traversal remain unverified. |
| TODO-AC-12 | PARTIAL | Dirty-draft Activity recreation, validation retention and process-death task persistence are exercised. Retry states, empty Completed behavior and full route/view restoration remain unverified. |
| TODO-AC-13 | PARTIAL | CRUD isolation test confirms event/habit table counts, Timeline, summaries, weekly Review and Search stay unchanged through every task mutation. Network traffic and OS permission state were not part of the fixture. |

## UX acceptance criteria

| Criterion | Status | Evidence and remaining work |
| --- | --- | --- |
| UX-TODO-01 | PASS | More, Today and Quick Capture reach the shared flow; navigation regressions preserve the origin/selected date, return path and five primary destinations. |
| UX-TODO-02 | PASS | Domain/data ordering checks cover task groups and Completed order; list state grows from 50 to 100 through live insertion, deletion and reordering, and the Room fixture returns exact bounded pages. |
| UX-TODO-03 | PARTIAL | One checkbox semantics node, separate body/status actions and complete/reopen flows pass. Physical target dimensions and every row-label combination remain unverified. |
| UX-TODO-04 | PASS | State tests verify eligibility, cap/count, backfill, midnight and zone regrouping while preserving the civil due date; device flows verify future/historical hiding and Today restoration. |
| UX-TODO-05 | PARTIAL | Today contextual Add and historical Quick Capture Task flows pass; native keyboard/IME kept Save reachable while typing. Full large-text capture choices remain incomplete. |
| UX-TODO-06 | PARTIAL | Required-title validation, editable draft retention, dirty Back continue/discard and delete-confirmation cancel are exercised. Exact limits and save/delete failure behavior remain unverified. |
| UX-TODO-07 | PARTIAL | Localized due-date display survives EN/FA/FR Activity recreation; native English/Persian picker selection and clearing are exercised. Travel and all dirty-state combinations remain incomplete. |
| UX-TODO-08 | PARTIAL | Completion/reopen and dirty status guard are exercised. Completed-edit preservation and duplicate-tap handling remain unverified. |
| UX-TODO-09 | PARTIAL | Activity recreation retains the tested draft; process death preserves saved task data; native Back offers continue/discard and the IME is visible. Full process-death draft and picker-cancel flows remain unverified. |
| UX-TODO-10 | PARTIAL | Confirmed deletion succeeds; native confirmation wording explains permanence and Cancel preserves the task. Backup wording and failed-delete retention remain incomplete. |
| UX-TODO-11 | PASS | Editor tests retain drafts through update conflict, explicit reload and missing-target states; persistence tests reject stale update/completion/delete requests and do not recreate absent tasks. |
| UX-TODO-12 | PARTIAL | Successful row actions, retries/Undo coverage and validation feedback are exercised. Native focus traversal and every scoped failure state remain unverified. |
| UX-TODO-13 | PARTIAL | Native dark/light themes and Persian RTL rendering pass at the tested width/font scale; keyboard/IME, picker, dirty dialogs and date clear are exercised. Native TalkBack speech/focus traversal and the full locale/width/font matrix remain unverified. |
| UX-TODO-14 | PARTIAL | EN/FA/FR catalog and dirty-draft recreation tests pass; native Persian rendering shows a Latin title, Persian digits and Gregorian date in RTL. Full catalog-format and locale/system combinations remain incomplete. |
| UX-TODO-15 | PARTIAL | Backup settings tests and native SAF export, preview, restore-success, process persistence and isolated app-data reset pass. Retained-route invalidation after restore remains incomplete. |

## Manual/native and performance checks

The manual run used disposable `emulator-5562`, API 36, 1080×2400 at 420 dpi
(about 411 dp wide), OS font scale 1.0. The app language was Persian for RTL and
English for export/restore/reset. The app-data reset at the end returned Luma to
its default English settings. Screenshots are stored under
[`TD07 evidence`](superpowers/evidence/2026-10-19-td07/).

| Check | Result | Evidence |
| --- | --- | --- |
| TalkBack initial accessibility focus | PARTIAL / BLOCKED | TalkBack was enabled on the disposable emulator; the initial focus ring appeared on Today and the task accessibility/semantics cases passed 3/3 with TalkBack active. Spoken output and focus traversal could not be heard or captured through the available tools. [Focus screenshot](superpowers/evidence/2026-10-19-td07/talkback-initial-focus.png) |
| RTL mixed text and dates | PASS | Persian Tasks and picker screens show RTL layout, Latin `TD07 task`, Persian digits and Gregorian October 18, 2026. [Picker](superpowers/evidence/2026-10-19-td07/persian-date-picker.png), [light theme task](superpowers/evidence/2026-10-19-td07/rtl-overdue-task-light.png) |
| Keyboard/IME | PASS | Gboard remained visible while entering a title, with Save available above it. [Editor with IME](superpowers/evidence/2026-10-19-td07/keyboard-and-past-date-editor.png) |
| Date clearing | PASS | Remove then Save returned the task to the No date group. [No-date screen](superpowers/evidence/2026-10-19-td07/date-cleared-no-date.png) |
| Overdue state is readable without color | PASS | The overdue label and due date are visible text; the checkbox is separate, and accessibility content description includes status and date in both light and dark screenshots. [Dark](superpowers/evidence/2026-10-19-td07/overdue-task-dark.png), [light/RTL](superpowers/evidence/2026-10-19-td07/rtl-overdue-task-light.png) |
| Dark and light themes | PASS | Tasks were inspected in both themes at the recorded default width and font scale. [Dark Tasks](superpowers/evidence/2026-10-19-td07/tasks-dark.png), [light RTL Tasks](superpowers/evidence/2026-10-19-td07/rtl-overdue-task-light.png) |
| Dirty Back and delete dialogs | PASS | Persian dirty Back offered Continue editing and Discard; delete confirmation explained permanent removal; Cancel returned to the intact task. [Dirty Back](superpowers/evidence/2026-10-19-td07/dirty-back-dialog-persian.png), [delete confirmation](superpowers/evidence/2026-10-19-td07/delete-dialog-persian.png) |
| Process recreation | PASS | Force-stop and relaunch preserved the restored task and its No date state; the task remained in the list. The language-flow test separately preserves a civil due date through Activity recreation. |
| Native export and restore | PASS | SAF Create Document exported a ZIP; SAF Open Document selected it; preview showed one task; Replace and restore completed and the restored task was visible. [Preview](superpowers/evidence/2026-10-19-td07/restore-preview.png), [success](superpowers/evidence/2026-10-19-td07/restore-success.png) |
| Full app-data reset | PASS | `pm clear com.luma.lifeos` returned Success on `emulator-5562`; relaunch showed English defaults and Tasks reported 0 open tasks. [Post-reset Tasks](superpowers/evidence/2026-10-19-td07/after-reset-empty-tasks.png) |
| CRUD isolation | PASS | See `TodoIsolationAcceptanceTest`; event/habit counts, Timeline, summary, Review and Search remained unchanged after every task write. Network and OS permission state were not measured. |
| 10,000-task scale | PASS | 50 Open rows and 3 Today rows loaded with full counts. Repository p50/p95: Open 8.10/10.89 ms; Today 1.66/2.76 ms on API 36. The Open p95 is below the 300 ms target. |
| Full layout / target-size matrix | NOT RUN | Only the native 411 dp / 100% font configuration and the Compose 320 dp / 200% semantics configuration were checked; physical target dimensions and remaining combinations were not measured. |

The Compose host reported `Focused=false` even for a minimal Material button
after `RequestFocus`, so native TalkBack remains a separate acceptance result;
the TalkBack screenshot documents an initial focus ring, not spoken feedback.

TD07 remains **In progress** until the full app/data suites are green, the
remaining criteria are run, and the unresolved failures/TalkBack gap are fixed or
explicitly accepted. Do not mark the Tasks feature release-ready from focused
results alone.
