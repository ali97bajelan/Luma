# Calendar & Agenda Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. The user's phase ordering overrides test-first execution: all implementation tasks finish before test authoring or execution in QA.

**Goal:** Add a native month/week Calendar & Agenda screen for dated tasks, scheduled habits and saved reflections using the supplied light/dark references.

**Architecture:** A read-only calendar repository projects existing Room task, habit and journal tables. A saved-state ViewModel coordinates period/day/filter state and per-source loading; existing task editor, mutation controller and capture routes own all writes. Calendar replaces Habits in the third primary navigation slot; the full Habits destination moves to More with its existing routes and actions.

**Tech Stack:** Kotlin, java.time, coroutines/Flow, Room, Compose Material 3, Navigation Compose, SavedStateHandle; existing test infrastructure in QA only.

**Spec:** [Calendar & Agenda specification](../specs/2026-10-25-calendar-agenda-design.md)

**Tasks:** [Status and dependencies](../../CALENDAR_TASKS.md)  
**QA:** [Fixture, scenarios and evidence](../../CALENDAR_QA.md)

Status: Proposed, not executed. Read spec and plan together. This handoff does not authorize feature implementation or claim design approval.

## Global Constraints

- Native Android/Kotlin/Compose Material 3; preserve minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- No new dependencies, runtime permissions, network services, database tables, schema versions or archive formats.
- Support System/Light/Dark, English/French/Persian, Persian RTL and the Android 12/24-hour preference.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL, IME and system insets.
- Preserve task IDs, title/note limits, due-date years 1–9999, minute precision, revision checks, completion/Undo, confirmed deletion and restore invalidation.
- Main-bar order: Today, Timeline, Calendar, Review, More. Habits is a secondary destination under More; retain its existing route and actions.
- All implementation tasks precede QA. No Gradle commands, test execution or emulator use during implementation; test authoring and execution belong to QA.

## Review Focus

1. Today carry-in versus historical/future exact-date membership: CQA-09/10 prove no backlog is assigned to another date.
2. Late date/filter/zone responses and source errors: CQA-25/29 prove stale data and false zero totals cannot appear.
3. Locale, 48 dp cells and seven-column layout at 320 dp: CQA-22/30/31 prove date and action access without overlapping targets.
4. Restore or concurrent edits during completion/Undo: CQA-19/20/24 prove revision safety and retained recovery.
5. Month bounds, Gregorian overflow and timezone/day boundaries: CQA-05–08/14–16 prove civil-date correctness.

## File and interface map

All new files below are planned, not present yet. Existing package conventions remain unchanged.

| Area | Files | Responsibility |
| --- | --- | --- |
| Domain | `core/domain/src/main/kotlin/com/luma/lifeos/domain/calendar/CalendarModels.kt`, `CalendarRules.kt`, `CalendarRepository.kt` | Date ranges, marker/read models, pure membership/navigation, read-only contracts |
| Data | `core/data/src/main/kotlin/com/luma/lifeos/data/calendar/CalendarDao.kt`, `CalendarRows.kt`; `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomCalendarRepository.kt` | Bounded read projections and mappings over saved data |
| Composition | `core/data/src/main/kotlin/com/luma/lifeos/data/db/LumaDatabase.kt`, `core/data/src/main/kotlin/com/luma/lifeos/data/LumaStorage.kt`, `app/src/main/java/com/luma/lifeos/MainActivity.kt` | DAO/provider wiring, no schema or version changes |
| State/UI | `app/src/main/java/com/luma/lifeos/ui/calendar/CalendarUiState.kt`, `CalendarViewModel.kt`, `CalendarRoute.kt`, `CalendarScreen.kt`, `CalendarGrid.kt`, `CalendarAgenda.kt` | Retained state, scoped flows, stateless layout and agenda sections |
| Integration | `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`, `ui/navigation/AppDestinations.kt`, `ui/more/MoreRoute.kt`, `ui/QuickCaptureSheet.kt`; inspect `ui/navigation/PrimaryNavigationBar.kt`, `ui/habits/HabitsRoute.kt` | Calendar primary-tab integration, More → Habits/legacy entry compatibility, dated capture and restore wiring |
| Resources | `app/src/main/res/values/strings.xml`, `values-fr/strings.xml`, `values-fa/strings.xml`; optional `res/drawable/ic_calendar.xml` | Offline localized copy/plurals and icon consistent with existing assets |
| QA only | Test paths listed under CAL-Q01/Q02; `docs/validation/2026-10-25-calendar-agenda.md` | Automated regression coverage and actual-run evidence |

