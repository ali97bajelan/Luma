# Task Due Time Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional hour/minute deadlines to tasks, including durable editing, localized display, ordering, migration and backup compatibility.

**Architecture:** Extend the existing nullable civil date with nullable `LocalTime`; persist minutes since midnight. Keep task repositories and revision-based writes, add the field throughout editor recovery and widget projections, and reuse the existing time-input and formatting patterns.

**Tech Stack:** Kotlin, Java Time, Compose Material 3, SavedStateHandle, Room, JUnit and Compose instrumentation; existing project dependencies only.

**Spec:** [Task due time design](../specs/2026-10-23-task-due-time-design.md). Status: source implementation complete; behavioral validation pending. See the execution ledger and [acceptance record](../../validation/2026-10-23-task-due-time.md).

## Execution status (2026-10-23)

Tasks 1–6 have source, regression-test-source and documentation changes in the working tree. Room schema generation, production compilation, debug APK assembly, Android test-source compilation and lint passed. No Gradle test task, emulator, or device validation was run, as requested. DUE-01–08 remain NOT RUN; see the acceptance record. No commit was created.

## Global Constraints

- Native Android/Kotlin/Compose Material 3; preserve minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- No new dependencies, runtime permissions or network services.
- Support System/Light/Dark, English/French/Persian, Persian RTL and the Android 12/24-hour preference.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL, IME and system insets.
- Preserve existing title/note limits, due-date years 1–9999, task IDs, revision checks, completion/Undo, delete and restore invalidation.
- Do not run Gradle tests or an emulator; the user explicitly requested implementation without either. Record every test/device acceptance item as NOT RUN.

## Review Focus

1. Midnight must survive as minute `0`, never be confused with null: Tasks 1–3.
2. Canceling a picker, then recreating or reopening it, must not commit temporary input: Tasks 4–5.
3. Old archives use exact legacy headers; schema-4 import must work after the staging schema changes: Task 3.
4. Time-only differences must participate in dirty state and idempotent/conflicting replay: Tasks 2 and 4.
5. SQL and domain sorting must agree across date-only rows, equal minutes and page boundaries: Tasks 1–2 and 6.

## File map and dependencies

Paths below are repository-relative. Braces list exact file names within that directory; files are modifications unless marked Create. Preserve existing production interfaces except the explicit additions below.

| Task | Files and responsibility |
| --- | --- |
| 1 | `core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/{TodoModels,TodoRules}.kt`: due-time value/invariants/order. `core/domain/src/main/kotlin/com/luma/lifeos/domain/widget/WidgetModels.kt`: widget task time. Test: `core/domain/src/test/kotlin/com/luma/lifeos/domain/todo/TodoRulesTest.kt` |
| 2 | `core/data/src/main/kotlin/com/luma/lifeos/data/todo/{TodoEntity,TodoMapping,TodoDao}.kt`, `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomTodoRepository.kt`, `core/data/src/main/kotlin/com/luma/lifeos/data/db/LumaDatabase.kt`, `core/data/src/main/kotlin/com/luma/lifeos/data/widget/RoomWidgetStore.kt`; Create generated `core/data/schemas/com.luma.lifeos.data.db.LumaDatabase/6.json`. Tests: `core/data/src/androidTest/kotlin/com/luma/lifeos/data/{TodoMigrationTest,TodoPersistenceTest,WidgetStoreAcceptanceTest}.kt` |
| 3 | `core/data/src/main/kotlin/com/luma/lifeos/data/backup/{BackupArchive,BackupValidation,RoomBackupService}.kt`; strict version-aware task import. Tests: `core/data/src/test/kotlin/com/luma/lifeos/data/backup/{BackupCodecTest,BackupValidationTest}.kt`, `core/data/src/androidTest/kotlin/com/luma/lifeos/data/backup/TodoBackupTest.kt` |
| 4 | `app/src/main/java/com/luma/lifeos/ui/todo/{TodoUiState,TodoEditorViewModel}.kt`; complete draft/baseline/pending lifecycle. Tests: `app/src/test/java/com/luma/lifeos/ui/todo/{TodoEditorViewModelTest,TodoRepositoryFixture,TodoRestoreInvalidationTest}.kt` |
| 5 | `app/src/main/java/com/luma/lifeos/ui/todo/{TodoEditorScreen,TodoEditorRoute,TodoRow}.kt`, `app/src/main/java/com/luma/lifeos/widget/WidgetRenderer.kt`, `app/src/main/res/values{,-fr,-fa}/strings.xml`; picker and display. Tests: `app/src/androidTest/java/com/luma/lifeos/todo/{TodoEditorFlowTest,TodoLanguageFlowTest,TodoAccessibilityTest,TodayTasksFlowTest}.kt`, `app/src/androidTest/java/com/luma/lifeos/widget/WidgetRendererAcceptanceTest.kt` |
| 6 | `docs/{TODO_TASKS,BACKUP_FORMAT}.md`, `README.md`; feature/version documentation. Create `docs/validation/2026-10-23-task-due-time.md` for acceptance results (use the actual execution date if later) |