Use these shared domain contracts (implement bodies idiomatically; private DTOs/helpers are builder choices):

```kotlin
enum class CalendarMode { MONTH, WEEK }
enum class CalendarFilter { ALL, TASKS, OVERDUE }
data class CalendarRange(val start: LocalDate, val through: LocalDate)
data class CalendarTaskMarker(val count: Int, val overdue: Boolean)
data class CalendarReflection(val id: EventId, val title: String?, val preview: String)
data class CalendarReflections(val total: Int, val latest: CalendarReflection?)

interface CalendarRepository {
    fun observeTaskMarkers(range: CalendarRange, today: LocalDate): Flow<Map<LocalDate, CalendarTaskMarker>>
    fun observeHabitMarkers(range: CalendarRange): Flow<Map<LocalDate, Int>>
    fun observeJournalMarkers(range: CalendarRange, zone: ZoneId): Flow<Map<LocalDate, Int>>
    fun observeTasks(date: LocalDate, today: LocalDate, overdueOnly: Boolean, limit: Int = 50): Flow<TodoPage>
    fun observeOverdueCount(today: LocalDate): Flow<Int>
    fun observeHabits(date: LocalDate): Flow<List<HabitDay>>
    fun observeReflections(date: LocalDate, zone: ZoneId): Flow<CalendarReflections>
}

fun calendarRange(selected: LocalDate, mode: CalendarMode, firstDay: DayOfWeek): CalendarRange
fun moveCalendarSelection(selected: LocalDate, mode: CalendarMode, delta: Int): LocalDate
fun calendarTaskEligible(task: TodoTask, selected: LocalDate, today: LocalDate, overdueOnly: Boolean): Boolean
```

`delta` accepts -1/+1; bound navigation clamps within supported years. Range is inclusive, maximum 42 valid dates; render out-of-range padding separately as null grid cells. Empty maps mean a successfully loaded zero, never a failure. Repository errors propagate so UI can distinguish them. Reflection preview is at most 160 Unicode code points of saved body, whitespace collapsed; latest selection uses occurrence descending/ID ascending. `CalendarReflections(0, null)` is the sole empty result.

## Phase 1 — Implementation only

No test files, test runs, Gradle invocations, app installation or emulator work in this phase. “Source complete” is the strongest permitted completion claim. Complete CAL-I01–I06 before CAL-Q01 begins.

### CAL-I01 — Calendar rules and read contracts

**Files:** Create domain calendar files above. Read existing `domain/todo/TodoRules.kt` and `domain/habit/HabitModels.kt`; do not change their semantics.

**Consumes:** `TodoTask`, `TodoPage`, `HabitDay`, `EventId`, `LocalDate`, `ZoneId`.  
**Produces:** All contracts in the shared interface block; pure calendar rules for I02/I03.

- [ ] Define immutable read models and validate ranges/limits (date years 1–9999, start ≤ through, span ≤42; limit positive).
- [ ] Implement `calendarRange` for locale weekday alignment, clipped bounds and month/week ranges; keep padding information in UI cells.
- [ ] Implement `moveCalendarSelection` using `plusMonths`/`plusWeeks` with explicit supported-bound handling; preserve clamped-day semantics Jan 31 → Feb 28 → Mar 28.
- [ ] Implement `calendarTaskEligible` exactly as CAL-03, including strict `< today` overdue and exclusion of completed/undated tasks. Reuse `openTodoTaskComparator` for ordering.
- [ ] Source-review rule branches against CAL-02–04 and list test obligations CQA-04–16 for QA. Do not execute tests.

**Ready for QA when:** Contracts and pure rule source exist with no new persistence or mutation API.

### CAL-I02 — Bounded Room calendar projection

**Files:** Create `data/calendar/CalendarDao.kt`, `CalendarRows.kt`, `repository/RoomCalendarRepository.kt`. Modify `LumaDatabase.kt` to expose `calendar(): CalendarDao`; modify `LumaStorage.kt` to expose `calendar: CalendarRepository`.

**Consumes:** I01 contracts; existing `todo_task`, `habit`, `habit_schedule`, `habit_log`, `habit_day_choice`, `life_event`, `journal_entry`; existing `HabitRepository.observeDay`.  
**Produces:** `RoomCalendarRepository(database: LumaDatabase, habits: HabitRepository) : CalendarRepository` and `LumaStorage.calendar`.

- [ ] Add range-bounded task aggregates grouped by original due_date, plus global overdue count. Add task page query with exact selected-day/today-carry-in/overdue predicates, full count and existing SQL time/null/tie ordering from TodoDao. Count and rows use one consistent Room snapshot per result.
- [ ] Add bounded habit schedule aggregates for valid dates: use a parameterized recursive date CTE limited to the requested ≤42 days, latest effective schedule per habit/date, ACTIVE lifecycle, start date and weekday mask. Count each habit once independent of log count/skip. Reuse existing day observation/mapping for selected-day detail and filter to active scheduled days.
- [ ] Add journal aggregates by device-zone day boundaries computed in Kotlin, joining actual journal content to life_event without requiring type JOURNAL (promoted journals remain eligible). Apply manual-source/completeness rules from existing journal reads. Do not count generic memories/moods as notes. Select latest journal metadata and only its bounded preview, plus full count.
- [ ] Use Room observation/invalidation over each source's actual tables. Ensure task completion/date edits, journal promotion/deletion, habit schedule changes and restore re-emit; do work off the main thread. Return independent source flows; propagate errors.
- [ ] Register the DAO/provider without changing Room version 6, exported schema inventory, backups, permissions or dependencies. Inspect SQL/domain predicate equivalence and do not invoke schema generation/builds.

**Ready for QA when:** Indexed/bounded reads exist; there is no scan of all global task rows in the app and no per-cell loading of journal bodies. QA must validate query compilation and actual schedule/date results.

### CAL-I03 — Saved state and stale-response protection

**Files:** Create `ui/calendar/CalendarUiState.kt`, `CalendarViewModel.kt`; use `ui/today/CurrentDay.kt` unchanged unless a reusable visibility hook is necessary.

**Consumes:** I01 rules, I02 repository, existing foreground/day/zone updates and restore generation.  
**Produces:** `CalendarViewModel(repository: CalendarRepository, saved: SavedStateHandle)`; `state: StateFlow<CalendarUiState>`; actions `selectDate(LocalDate)`, `setMode(CalendarMode)`, `setFilter(CalendarFilter)`, `movePeriod(Int)`, `goToToday()`, `loadMore()`, `retry(CalendarSource)`, `setEnvironment(today: LocalDate, zone: ZoneId, firstDay: DayOfWeek, restoreGeneration: Int)`.

- [ ] Define `CalendarSource { TASKS, HABITS, JOURNALS }` and per-source sealed Loading/Ready/Error state. Separate task page, marker, overdue count and reflection/habit detail status so unrelated failures cannot become false zeroes.
- [ ] Persist selection/mode/filter using stable primitive values; parse bad saved dates safely. Preserve LazyList position with Compose saveable state; restore data limit as 50 rather than saving rows.
- [ ] Build keyed `flatMapLatest` observations using D/T/range/filter/zone/restore generation. Clear prior-date detail immediately; gate every emission by current request key. Source Retry restarts only that source. Within one source avoid a fresh full count paired with stale rows/markers as if already consistent.
- [ ] On day/zone changes preserve selection and recompute membership/labels. Reset limit on date/filter/day/zone changes and invalidate rows/pending generations on restore.
- [ ] Expose full totals and unknown/error status separately from list size; derive All Items and carry-in caption per CAL-03.