Order: **1 → 2 → 3 → 4 → 5 → 6**. Before starting, inspect workspace changes and the live Room/archive versions. Rebase the proposed 6/5 versions if another feature has shipped. Do not implement or merge the deferred tags proposal as part of this work.

### Task 1: Define minute-precision civil time

**Interfaces:** Append `dueTime: LocalTime? = null` to `TodoTask`, `TodoInput` and `WidgetRow.Task`. Extend `normalizeTodoInput(input: TodoInput): TodoInput` and `openTodoTaskComparator`; add `TodoField.DUE_TIME`. Existing repository and bucket/eligibility signatures stay unchanged.

- [x] Add domain cases: `minutePrecisionDueTimeAcceptsBoundaries` asserts null, `00:00`, `09:05`, `23:59` survive normalization; `timeRequiresDate` asserts `DUE_DATE/REQUIRED`; `subMinuteDueTimeIsRejected` asserts `DUE_TIME/OUT_OF_RANGE` for seconds or nanoseconds. Assert matching `TodoTask` construction failures. (Authored; tests not run.)
- [ ] **NOT RUN (user request):** `./gradlew -PdomainOnly=true :core:domain:test --tests 'com.luma.lifeos.domain.todo.TodoRulesTest'`; the new cases were not run before implementation.
- [x] Implement the new properties/invariants and preserve time through normalization. Sort equal-date tasks by time with null last, then retain creation/ID tie breakers. Do not change completed order, buckets or eligibility.
- [x] Add `mixedDueValuesHaveStableOrder`: shuffled yesterday/today/tomorrow/undated rows sort by the spec, with `00:00 < 09:05 < 23:59 < null` within today; equal date/time uses creation/ID. Add assertions that a today/09:00 task is still TODAY and eligible independent of clock time. (Authored; tests not run.)
- [ ] **NOT RUN (user request):** focused domain test and full `:core:domain:test` commands. No task-level commit was created; see the suggested feature commit message in the handoff.

### Task 2: Persist time and align SQL/widget queries

**Interfaces:** Append `dueTimeMinutes: Int? = null` with `@ColumnInfo(name = "due_time_minutes")` to `TodoEntity`, `TodoPageRow` and `WidgetTodoRow`. Add `dueTimeMinutes: Int?` to `TodoDao.update` beside `dueDate`. Existing `TodoInput.toEntity(id, revision, now)` and `TodoEntity.toDomain()` map the new value. Repository methods retain signatures; `matches` compares time too. `RoomWidgetStore` populates `WidgetRow.Task.dueTime`.

- [x] Extend `TodoMigrationTest` with populated version-5 tasks, including undated and completed tasks. Assert migration to version 6 retains title/note/date/status/timestamps/IDs/revisions and gives all rows null time; validate all historical migration paths to latest and both final indexes. (Authored; tests not run.)
- [x] Extend `TodoPersistenceTest`: create/update/reopen retain 0, 545 and 1439 minutes; clearing time/date persists null correctly; identical revision replay returns the committed row; a different time under the same next revision conflicts; stale revisions cannot overwrite time. Extend widget storage acceptance to assert selected minutes and status/Undo preservation. (Authored; tests not run.)
- [ ] **NOT RUN (user request):** focused migration/persistence/widget instrumentation and pre-implementation behavior check.
- [x] Implement Room 5→6, replace the open index, retain the completed index and generate schema 6 through the existing Room/KSP build. Reject non-null minutes without a date or outside 0–1439. Carry the column through every open/completed/Today/widget projection and mapping, including count-only/empty page sentinel rows. (KSP schema generation completed.)
- [x] Update SQL orders to due date, explicit null-time rank, minute, creation and ID. Keep null-date placement, count queries, limits, offsets and completed ordering. Update transactional repository writes/matching and widget-store projection.
- [x] Add mixed-time SQL-vs-domain ordering assertions with at least 60 same-date tasks and >50-row boundaries; check no missing/duplicate IDs and equal total counts. Date-only and midnight cases must appear in both paths. (Authored; tests not run.)
- [ ] **NOT RUN (user request):** `./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.data.TodoMigrationTest,com.luma.lifeos.data.TodoPersistenceTest,com.luma.lifeos.data.WidgetStoreAcceptanceTest`. Schema 6 was generated; no task-level commit was created.