**Ready for QA when:** State transitions can be traced from source, including late flow emissions, partial failures and process restoration. CQA-22–26/29 remain unexecuted.

### CAL-I04 — Native calendar and agenda UI

**Files:** Create `CalendarScreen.kt`, `CalendarGrid.kt`, `CalendarAgenda.kt`, `CalendarRoute.kt`; add localized resources in all three catalogs. Read shared `LumaUiTokens.kt`, `LumaUiComponents.kt`, `AppPageScaffold.kt`, `TodoRow.kt`, `TodoActionFeedback.kt`.

**Consumes:** I03 state/actions and existing `TodayTasksViewModel`, `TodoActionFeedbackHost`.  
**Produces:** `CalendarRoute(repository: CalendarRepository, todos: TodoRepository, today: LocalDate, zone: ZoneId, restoreGeneration: Int, onTask: (UUID) -> Unit, onAddTask: (LocalDate) -> Unit, onSchedule: (LocalDate) -> Unit, onHabit: (UUID, LocalDate) -> Unit, onJournal: (EventId) -> Unit, onWriteReflection: (LocalDate) -> Unit, onTimeline: (LocalDate) -> Unit, onAllTasks: () -> Unit)`.

- [ ] Compose existing primary-page scaffold without a Back arrow, Calendar & Agenda heading, Today control, adaptive month header, segmented Month/Week controls, Previous/Next and date grid. Render actual seven-column date cells and ≥336 dp horizontal viewport when needed for 48 dp targets; bring selection into view.
- [ ] Render selected/today/overflow states, typed marker legend and accessible full-date/count labels; disable bound arrows/blanks. Use semantic theme roles and bundled typography in both themes.
- [ ] Render exact-date ribbon, All Items/Tasks/Overdue chips, explicit overdue scope and carry-in caption. Render 50-row task page with full count/Load more, due badges, optional note preview, separate checkbox/open/menu actions, and no fake tags.
- [ ] Connect completion, retry, conflict, missing and Undo to existing controller/feedback patterns. Recovery stays reachable when its task row disappears from the active filter. Invalidate the controller on restore as Today does; do not duplicate mutation semantics.
- [ ] Render scheduled-habit read-only rows and tracking-zone caption; latest real reflection preview, empty Write reflection and dated View all journals. Handle each source's loading/error/empty state with Retry and healthy sources still active.
- [ ] Render one Schedule FAB and one scroll container; reserve measured bottom clearance for FAB/snackbar/bars. Add localized strings/plurals, state descriptions and keyboard/TalkBack actions. Do not add dead mockup controls.

**Ready for QA when:** All CAL-01–07 UI states/actions are represented in source in both theme roles; no visual/behavioral acceptance claim yet.

### CAL-I05 — Navigation, dated capture and caller compatibility

**Files:** Modify `AppDestinations.kt`, `LifeOsApp.kt`, `MainActivity.kt`, `MoreRoute.kt`, `QuickCaptureSheet.kt`; inspect `PrimaryNavigationBar.kt` and existing `HabitsRoute.kt`/`HabitsScreen.kt` Back support; add localized Calendar tab/Habits row strings and icon as needed.

**Consumes:** I02 provider and I04 route callbacks; existing `openTodoEditor`, `openCapture`, editor results and restore generation.  
**Produces:** `AppRoutes.CALENDAR = "calendar"`; `PrimaryDestination.CALENDAR` replacing `HABITS` in slot three; a nullable calendar provider trailing/defaulted in `LifeOsApp`; trailing/defaulted More `onHabits: (() -> Unit)? = null`; existing `AppRoutes.HABITS` retained as a secondary route; compatible dated Quick Capture additions from CAL-05.