### Task 3: Version backups and adapt legacy task headers

**Interfaces:** Advance `CURRENT_BACKUP_SCHEMA_VERSION` to 5; `backupTables(5)` returns the unchanged schema-4 inventory. Create `internal fun backupImportColumns(table: String, schemaVersion: Int, currentColumns: List<String>): List<String>` in `BackupArchive.kt`: only schema-4 `todo_task` removes `due_time_minutes`; other supported cases retain current columns. Use it in staging before `BackupRows.read`. The schema-4 header remains `id,title,note,due_date,completed_at,created_at,updated_at,revision_token` in that tab-separated order.

- [x] Extend codec tests to assert schema 4 and 5 inventories match, current version is 5, the legacy helper returns the exact eight columns, and schema 5 requires the new column. Add validation tests for minute values -1, 1440, wrong cell type, and non-null minute without date. (Authored; tests not run.)
- [ ] **NOT RUN (user request):** `./gradlew :core:data:testDebugUnitTest --tests 'com.luma.lifeos.data.backup.BackupCodecTest' --tests 'com.luma.lifeos.data.backup.BackupValidationTest'`; no pre-implementation failure run was performed.
- [x] Implement version-aware column selection. Derive column descriptors/types and INSERT columns for the chosen archive version; synthesize null time in the validation row for schema 4, while SQLite's absent column remains null. Preserve strict TSV headers, cell types, archive inventory checks and staged replacement. Extend `validateBackupRow` to pass validated minutes into `TodoEntity`.
- [x] Extend `TodoBackupTest` with schema-5 null/0/545/1439 round trips and real schema-4 payloads using the exact old header. Assert schemas 1–3 still replace with zero tasks; restored task IDs/timestamps survive and revision tokens rotate. Adjust existing fixtures that previously relied on current-schema headers under old manifest versions. (Authored; tests not run.)
- [x] Test source covers schema-5 missing column, schema-4 unexpected column and invalid minute/date pairs rejecting before live data changes, plus null/empty cell distinction. (Authored; tests not run.)
- [ ] **NOT RUN (user request):** focused backup JVM and instrumentation commands. No task-level commit was created.

### Task 4: Carry time through editor recovery

**Interfaces:** Append `dueTime: LocalTime? = null` to `TodoDraft`; include it in `toInput()`. Add `TodoEditorViewModel.setDueTime(value: LocalTime?): Unit`; preserve `setDueDate(value: LocalDate?)`, with null clearing date/time atomically. `setDueTime(nonNull)` without a draft date does nothing, so UI events cannot create an invalid draft; domain validation still rejects invalid external inputs.

- [x] Extend `TodoEditorViewModelTest` with `timeOnlyEditIsDirtyAndCanRevert`, `removeDateClearsTimeAtomically`, `dateChangeRetainsTime`, `oldSavedStateHasNoTime`, `confirmedMinuteSurvivesRecreation`, `pendingSaveReplaysExactMinute`, and `reloadUsesLatestTime`. Assert midnight is serialized distinctly from null and that blocked edits cannot change time. (Authored; tests not run.)
- [x] Update `TodoRepositoryFixture` to retain/compare time, so recovery tests fail when mappings drop it. Extend restore-invalidation tests to seed a time-bearing pending save and prove it cannot replay after archive replacement. (Authored; tests not run.)
- [ ] **NOT RUN (user request):** `./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.ui.todo.TodoEditorViewModelTest' --tests 'com.luma.lifeos.ui.todo.TodoRestoreInvalidationTest'`; no pre-implementation failure run was performed.
- [x] Implement due-time editing and all `TodoTask`/draft/input conversions. Save draft key `todo_draft_due_time`, baseline key `todo_baseline.due_time` and pending key `todo_pending_input.due_time` as ISO `HH:mm` or empty for null. Derive baseline/pending keys from existing `KEY_BASELINE`/`KEY_PENDING_INPUT` constants. Missing keys mean null; if a restored date is absent, normalize its draft time to null. Include pending time keys in cleanup.
- [x] Update comparable/equivalent input, persisted baseline, retry reconciliation, confirmed reload and every loading/recovery fallback that constructs a `TodoDraft`. Completion/Undo and delete carry the field through the same task mapping path.
- [ ] **NOT RUN (user request):** focused and full Todo editor unit tests. No task-level commit was created.