- [ ] Pass `storage.calendar` from MainActivity; add Calendar to the primary route loop in place of Habits, with a localized label/compact label and calendar icon. Ensure the shell recognizes Calendar as primary and renders exactly Today/Timeline/Calendar/Review/More; render its unavailable fallback if the provider is absent. Scope Calendar state to its back-stack entry, default to today and use existing primary-tab save/restore/Back behavior.
- [ ] Register the retained `habits` route separately, outside the primary loop, and add More → Habits before Tasks with `more_habits` test tag. Reuse `HabitsRoute` with its existing non-null `onBack` support. Use a Habits-entry saved date (More defaults to today; explicit dated callers override it), retaining it after child navigation and keeping Today/Timeline and Calendar date state independent.
- [ ] Replace `openHabitsDate` primary `popUpTo`/restore behavior with secondary navigation and preserve all Today/Timeline/Review/capture/widget dated habit links and restored legacy `habits` stacks. Existing Today habit previews stay present. Verify shell Back/bottom-bar behavior for primary Calendar versus secondary Habits in source; do not add a duplicate Calendar row in More.
- [ ] Wire Add task and editor menu to `openTodoEditor` without overwriting edit dates; route confirmation deletion through the editor. Deliver existing editor save/completion result once to the right Calendar feedback host.
- [ ] Add trailing `taskDueDate`/`onScheduledTask` sheet parameters without changing existing callers' `onTask` type. Calendar Schedule sets task default D explicitly, with a task-specific due-date caption independent of record chips; dismiss the sheet before navigation. Existing Timeline/Review Quick Capture remains undated for tasks.
- [ ] Wire journal open, Write reflection and View all to existing dated routes and capture date validation. Wire habit detail using the selected civil date; do not convert it to device zone or enable future logging.
- [ ] Carry restore generation into Calendar and child editors/actions; clear pending capture context on dismiss, Back and data restore.

**Ready for QA when:** Every visible control has a concrete route/action, with source-compatible existing QuickCapture call sites and no new writes outside existing repositories.

### CAL-I06 — Source review and QA handoff

**Files:** Update `docs/CALENDAR_TASKS.md`; create `docs/validation/2026-10-25-calendar-agenda.md` with environment/evidence fields, all checks NOT RUN. Update `docs/luma-redesign/route-coverage.md` for new route ownership at implementation time.

**Consumes:** I01–I05 source changes.  
**Produces:** Source-complete handoff with known risks and QA entry decision.

- [ ] Review spec-to-source coverage, signatures/call sites, resource-key parity, request keys, null/bounds handling and reference deviations using file reads/searches and `git diff --check` only.
- [ ] Inspect changed-file scope: only the specified Calendar-for-Habits navigation replacement and More → Habits relocation; no task tags, unrelated navigation changes, schema/archive/permission/dependency changes or unrelated working-tree edits.
- [ ] Record source revision plus dirty patch identifier, included implementation files, proposed design decisions and known concerns in the handoff. Mark each I-task source complete or incomplete with reasons.
- [ ] Confirm I01–I06 all source complete before opening QA. Explicitly record “Gradle/build/tests/emulator NOT RUN during implementation.”

**Gate:** If any I-task is incomplete, QA cannot start. Static review is not a test pass.

## Phase 2 — QA only

### CAL-Q01 — Automated test authoring and host/build checks

**Files to create:**

- `core/domain/src/test/kotlin/com/luma/lifeos/domain/calendar/CalendarRulesTest.kt`
- `app/src/test/java/com/luma/lifeos/ui/calendar/CalendarViewModelTest.kt`

**Existing infrastructure:** Existing JUnit/coroutines fixtures; no new dependencies.