### Task 5: Add time input and localized deadline display

**Interfaces:** Append `onDueTime: (LocalTime?) -> Unit = {}` to `TodoEditorScreen` and wire it in `TodoEditorRoute`. Keep existing callbacks/test tags. Reuse `rememberDisplayTimePolicy`, `DisplayTimePolicy.time()`, `localizedTime` and `LumaAlertDialog`; follow `HabitLogCorrectionDialog`'s `rememberTimePickerState`/`TimeInput` pattern. Add strings `todo_due_time_label`, `todo_no_time`, `todo_add_time`, `todo_change_time`, `todo_remove_time`, `todo_time_out_of_range`, and two-placeholder `todo_due_date_time`; reuse `select_time` and `cancel`.

- [x] Extend Compose editor tests with add/default-09:00/confirm-09:05/change/remove; assert Cancel, Back and outside dismissal preserve draft/dirty state and reopening resets canceled input. Test remove date clears both fields, date change retains time, invalid minute input is rejected by `TimeInput`, and conflict closes a dialog before stale confirmation. (Authored; Android test sources compiled but not run.)
- [x] Add initial display assertions: editor rendering covers `00:00`, `09:05` and `23:59` using the device's current hour preference; language recreation checks 09:00 in EN/FR/FA; widget detail rendering checks 09:05. (Authored; tests not run.)
- [ ] Remaining display assertion coverage: locale × hour-preference matrix, completed-row original due label, Today due label and summary-only widget behavior were not fully authored. DUE-04 device/runtime acceptance remains NOT RUN.
- [ ] **NOT RUN (user request):** focused app instrumentation; no pre-implementation failure run was performed.
- [x] Implement the time row/dialog, test tags, cancel/reset rules and editability guards. The unconfirmed picker is transient and closes on recreation. Map `DUE_TIME` errors to localized accessible feedback and keep time UI hidden without a date.
- [x] Add all copy to EN/FR/FA catalogs. Use the shared display policy for the editor and `TodoRow`; append time to formatted due date before passing through existing Today/overdue/original-due templates. In `WidgetRenderer`, use the widget's localized context and Android hour preference, then bidi-wrap the combined deadline before inserting into existing widget label templates. Summary text remains count/empty-state only; due details are rendered in task rows.
- [ ] **NOT RUN (user request):** focused app instrumentation and `./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.language.*'`. No task-level commit was created.

### Task 6: Integrated acceptance and documentation

**Interfaces:** No new production interface. Acceptance IDs DUE-01–08 map to Tasks 1, 5, 2/4, 5, 1/2, 2, 3 and 4/5 respectively.

- [ ] **NOT RUN (user request):** Gradle unit and instrumentation test tasks from the full acceptance command. The non-test build/lint tasks and Android test source compilation passed (see acceptance record).
- [ ] **NOT RUN (user request):** focused data and app instrumentation commands; no emulator was started.
- [ ] **NOT RUN (user request):** visual, keyboard, TalkBack, widget refresh and window/font/theme/locale/RTL/IME matrix.
- [ ] **NOT RUN (user request):** timezone/DST behavior and calendar-day overdue checks.
- [x] Update `docs/BACKUP_FORMAT.md` with archive schema 5, Room 6, minutes/null semantics, strict legacy adaptation and restore revision behavior. Update `docs/TODO_TASKS.md` and README to describe optional time, calendar-day buckets and the absence of reminders. Link both feature documents from the task handoff.
- [x] Create the acceptance record with base SHA/date/device and PASS/FAIL/NOT RUN for every DUE ID. No screenshots or behavioral results are available because tests and device checks were not run. No documentation commit was created.

## Handoff

Execution status: source implementation, regression-test source, schema generation, Kotlin compilation, debug APK assembly, Android test APK/source compilation, lint and documentation are recorded above. Behavioral acceptance is pending; Gradle tests and emulator/device checks remain NOT RUN per the user's request. Working-tree changes are uncommitted.