- [ ] Author domain tests for CQA-04–16: exact arrays/ranges for leap/overflow/week locales/year bounds, membership sets, deterministic sorting, null versus midnight and date-based overdue.
- [ ] Author ViewModel tests for CQA-22–26/29 using controllable flows: late A-after-B ignored, source retry isolated, unknown totals not zero, paging reset, rollover preserves selection, restore invalidation and saved-state defaults.
- [ ] Run host/build/lint commands below; capture complete logs and test counts. Fix failures within Calendar scope, then rerun affected checks in QA.

```sh
./gradlew :core:domain:test :app:testDebugUnitTest :core:data:testDebugUnitTest
./gradlew :app:assembleDebug :app:lintDebug :core:data:lintDebug
./gradlew :app:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest
```

Expected: each command exits 0, tests have no failures, app/test APKs assemble and lint has no errors. Existing failures must be named and compared to baseline; they are not waived automatically. Build blocked by SDK/dependency/environment is BLOCKED, not PASS.

### CAL-Q02 — Room and end-to-end automation on a QA device

**Files to create:**

- `core/data/src/androidTest/kotlin/com/luma/lifeos/data/CalendarProjectionTest.kt`
- `app/src/androidTest/java/com/luma/lifeos/calendar/CalendarFlowTest.kt`
- `app/src/androidTest/java/com/luma/lifeos/calendar/CalendarSemanticsTest.kt`

- [ ] Author Room tests with the shared fixture for CQA-09–16/26–29: exact IDs/totals/order, observed invalidation, promoted journal counted once, schedule versions/start/lifecycle/skip, DST boundaries, >50 rows and snapshot consistency.
- [ ] Author flow/semantics tests for CQA-01–08/17–25/30–32: Calendar tab placement/reselection/Back, More → Habits/legacy links, entry/return, period/filter controls, dated editor/capture and existing undated paths, completion/Undo/conflict/retry/restore, localized semantics and bounded viewport.
- [ ] Use an isolated QA install/AVD and recorded API/device; run the focused suites and existing TodayTasks, Todo, QuickCapture, Habits, navigation, language and restore regressions. Then run full app/data connected checks if focused checks pass.

```sh
./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.data.CalendarProjectionTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.luma.lifeos.calendar
./gradlew :app:connectedDebugAndroidTest :core:data:connectedDebugAndroidTest
```

Expected: exit 0 and zero failed tests; record skipped tests and why. Use exact available regression class names from `rg --files app/src/androidTest core/data/src/androidTest`, not guessed class filters.

### CAL-Q03 — Manual visual, lifecycle and accessibility acceptance

- [ ] Execute every CQA scenario in [CALENDAR_QA.md](../../CALENDAR_QA.md) with the fixture and required locale/theme/window/API matrix. Automated coverage does not replace visual, TalkBack, IME or actual Back checks.
- [ ] Save light/dark screenshots aligned to the same data/date and record deliberate reference deviations, measured contrast and target bounds.
- [ ] Record defects with reproduction, expected/actual, severity, build/patch and evidence. Correct defects then rerun affected scenarios and mutation/navigation regressions within QA; if scope changes, revise the spec/plan first.
- [ ] Fill the acceptance record; mark PASS only against current-build evidence, BLOCKED/NOT RUN explicitly otherwise. Do not close unrelated release blockers.

**Release gate:** All P0/P1 Calendar scenarios pass with no unresolved data loss, revision conflict overwrite, broken entry/return, inaccessible action or stale/false data. Every matrix cell has evidence or explicit NOT RUN/BLOCKED (which leaves acceptance pending). Update the task ledger only after evidence review.

## Plan self-review

Spec CAL-01–07 maps to I01–I06 and all scenarios through the spec traceability table. Interfaces above are shared across tasks; Calendar writes retain existing mutation interfaces. The five review risks each have QA cases. No test/build/emulator step occurs in implementation. User-selected placement is Calendar in the third primary slot and Habits under More; Gregorian calendar, read-only habit previews and no new reminders/tags remain explicit design choices; no feature code was written by this planning pass.
