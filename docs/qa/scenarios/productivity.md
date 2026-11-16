# Productivity: Tasks, Habits and Focus

Status: **NOT RUN** for every case. This is an execution specification derived from the current source; no app QA or tests were run to create it.

## Execution contract

Use a disposable emulator/device and a productionDebug QA build (`com.luma.lifeos.qa`, minSdk 28, targetSdk 35) with a recorded commit/build ID. If the execution owner builds/tests, use `assembleProductionDebug`, `testProductionDebugUnitTest`, and `connectedProductionDebugAndroidTest`; this document does not assert those commands passed. Baseline B is English, Light, 24-hour time, Asia/Tehran, Gregorian display, notifications allowed, app lock off, quiet hours 22:00–08:00, “Show task and habit names on the lock screen” off, no imported data, no open Focus session/break, and no productivity records. Reference **D = Monday 2026-11-16, 12:00 local**. If setting the device date is unavailable, record the actual D and shift *every* relative fixture/date consistently; preserve weekday relationships by choosing the corresponding Monday. Do not compare a shifted run against literal 2026 dates. Notification tests use D at 12:00 with due time 12:02; record the actual trigger/post time because WorkManager delivery can be delayed.

Each case starts independently: restore B, then execute only the named fixture recipe and listed modifications. A case never inherits another case's output. Use a clean emulator snapshot/app-data reset between cases; do not clear data before capturing a failure. Date-travel setup must happen before creating reminders or starting Focus. Restore D before execution. Rotate/recreate means Activity recreation; process death means terminate only Luma's process on the disposable test device and relaunch without clearing its data. These are different checks.

**Counts:** count persisted entity IDs, including children and completed/archived objects. Never exceed 50 tasks, 50 habit definitions, 50 habit logs, 50 Focus sessions, 50 Focus runs, 50 tags, 50 schedule versions or 50 reminder/action records of any one type in an isolated case. The largest combined named fixture has 7 tasks, 3 habits, 3 logs, 2 sessions, 3 runs, 2 tags. Bounded 50-item cases replace that fixture completely. Do not run existing scale tests or generate a 51st record. Pagination beyond 50 cannot be exercised under this cap and is an explicit gap.

**Every action has its expected result after →.** Wait until saving/loading has settled before the next action; a spinner alone is not success. Evidence for every case: before/after screenshots (include the selected date/filter), build/device/API/language/theme/zone/D, entity IDs and counts from Android Studio App Inspection or the debug repository harness, actual versus expected, logs for any error, and final outcome. The baseline outcome remains NOT RUN until execution. Cleanup for **every** case is capture evidence, stop any open session, disable reminders, then restore B and the reference clock; do not delete evidence. Counts below are post-case expectations before cleanup.

## Navigation and precise fixture recipes

Visible English labels are primary manual targets; stable test tags in backticks disambiguate repeated controls in automation. Scroll to the named control when needed. Tasks/Habits/Focus are secondary routes: **More → Tasks / Habits / Focus**, not bottom tabs. Settings within Focus is `focus_settings_open`. Global reminders are **More → Settings → Reminders**. Today and Review refer to bottom destinations.

- **T-create(id, title, date, time, importance, note):** More → Tasks → Add task (`todo_list_add`); replace Title (`todo_editor_title`); enter Note only when specified; select importance chip (`todo_importance_high/medium/low/none`); for a date tap Change date (`todo_editor_change_due_date`), select the specified Gregorian date and Select date. Only after a date exists, Add time → input hour/minute → Select time. Tap Save; expect Tasks. Reopen by title and verify every field. An omitted optional field stays absent; do not add then infer inheritance.
- **T1:** title `T1 Paperwork`, D, 12:02, High, note `Bring passport`.
- **T2:** `T2 Groceries`, no date/time, Low, no note.
- **T3:** `T3 Submit form`, D−1, 09:00, Medium, no note.
- **T4:** `T4 Call dentist`, D+1, 10:30, None, no note.
- **T5:** `T5 Project`, D+2, no time, High, note `Parent note`.
- **T6/T7 children:** reopen saved T5 → Add subtask (`todo_editor_add_subtask`) → title `T6 Outline` / `T7 Review` → set T6 Low and T7 Medium → leave date/time/note absent → Save, returning to T5. Capture IDs. Children do not inherit parent fields. For a second root destination use T2.
- **T-set:** create T1–T7 in this order. Open tasks = 7; roots = 5; T5 progress = 0/2.

- **H-create(id, name, start, target, unit, weekdays):** More → Habits → Add habit (`habit_create`); enter Name, optional Unit and Target (`habit_name/unit/target`); Start date → choose date → Select date; select Daily/Weekdays or toggle day chips (`habit_weekday_1` is Monday through 7 Sunday) to the *exact* requested set; Save; reopen detail by the name/open area, not its separate Log control.
- **H1:** `H1 Water`, start D−2, target 2, unit `glasses`, Daily. Because new past starts are rejected, set clock to D−2 noon, create H1 then restore D noon. This captures tracking zone Asia/Tehran.
- **H2:** `H2 Walk`, start D, target 1, no unit, Tuesday/Thursday only.
- **H3:** `H3 Read`, start D+2, target 3, unit `pages`, Daily.
- **H-log(id,H,date):** More → Habits → selected-date control (`day_picker`) → specified date → Select date → open H detail → Log once and wait for count to settle. At a past date the occurrence time is absent. To create **HL1/HL2** log H1 on D twice; **HL3** log H1 on D−1 once. Record each actual log UUID, occurrence and recorded-at time. HL labels are test aliases, not app fields.
- **H-set:** create H1–H3; logs absent unless explicitly named. Definitions 3, initial schedule versions 3; H1 on D 0/2, H2 unscheduled, H3 future and unloggable.

- **F-start(id,name,mode,minutes,tag,task):** More → Focus → enter name (`focus_name`); Count up or Timed; for Timed choose preset 15/25/50 or Custom duration and enter minutes; expand tag disclosure and enter tag when specified; Choose task → search exact title → select it only when specified; if task selection fills the name, replace it with the required fixture name; tap Start once (`focus_start`). Record session/run UUIDs and start time.
- **F1:** `F1 Draft report`, Count up, tag `Work`, unlinked. Run at least 6 seconds, Pause, record displayed credit C1, wait 5 seconds, Resume, run at least 6 seconds, End and save. Record persisted millisecond credit C and two run IDs. UI input/wall-clock latency means C is measured, not assumed to be exactly 12 seconds.
- **F2:** `F2 Reading`, Timed 1 minute, tag `Study`, unlinked. Let target expire naturally; credit is exactly 60,000 ms and one run.
- **F-terminal:** F1 only, terminal, no reflection/break. **F-set:** F1 then F2, Done between them; 2 terminal sessions/3 runs/2 tags; saved total C+60,000 ms. Do not create extra sessions while inspecting setup/repeat.
- Deterministic integration alternatives use `FocusTimeSample`/test Clock and repository commands in an isolated instrumentation fixture, never raw invalid SQL or production changes. Exact timing oracle for F1: start at sample elapsed 0, pause 10,000, resume 15,000, stop 25,000 → C=20,000, two runs of 10,000. F2 start then sample 60,000 → 60,000. Keep actual wall instants on D and coherent elapsed/boot/process IDs. Manual and deterministic evidence must be labeled separately.

## Source and existing test map

Links below resolve from this document. Source wins over older roadmap documents. There is **no task recurrence editor/model in the current Tasks implementation**; do not add recurrence test steps based on old plans. Habit reminders expose one daily time in this UI; interval/multiple-time domain schedules require a harness and are not presented as UI features. Focus app shortcuts launch selected apps; they do not block all other apps or enforce Android-wide focus.

- Tasks: [editor UI](../../../app/src/main/java/com/luma/lifeos/ui/todo/TodoEditorScreen.kt), [editor ViewModel](../../../app/src/main/java/com/luma/lifeos/ui/todo/TodoEditorViewModel.kt), [list UI](../../../app/src/main/java/com/luma/lifeos/ui/todo/TodoListScreen.kt), [list ViewModel](../../../app/src/main/java/com/luma/lifeos/ui/todo/TodoListViewModel.kt), [family confirmations](../../../app/src/main/java/com/luma/lifeos/ui/todo/TodoFamilyActions.kt), [move picker](../../../app/src/main/java/com/luma/lifeos/ui/todo/TodoMovePickerSheet.kt), [rules](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoRules.kt), [models/limits](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoModels.kt), [text limits](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoInputs.kt), [Room repository](../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomTodoRepository.kt), [family commands](../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomTodoFamilyCommands.kt).
- Habits: [editor UI](../../../app/src/main/java/com/luma/lifeos/ui/habits/HabitEditorRoute.kt), [editor ViewModel](../../../app/src/main/java/com/luma/lifeos/ui/habits/HabitEditorViewModel.kt), [browse UI](../../../app/src/main/java/com/luma/lifeos/ui/habits/HabitsScreen.kt), [detail/reminder UI](../../../app/src/main/java/com/luma/lifeos/ui/habits/HabitDetailRoute.kt), [ViewModel](../../../app/src/main/java/com/luma/lifeos/ui/habits/HabitsViewModel.kt), [history](../../../app/src/main/java/com/luma/lifeos/ui/habits/HabitHistory.kt), [correction](../../../app/src/main/java/com/luma/lifeos/ui/habits/HabitLogCorrectionDialog.kt), [rules](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/habit/HabitRules.kt), [limits](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/habit/HabitInputs.kt), [Room repository](../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomHabitRepository.kt).
- Reminders: [global settings](../../../app/src/main/java/com/luma/lifeos/ui/settings/ReminderSettingsRoute.kt), [preferences/default quiet hours](../../../app/src/main/java/com/luma/lifeos/reminders/ReminderPreferences.kt), [work reconciliation](../../../app/src/main/java/com/luma/lifeos/reminders/ReminderWork.kt), [notification/actions](../../../app/src/main/java/com/luma/lifeos/reminders/ReminderPlatform.kt), [task planner](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TaskReminderPlanner.kt), [habit planner](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/habit/HabitReminderPlanner.kt).
- Focus: [setup UI](../../../app/src/main/java/com/luma/lifeos/ui/focus/FocusSetupContent.kt), [setup reducer](../../../app/src/main/java/com/luma/lifeos/ui/focus/FocusSetupState.kt), [screen/runtime controls](../../../app/src/main/java/com/luma/lifeos/ui/focus/FocusScreen.kt), [ViewModel](../../../app/src/main/java/com/luma/lifeos/focus/FocusViewModel.kt), [runtime](../../../app/src/main/java/com/luma/lifeos/focus/FocusRuntime.kt), [completion/break UI](../../../app/src/main/java/com/luma/lifeos/ui/focus/FocusCompletionContent.kt), [history](../../../app/src/main/java/com/luma/lifeos/ui/focus/FocusHistoryRoute.kt), [reflection](../../../app/src/main/java/com/luma/lifeos/ui/focus/FocusReflectionEditor.kt), [metadata editor](../../../app/src/main/java/com/luma/lifeos/ui/focus/FocusSessionMetadataEditor.kt), [tags](../../../app/src/main/java/com/luma/lifeos/ui/focus/FocusTagManager.kt), [goal settings](../../../app/src/main/java/com/luma/lifeos/ui/focus/FocusGoalSettings.kt), [invitations](../../../app/src/main/java/com/luma/lifeos/ui/focus/FocusInvitationSettings.kt), [Room sessions](../../../core/data/src/main/kotlin/com/luma/lifeos/data/focus/RoomFocusRepository.kt), [Room products](../../../core/data/src/main/kotlin/com/luma/lifeos/data/focus/RoomFocusProductRepository.kt).
- Routing: [LifeOsApp](../../../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt), [route constants](../../../app/src/main/java/com/luma/lifeos/ui/navigation/AppDestinations.kt). Design review reference: [current redesign contract](../../luma-redesign/README.md), [route coverage](../../luma-redesign/route-coverage.md), [consistency checklist](../../luma-redesign/ui-consistency-checklist.md).

Existing coverage references (reference only; not executed for this document):

- **TT:** [TodoEditorFlowTest](../../../app/src/androidTest/java/com/luma/lifeos/todo/TodoEditorFlowTest.kt) covers picker cancel, time defaults/bounds, dirty close/status, delete, importance and save-and-continue; [TodoHierarchyFlowTest](../../../app/src/androidTest/java/com/luma/lifeos/todo/TodoHierarchyFlowTest.kt) covers parent/child navigation; [TodoFamilyCommandsTest](../../../core/data/src/androidTest/kotlin/com/luma/lifeos/data/TodoFamilyCommandsTest.kt) covers family completion/undo, stale plans, reopen and sibling moves; [TodoEditorViewModelTest](../../../app/src/test/java/com/luma/lifeos/ui/todo/TodoEditorViewModelTest.kt), [TodoListViewModelTest](../../../app/src/test/java/com/luma/lifeos/ui/todo/TodoListViewModelTest.kt), [TodayTasksFlowTest](../../../app/src/androidTest/java/com/luma/lifeos/todo/TodayTasksFlowTest.kt).
- **HT:** [HabitCaptureFlowTest](../../../app/src/androidTest/java/com/luma/lifeos/habits/HabitCaptureFlowTest.kt) covers log/undo/correction, pending schedules, archive, confirmation and error UI; [HabitDateSelectionFlowTest](../../../app/src/androidTest/java/com/luma/lifeos/habits/HabitDateSelectionFlowTest.kt) covers stale selection and observation failures; [HabitEditorViewModelTest](../../../app/src/test/java/com/luma/lifeos/ui/habits/HabitEditorViewModelTest.kt), [HabitsViewModelTest](../../../app/src/test/java/com/luma/lifeos/ui/habits/HabitsViewModelTest.kt), [HabitPersistenceTest](../../../core/data/src/androidTest/kotlin/com/luma/lifeos/data/HabitPersistenceTest.kt), [HabitTimelineFlowTest](../../../app/src/androidTest/java/com/luma/lifeos/habits/HabitTimelineFlowTest.kt), [HabitReviewFlowTest](../../../app/src/androidTest/java/com/luma/lifeos/habits/HabitReviewFlowTest.kt).
- **FT:** [FocusSetupContentTest](../../../app/src/androidTest/java/com/luma/lifeos/ui/focus/FocusSetupContentTest.kt), [FocusLifecycleTest](../../../app/src/androidTest/java/com/luma/lifeos/ui/focus/FocusLifecycleTest.kt), [FocusReportsTest](../../../app/src/androidTest/java/com/luma/lifeos/ui/focus/FocusReportsTest.kt) (reflection navigation/save failure, separate task/unlink retries, repeat and history deletion), [FocusRuntimeRecoveryTest](../../../app/src/androidTest/java/com/luma/lifeos/focus/FocusRuntimeRecoveryTest.kt), [FocusAllowedAppsTest](../../../app/src/androidTest/java/com/luma/lifeos/ui/focus/FocusAllowedAppsTest.kt), [FocusPersistenceTest](../../../core/data/src/androidTest/kotlin/com/luma/lifeos/data/focus/FocusPersistenceTest.kt), [FocusTimingTest](../../../core/domain/src/test/kotlin/com/luma/lifeos/domain/focus/FocusTimingTest.kt), [FocusInsightsTest](../../../core/domain/src/test/kotlin/com/luma/lifeos/domain/focus/FocusInsightsTest.kt).

## Failure injection contract

Cases labeled **integration/harness** cannot be forced reliably with airplane mode: these features store local data. Use a disposable in-memory or named test Room database and the constructor-injected repositories/Clock/SavedStateHandle already used by TT/HT/FT. Wrap the relevant repository with a delegate and a one-shot gate: (a) throw/return Unavailable before commit, (b) delegate the mutation then throw/return Unavailable once to simulate lost acknowledgement, (c) suspend until the test releases it, or (d) apply a second client's legitimate update to make a revision/plan stale. Record which operation and which stage was intercepted, then remove the gate for Retry. Do not add a hidden menu, edit production logic, corrupt the database or claim these are manual UI cases. Missing harness setup is BLOCKED with the precise dependency named, not a pass. Existing tests are examples; additional scenario-specific harness assertions remain work for the execution owner.

## Case inventory

67 independent cases: Tasks 22, Habits 18, Focus 27. Priority totals: P0 34, P1 32, P2 1. “Integration/harness” cases and sub-variants are **BLOCKED for execution until the named injected fixture exists**; their test-result status remains NOT RUN, not an implied pass.

| ID | Priority | Level | Status |
|---|---|---|---|
| [TASK-001](#task-001) | P0 | UI | NOT RUN |
| [TASK-002](#task-002) | P1 | UI + integration for line-break input | NOT RUN |
| [TASK-003](#task-003) | P1 | UI | NOT RUN |
| [TASK-004](#task-004) | P1 | UI | NOT RUN |
| [TASK-005](#task-005) | P0 | UI/lifecycle | NOT RUN |
| [TASK-006](#task-006) | P1 | UI | NOT RUN |
| [TASK-007](#task-007) | P0 | UI | NOT RUN |
| [TASK-008](#task-008) | P0 | UI | NOT RUN |
| [TASK-009](#task-009) | P0 | UI | NOT RUN |
| [TASK-010](#task-010) | P1 | UI | NOT RUN |
| [TASK-011](#task-011) | P0 | UI | NOT RUN |
| [TASK-012](#task-012) | P0 | UI | NOT RUN |
| [TASK-013](#task-013) | P1 | UI | NOT RUN |
| [TASK-014](#task-014) | P0 | UI | NOT RUN |
| [TASK-015](#task-015) | P1 | UI/crossscreen | NOT RUN |
| [TASK-016](#task-016) | P0 | Integration/harness | NOT RUN |
| [TASK-017](#task-017) | P0 | Integration/harness | NOT RUN |
| [TASK-018](#task-018) | P0 | Integration/harness | NOT RUN |
| [TASK-019](#task-019) | P0 | Device/UI + notification | NOT RUN |
| [TASK-020](#task-020) | P1 | Device/UI + integration for replay | NOT RUN |
| [TASK-021](#task-021) | P1 | Device/UI | NOT RUN |
| [TASK-022](#task-022) | P2 | UI + bounded fixture | NOT RUN |
| [HAB-001](#hab-001) | P0 | UI | NOT RUN |
| [HAB-002](#hab-002) | P1 | UI | NOT RUN |
| [HAB-003](#hab-003) | P1 | UI | NOT RUN |
| [HAB-004](#hab-004) | P0 | UI/lifecycle | NOT RUN |
| [HAB-005](#hab-005) | P0 | UI | NOT RUN |
| [HAB-006](#hab-006) | P0 | UI | NOT RUN |
| [HAB-007](#hab-007) | P0 | UI | NOT RUN |
| [HAB-008](#hab-008) | P1 | UI | NOT RUN |
| [HAB-009](#hab-009) | P0 | UI | NOT RUN |
| [HAB-010](#hab-010) | P1 | UI | NOT RUN |
| [HAB-011](#hab-011) | P0 | UI/date boundary | NOT RUN |
| [HAB-012](#hab-012) | P1 | UI/date boundary | NOT RUN |
| [HAB-013](#hab-013) | P1 | UI/crossscreen | NOT RUN |
| [HAB-014](#hab-014) | P1 | UI/crossscreen | NOT RUN |
| [HAB-015](#hab-015) | P0 | Device/UI + notification | NOT RUN |
| [HAB-016](#hab-016) | P1 | Device/UI + planner harness | NOT RUN |
| [HAB-017](#hab-017) | P0 | Integration/harness | NOT RUN |
| [HAB-018](#hab-018) | P1 | Integration/harness + UI | NOT RUN |
| [FOC-001](#foc-001) | P0 | UI | NOT RUN |
| [FOC-002](#foc-002) | P1 | UI + integration for raw control characters | NOT RUN |
| [FOC-003](#foc-003) | P0 | UI + deterministic integration oracle | NOT RUN |
| [FOC-004](#foc-004) | P0 | UI | NOT RUN |
| [FOC-005](#foc-005) | P0 | UI/lifecycle | NOT RUN |
| [FOC-006](#foc-006) | P0 | Device/UI + deterministic integration | NOT RUN |
| [FOC-007](#foc-007) | P0 | Integration/harness + device lifecycle | NOT RUN |
| [FOC-008](#foc-008) | P0 | Integration/harness | NOT RUN |
| [FOC-009](#foc-009) | P1 | UI/device lifecycle | NOT RUN |
| [FOC-010](#foc-010) | P1 | UI | NOT RUN |
| [FOC-011](#foc-011) | P1 | UI | NOT RUN |
| [FOC-012](#foc-012) | P0 | UI/lifecycle | NOT RUN |
| [FOC-013](#foc-013) | P1 | UI | NOT RUN |
| [FOC-014](#foc-014) | P0 | UI/crossscreen | NOT RUN |
| [FOC-015](#foc-015) | P0 | UI/crossscreen | NOT RUN |
| [FOC-016](#foc-016) | P0 | Integration/harness + UI | NOT RUN |
| [FOC-017](#foc-017) | P0 | UI | NOT RUN |
| [FOC-018](#foc-018) | P1 | UI | NOT RUN |
| [FOC-019](#foc-019) | P1 | UI/date boundary | NOT RUN |
| [FOC-020](#foc-020) | P1 | Integration/harness + UI | NOT RUN |
| [FOC-021](#foc-021) | P1 | Device/UI + notification | NOT RUN |
| [FOC-022](#foc-022) | P1 | Integration/harness + UI | NOT RUN |
| [FOC-023](#foc-023) | P1 | Device/UI + platform harness | NOT RUN |
| [FOC-024](#foc-024) | P1 | UI/crossscreen + deterministic date fixture | NOT RUN |
| [FOC-025](#foc-025) | P1 | Integration/harness | NOT RUN |
| [FOC-026](#foc-026) | P0 | Integration/harness | NOT RUN |
| [FOC-027](#foc-027) | P1 | Integration/harness + UI matrix | NOT RUN |

<a id="task-001"></a>

### TASK-001 — Empty list and title-only creation

**Priority:** P0 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; no fixtures.

1. Open More → Tasks, then Completed → Both tabs show their distinct empty states and zero counts; Add task remains reachable.
2. Return Open → Add task; enter `  T2 Groceries  ` only; Save once → Returns to list with one open row.
3. Reopen row, then close and relaunch app → Title is trimmed to `T2 Groceries`; date/time/note absent; importance None; row persists.

**Persisted oracle/count:** 1 task, 1 open, 0 completed; no duplicate on relaunch.

**Coverage/evidence:** TT: titleOnlyTaskCanSaveWithoutOptionalFields; Room normalization. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="task-002"></a>

### TASK-002 — Validation and text boundaries

**Priority:** P1 · **Level:** UI + integration for line-break input · **Status:** NOT RUN

**Fixture/setup:** B; create no existing task. Use clipboard strings `A` repeated 200/201, `N` repeated 4000/4001; emoji count is Unicode code points..

1. Open Add task; leave title blank, then spaces; Save for each → Required error, editor stays, zero tasks.
2. Enter a 201-character title; Save → Title length error, no write. Replace with 200 `A`s → Error clears after correction.
3. Enter 4001 `N`s in note; Save → Note error; replace with 4000 and Save → One task saves at exact limits.
4. In an isolated harness invoke title change/save with `Line 1\nLine 2` and separately U+2028 → Single-line validation rejects; persisted valid record unchanged. Repeat 200-code-point emoji title in a fresh baseline → Valid text saves without UTF-16 miscount.

**Persisted oracle/count:** Per isolated sub-run ≤1 task. Accepted title/note lengths 200/4000 code points; rejected inputs create 0 records. Capture visible and semantic field errors.

**Coverage/evidence:** TT + TodoRulesTest; clipboard/IME may sanitize line breaks, so raw control-character validation requires harness. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="task-003"></a>

### TASK-003 — Date/time picker cancel and default

**Priority:** P1 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; T2.

1. Open T2; Change date → choose D+1 → Cancel → Date stays absent and close has no dirty prompt.
2. Change date → D → Select date → Date shows D; Add time becomes available.
3. Add time; change displayed hour/minute then Cancel → Time remains absent.
4. Add time again; confirm untouched default → Time becomes 09:00; Save; reopen → D/09:00 retained.

**Persisted oracle/count:** 1 task; no writes for canceled picker changes; one update after Save.

**Coverage/evidence:** TT: pickerCancelDoesNothingAndConfirmingItsCurrentDateKeepsDraftClean; dueTimePickerCancelKeepsDraftAndConfirmationUsesDefaultNine. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="task-004"></a>

### TASK-004 — Minute boundaries and removing a date

**Priority:** P1 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; T1.

1. Open T1; Change time → 00:00 → Select time → Save → Reopen shows midnight for D.
2. Change time → 23:59 → Select time → Save → Minute boundary retained.
3. Remove time → Save → D remains and time absent.
4. Re-add 12:02; Remove date → Both date and time clear immediately; Save/reopen → Neither field reappears.

**Persisted oracle/count:** 1 task, dueDate=null and dueTime=null at end; no orphan reminder slot.

**Coverage/evidence:** TT: optionalDueDateCanBeCleared; removingDueDateClearsTimeAndInvalidationClosesPicker; dueTimeRowFormatsMinuteBoundariesUsingSystemPreference. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="task-005"></a>

### TASK-005 — Draft discard, keep editing and recreation

**Priority:** P0 · **Level:** UI/lifecycle · **Status:** NOT RUN

**Fixture/setup:** B; T2.

1. Open T2; change title to `T2 Draft`, note to `Unsaved`, importance High; rotate/recreate Activity → All unsaved fields retained, no database update.
2. Back → Discard prompt; Keep editing → Same draft remains.
3. Back → Discard → Returns to list; reopen T2 → Original title/Low/no note retained.
4. Make same changes; Save; relaunch → Exactly those edits persist once.

**Persisted oracle/count:** 1 task throughout; baseline unchanged until final Save.

**Coverage/evidence:** TT dirtyCloseCanKeepEditingOrConfirmDiscard; SavedStateHandle in editor ViewModel. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="task-006"></a>

### TASK-006 — Importance, grouping, sort and search

**Priority:** P1 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; T-set.

1. Open Tasks/Open with All importance and Date sort → T3 Overdue, T1 Today, T4/T5 Upcoming and T2 No date; children are reachable once through family context.
2. Select High filter → Matching roots/rows are T1 and T5, child importance is independent. Select Low → T2 and T6 are matches with parent context for T6.
3. Clear filter; select Importance sort → High before Medium before Low before None; changing sort does not change saved importance.
4. Search `Outline` → Only T6 match with T5 context; search `Bring passport` → No title match; clear search/filter → Original open count 7 returns.

**Persisted oracle/count:** 7 tasks; search/filter/sort perform no writes. Capture IDs in visible matches and family context to reject duplicates.

**Coverage/evidence:** List UI/VM titleSearch and importance filters; TT list tests. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="task-007"></a>

### TASK-007 — Complete, undo and reopen a single task

**Priority:** P0 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; T2.

1. Tap T2 completion control in Open list → Row leaves Open; completion feedback/Undo appears; Completed contains one T2.
2. Tap Undo before dismissing feedback → T2 returns Open, Completed zero.
3. Complete again; open Completed → T2 → Reopen → Returns Open after successful write.
4. Relaunch; inspect status → One original ID remains open.

**Persisted oracle/count:** 1 task, completedAt absent at end; complete/undo/reopen do not create a replacement task.

**Coverage/evidence:** TT list/Today tests; repository status receipts. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="task-008"></a>

### TASK-008 — Dirty status action and single deletion confirmation

**Priority:** P0 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; T2.

1. Open T2; change note to `Draft` → Mark complete is disabled with Save first explanation.
2. Tap Delete → Confirmation appears; Cancel → Draft stays; task still exists.
3. Save draft; reopen; Delete → Confirm → Returns list; T2 disappears from Open and Completed.
4. Relaunch and search `T2` → Empty result.

**Persisted oracle/count:** 0 tasks after confirmed delete; cancel changed neither membership nor draft.

**Coverage/evidence:** TT dirtyStatusActionIsDisabledWithSaveFirstExplanation; deleteCancelRetainsEditorAndConfirmedDeleteCallsOnce. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="task-009"></a>

### TASK-009 — Unsaved parent save-and-continue

**Priority:** P0 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; no tasks.

1. Add task; title `T5 Project`, High, date D+2; Add subtask → Save and continue confirmation.
2. Cancel → Parent draft retained; zero persisted tasks.
3. Add subtask → Save and continue → Parent commits once; new child editor has T5 parent context and absent date/time/note.
4. Enter `T6 Outline`, Low; Save → Returns T5; 0/1 progress; Open parent from child returns correct T5.

**Persisted oracle/count:** 2 tasks; T6.parentId=T5; no inherited due date/importance/note and no second root T5.

**Coverage/evidence:** TT unsavedParentRequiresSaveAndContinueConfirmation; TodoHierarchyFlowTest. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="task-010"></a>

### TASK-010 — Child ordering and move between roots

**Priority:** P1 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; T2,T5,T6,T7.

1. Open T5 → Move T7 up → Order becomes T7,T6; first-row Move up and last-row Move down cannot move beyond edges.
2. Open T6 → Move task → select T2 → Cancel → T6 remains under T5.
3. Move task → T2 → Confirm → T6 parent context changes to T2; T5 progress 0/1 and T2 0/1.
4. Move T6 to a standalone task (`todo_move_to_root`) and Confirm → Parent context clears; T6 fields/ID unchanged; relaunch preserves order/membership.

**Persisted oracle/count:** 4 task IDs throughout, roots increase from 2 to 3 after promotion; T7 remains T5 child; no cycles or duplicate membership.

**Coverage/evidence:** Move picker/editor VM; TT family siblingMovesPreserveStableIdsAndDoNotRotateFamilyAtAnEdge. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="task-011"></a>

### TASK-011 — Family completion preview, cancellation and undo

**Priority:** P0 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; T5,T6,T7; complete T6 alone first (T5 stays open).

1. Open T5; Mark complete → Dialog names open-child count 1; Cancel → T5/T7 open and T6 completed.
2. Mark complete → Confirm → T5/T7 completed atomically; progress 2/2.
3. From resulting feedback tap Undo → T5/T7 reopen, T6 remains previously completed.
4. Relaunch; inspect Open/Completed and T5 progress → Same three IDs and 1/2 progress.

**Persisted oracle/count:** 3 tasks, 2 open/1 completed at end. Undo restores only tasks changed by this family command.

**Coverage/evidence:** TT familyCompletionAndUndoPreservePreviouslyCompletedChildren; hierarchy UI. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="task-012"></a>

### TASK-012 — Reopen child under completed parent

**Priority:** P0 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; T5,T6,T7; complete T5 family via confirmation.

1. Completed → T5 → open T6 → Reopen → Confirmation explains parent reopening.
2. Cancel → Both remain completed.
3. Reopen again → Confirm → T6 and T5 reopen together; T7 remains completed.
4. Open T5 from child context → Progress 1/2 and family remains readable.

**Persisted oracle/count:** 3 tasks; T5/T6 open, T7 completed; no open child under a completed parent.

**Coverage/evidence:** TT reopeningChildUnderCompletedParentReopensBothAtomically. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="task-013"></a>

### TASK-013 — All children done offers explicit parent completion

**Priority:** P1 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; T5,T6,T7.

1. Open Tasks; expand T5 children; complete T6 then T7 individually → Parent remains open; all-subtasks-complete offer appears.
2. Choose Not now → Offer dismisses; T5 still open with 2/2.
3. Open T5; Mark complete → Confirm if shown → Parent completes without changing child IDs.
4. Completed → expand/open T5 → All three completed and correct counts.

**Persisted oracle/count:** 3 tasks, 3 completed; child completion alone never silently completes parent.

**Coverage/evidence:** TodoListViewModel.suggestParentCompletion and parent offer UI. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="task-014"></a>

### TASK-014 — Family deletion scope

**Priority:** P0 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; T2,T5,T6,T7.

1. Open T5 → Delete → Dialog shows T5 and child count 2; Cancel → All four remain.
2. Delete → Confirm → Parent and both children disappear together.
3. Open T2 and search T6/T7 across both tabs → T2 remains intact; no orphan results.
4. Relaunch → Deletion remains durable.

**Persisted oracle/count:** 1 task (T2), 0 T5/T6/T7 IDs; family deletion does not remove unrelated root.

**Coverage/evidence:** TT familyDeleteRequiresPreviewAndRemovesOnlyTheConfirmedFamily. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="task-015"></a>

### TASK-015 — Today date context and task editor round-trip

**Priority:** P1 · **Level:** UI/crossscreen · **Status:** NOT RUN

**Fixture/setup:** B; T1,T2,T3,T4.

1. Today at D → Inspect Tasks card → Due/overdue eligible tasks are presented with their dates; future T4 is not presented as due D.
2. Open T1 from card → Exact T1 editor opens; change importance Medium, Save → Today/list reflects saved importance.
3. Open full Tasks from Today card → Same task IDs; complete T1 there → Returning Today removes its open row and updates count.
4. Undo via available task feedback → Today/Open list agree again.

**Persisted oracle/count:** 4 tasks, all open after undo; cross-screen status and selected date agree. If Today tasks section is disabled in preferences, enable it before setup and record that setting.

**Coverage/evidence:** TodayTasksState/ViewModel, TT TodayTasksFlowTest/TodayTasksSemanticsTest. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="task-016"></a>

### TASK-016 — Save failure and lost acknowledgement retry

**Priority:** P0 · **Level:** Integration/harness · **Status:** NOT RUN

**Fixture/setup:** B; new T2 input; inject create failure before commit, then separately commit-and-throw with same capture ID.

1. Open new editor, fill T2, Save with pre-commit failure → Save error/Retry, editor retains fields, count zero.
2. Remove gate → Retry → Returns list, exactly one T2.
3. Reset B; repeat with commit-and-throw → Error may remain but database already has one T2; rotate/recreate while error visible → Captured ID/pending intent retained.
4. Remove gate → Retry/reconcile → One existing T2 is accepted; no duplicate or changed payload.

**Persisted oracle/count:** Each isolated variant ends with exactly 1 task; retain captured create ID/revision and invocation log as evidence.

**Coverage/evidence:** TT editor ViewModel tests; scenario-specific lost-ack harness needed; airplane mode is not an injection. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="task-017"></a>

### TASK-017 — Stale editor and missing task recovery

**Priority:** P0 · **Level:** Integration/harness · **Status:** NOT RUN

**Fixture/setup:** B; T2; second repository client available.

1. Open T2, edit title `T2 Local draft`; second client updates note to `Remote` with valid revision → Save local draft fails with conflict; draft retained.
2. Reload latest → Confirmation because dirty; Keep editing → Local draft still visible.
3. Reload latest → Confirm → Latest persisted T2 title/note loaded; no silent overwrite.
4. In a fresh B+T2 editor, second client deletes T2; attempt Save → Missing feedback; no replacement record created; close returns usable list.

**Persisted oracle/count:** Conflict sub-run 1 task with remote note; missing sub-run 0 tasks. Capture old/new revisions.

**Coverage/evidence:** TT editor load/conflict/reload tests; production editor never recreates a missing edit target. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="task-018"></a>

### TASK-018 — Stale family plan and move error recovery

**Priority:** P0 · **Level:** Integration/harness · **Status:** NOT RUN

**Fixture/setup:** B; T2,T5,T6,T7; delegate repositories and one-shot operation gates.

1. Request T5 complete; after preview, second client changes T7's valid membership/fields → Confirm → Stale family recovery; no partial completion.
2. Choose Review again → Latest preview shown; Cancel → All baseline statuses remain.
3. Open T6 move picker; fail move before commit → Error retains selected target and explicit Retry; remove gate and Retry → One move to T2.
4. Repeat on separate baseline with move committed then lost acknowledgement → Retry resolves same operation once; T6 belongs to only one parent.

**Persisted oracle/count:** 4 tasks, no partial family mutation; completed variant counts remain baseline; move variant has T6 under T2 once.

**Coverage/evidence:** TT family staleCompletionPreviewCannotCompleteAChildAddedAfterConfirmation; move/recovery VM requires targeted harness. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="task-019"></a>

### TASK-019 — Automatic task reminder and notification completion

**Priority:** P0 · **Level:** Device/UI + notification · **Status:** NOT RUN

**Fixture/setup:** B; create T1 at D noon; notification channel allowed.

1. Reopen T1 → Automatic reminder description explains dated timed task behavior; no invented manual recurrence switch.
2. Leave app foreground/background until 12:02 slot is delivered → One task reminder notification appears with generic title while lock-screen-name previews are off; record actual lateness. Enable “Show task and habit names on the lock screen” before setup in a separate variant to expect T1's title.
3. Tap notification Complete → T1 becomes completed; reopening notification/action cannot complete another record.
4. Open Tasks/Completed and Today → Same T1 completed; no further reminder for the completed task.

**Persisted oracle/count:** 1 completed task, one delivered logical slot/action; notification may be delayed by platform, but duplicate delivery/action is a failure. Harness can call worker for due slot at 12:02 if OS delivery is blocked, labeled integration evidence.

**Coverage/evidence:** TaskReminderPlanner; RoomTaskReminderRepository; ReminderWork/Platform. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="task-020"></a>

### TASK-020 — Snooze, rescheduling and stale notification

**Priority:** P1 · **Level:** Device/UI + integration for replay · **Status:** NOT RUN

**Fixture/setup:** B; T1; obtain its 12:02 notification.

1. Tap Snooze → No task completion; reminder scheduled ten minutes after action, within D and quiet-hour eligibility.
2. Before snoozed delivery edit T1 due time to 12:30; Save → Old slot invalidated.
3. Use captured original notification Open → Correct editor or stale-safe handling; replay old Complete action in harness → Cannot mutate revised T1.
4. Advance coherently/wait to 12:30 → Current eligible reminder can deliver; T1 remains open until explicit completion.

**Persisted oracle/count:** 1 open task; only current task/config revision is actionable; one active future schedule for current due time.

**Coverage/evidence:** TaskReminderPlanner.snoozeTaskReminder/taskReminderActionIsCurrent; replay needs captured PendingIntent/command harness, not forged production action. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="task-021"></a>

### TASK-021 — Reminder permission, quiet hours and privacy

**Priority:** P1 · **Level:** Device/UI · **Status:** NOT RUN

**Fixture/setup:** B; T1.

1. Global Settings → Reminders → revoke notifications in Android settings and return → Status is blocked; T1 remains saved.
2. Grant through available notification access action; set quiet hours 12:00–13:00 → At 12:02 no task notification; task remains open; quiet status is distinguishable from save failure.
3. Disable quiet hours; move T1 time to current local time+2 minutes; enable “Show task and habit names on the lock screen” → Eligible private notification can show T1 title when access is allowed; public redacted version remains generic and note is never shown. Turn option off and repeat on a fresh due slot → Generic title.
4. Remove T1 date or delete before due → Pending notification canceled/rejected; no notification action mutates a removed task.

**Persisted oracle/count:** 0 tasks after delete variant, or 1 date-less open task after date-removal variant; preferences survive returning/relaunch.

**Coverage/evidence:** ReminderPreferences/global settings; task planner suppresses quiet slots rather than promising delivery exactly at quiet-end. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="task-022"></a>

### TASK-022 — Bounded 50-task list and accessible layout

**Priority:** P2 · **Level:** UI + bounded fixture · **Status:** NOT RUN

**Fixture/setup:** B replaces all fixtures. Create exactly 50 title-only tasks using T-create: `T01 Item`…`T50 Item`, no dates/time/note, alternating High/None. UI creation is authoritative; an equivalent 50-record debug repository fixture may speed setup if marked..

1. Open Tasks/Open; scroll first to last → Count 50, each ID appears once, final item reachable; no Load more requiring a 51st item.
2. Filter High → Count 25; search `T50 Item` with All → Exactly one; clear query → 50 again.
3. Repeat view/editor at 200% font, Dark then System, French then Persian/RTL → Controls readable/reachable, shared warm-paper/obsidian roles, no clipped counts/actions; minimum 48 dp targets and keyboard/insets clearance.
4. Restore EN Light; open T50, cancel unchanged → Count still 50.

**Persisted oracle/count:** 50 tasks maximum, no new records during variants; no stress/scale test. Evidence includes EN/FR/FA screenshots and semantics.

**Coverage/evidence:** TT accessibility/language tests; native 50-item page boundary only. Multi-page task list (>50) intentionally untested. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="hab-001"></a>

### HAB-001 — Empty habits and simple capture

**Priority:** P0 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; no habits.

1. More → Habits → Empty-state Add habit → New habit editor with target 1, all days selected, start D.
2. Enter name `H1 Water`, unit `glasses`, target 2; Save → One scheduled habit on D.
3. Open by habit name/open area → Detail shows 0/2, Log, history and collapsed management; distinct Log target is not triggered by opening.
4. Relaunch → Same habit and schedule remain.

**Persisted oracle/count:** 1 habit, 1 initial schedule, 0 logs; captured tracking zone Asia/Tehran.

**Coverage/evidence:** HT createRepeatedlyLogUndoOneAndCorrectAnIndividualHistoryItem; separate open/log semantics. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="hab-002"></a>

### HAB-002 — Name, unit, target and weekday validation

**Priority:** P1 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; no habits. Use `A`×100/101 and `U`×30/31..

1. Open Add habit; Save blank/spaces name → Required error, no write.
2. Set 100-character name and 30-character unit → Accepted; attempt a 101st/31st character → Rejected value/error is associated with relevant field; valid previous value retained.
3. Try target blank and 0 → Save rejects; attempt 1000 or a letter → Input rejected; set 999 → Valid.
4. Deselect all seven weekdays → Choose weekday error, Save writes nothing; reselect Monday → Save succeeds at exact bounds.

**Persisted oracle/count:** 1 habit, 1 schedule, target 999/Monday; zero logs. No need to log 999 times.

**Coverage/evidence:** HT editor validation; HabitTextLimits uses code points for name/unit, 1..999 target. Capture actual rejected-input retention rather than expecting truncation. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="hab-003"></a>

### HAB-003 — Start date boundary, picker cancel and future start

**Priority:** P1 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; no habits.

1. New habit named H3 Read; open start picker, choose D+2 then Cancel → Start remains D.
2. Select D−1 and confirm; Save → Past-start validation, editor stays.
3. Select D+2; target 3, unit pages; Save → Habit exists but D Log disabled and future-start explanation displayed.
4. Select D+2 in Habits → Future date remains unloggable until clock reaches that date; edit existing H3 → Start control is read-only.

**Persisted oracle/count:** 1 habit, 1 schedule effective D+2, 0 logs; no historical rewrite.

**Coverage/evidence:** HabitEditorViewModel START_IN_PAST; detail future log eligibility. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="hab-004"></a>

### HAB-004 — Editor draft, dirty dismissal and rotation

**Priority:** P0 · **Level:** UI/lifecycle · **Status:** NOT RUN

**Fixture/setup:** B; H1.

1. Open H1 → Edit; rename `H1 Draft`, unit `cups`, target 3, Monday only → Rotate → Draft and effective-date message survive.
2. Back → Keep editing → All draft values retained.
3. Back → Discard → Original name/unit and today's 2/Daily schedule retained.
4. Reopen Edit; make same changes; Save → Rename/unit apply, schedule shown pending for D+1; relaunch preserves saved state.

**Persisted oracle/count:** 1 habit, 2 schedule versions after Save, 0 logs; no version created by discard.

**Coverage/evidence:** HT editor SavedStateHandle and discard behavior. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="hab-005"></a>

### HAB-005 — Repeated logs, target and undo exact repetition

**Priority:** P0 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; H1.

1. Habits at D → Tap H1 Log once, wait → Count 1/2 partial; one new history record.
2. Log again → Count 2/2 reached; log again → Count 3/2 retained (target is a goal, not a hard count cap).
3. Tap Undo on latest repetition → Count 2/2; only third log removed.
4. Relaunch and open history → Exactly the first two IDs remain with occurrence times on D.

**Persisted oracle/count:** 1 habit, 2 logs after Undo; no consolidation into one count row.

**Coverage/evidence:** HT repeat-log/undo; repository log eligibility and individual IDs. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="hab-006"></a>

### HAB-006 — Past-date logging and date selection lifecycle

**Priority:** P0 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; H1 (start D−2), no logs.

1. Select D−1 in Habits; open H1; Log → D−1 count 1; history displays Time not recorded (backfill has no fabricated occurrence).
2. Return Habits; select D → Count 0; rotate → Selected date D retained; choose D−1 → Count 1.
3. Select D+1 → Log disabled with future-date reason.
4. Use Today action/date reset → Returns D; opening same habit preserves selected date context.

**Persisted oracle/count:** 1 habit, 1 log dated D−1 with occurredAt=null, recordedAt on D; no future log.

**Coverage/evidence:** HT HabitDateSelectionFlowTest; HabitTime/rules. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="hab-007"></a>

### HAB-007 — Correct a particular log and retain its identity

**Priority:** P0 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; H1 + HL1/HL2 on D.

1. Open history; select Edit for captured HL1 ID → Correction opens for HL1 only.
2. Choose date D−1, Clear time → Save → HL1 moves to D−1 and time absent; HL2 remains on D.
3. Reopen HL1 correction; date D, time 11:30 → Select time → Save → Both logs on D; HL1 time 11:30 in tracking zone.
4. Relaunch → Original HL1 ID and recordedAt retained; updatedAt changes; count D=2, D−1=0.

**Persisted oracle/count:** 1 habit, exactly 2 log IDs; correction moves a record, never creates a third.

**Coverage/evidence:** HT historyCorrectionMovesLogToAnotherDateWithoutChangingItsIdentity; correction UI/repository. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="hab-008"></a>

### HAB-008 — Correction validation and layered cancellation

**Priority:** P1 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; H1 + HL1 at D; reference time noon.

1. Edit HL1 → Open date picker → choose D+1 → Cancel picker → Correction remains unchanged; Back/cancel untouched correction → Closes without discard prompt.
2. Edit again; set D+1 and Save → Date error on date control; no write.
3. Correct date D and time 23:59; Save → Future occurrence-time error on time control, original log unchanged.
4. Set 11:30; Back → Keep editing → Draft retained; Cancel → Discard → Original date/time retained and no nested picker left open.

**Persisted oracle/count:** 1 habit, 1 unchanged log; errors are visible and exposed semantically on matching field.

**Coverage/evidence:** HT correctionValidationIsShownOnTheMatchingPickerControl; leavingDirtyCorrectionAsksOnceAndKeepEditingRetainsDraft. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="hab-009"></a>

### HAB-009 — Delete one log and delete habit confirmation

**Priority:** P0 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; H1 + HL1/HL2.

1. HL1 history Delete → Cancel → Both logs remain.
2. HL1 Delete → Confirm → Only HL1 removed; count D=1.
3. Expand Manage habit → Delete habit → Cancel → Habit and HL2 retained; history remains above management.
4. Delete habit → Confirm → Returns Habits; habit, remaining log, schedules/reminder records removed; relaunch shows empty state.

**Persisted oracle/count:** 0 habits/logs/schedules after final delete; unrelated record scope can be checked by adding H2 (then H2 alone remains).

**Coverage/evidence:** HT historyPrecedesCollapsedManagementAndDeleteRemainsConfirmed; repository cascading deletion. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="hab-010"></a>

### HAB-010 — Skip remaining and resume today's counting

**Priority:** P1 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; H1 + HL1.

1. Open H1 at D → Skip remaining → Count stays 1/2; remaining-skipped state displayed.
2. Return Habits/Today → Same 1/2 and skipped state; no fabricated successful repetition.
3. Open detail → Resume today → Skip marker clears; Log → Count 2/2.
4. Select D−1 → No skip marker/count inherited; relaunch D → 2/2 retained.

**Persisted oracle/count:** 1 habit, 2 logs, D skip choice removed at end; skip alone never adds a log.

**Coverage/evidence:** HabitsViewModel.setSkipRemaining; RoomHabitRepository day-choice persistence. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="hab-011"></a>

### HAB-011 — Schedule changes take effect tomorrow and cancel

**Priority:** P0 · **Level:** UI/date boundary · **Status:** NOT RUN

**Fixture/setup:** B; H1 + HL1.

1. Edit H1 → Target 3, choose Weekdays (Monday–Friday); Save → Detail shows pending effective D+1; D remains 1/2.
2. Cancel pending change → Pending card removed; D/D+1 fallback schedule remains target 2/Daily.
3. Edit target 3/Weekdays again → Save; advance clock to D+1 12:00 and reopen → Active target 3 schedule; yesterday D remains 1/2.
4. Review history → HL1 unchanged; no retroactive target/weekday rewrite.

**Persisted oracle/count:** 1 habit, 2 versions after final save; 1 log. Capture versions/effectiveFrom; restoring clock is cleanup only.

**Coverage/evidence:** HT pendingScheduleKeepsEffectiveDateAndCancellationAction; scheduleFor/version rules. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="hab-012"></a>

### HAB-012 — Pause/resume/archive and future-start restrictions

**Priority:** P1 · **Level:** UI/date boundary · **Status:** NOT RUN

**Fixture/setup:** B; H1,H3.

1. H1 detail → Manage → Pause → Pending D+1; today remains active. Advance to D+1 → Paused/unloggable, history readable.
2. Manage → Resume → Pending D+2; advance D+2 → Active/loggable.
3. Manage → Archive → Pending D+3; advance D+3 → Archived/unloggable; history still readable.
4. Fresh B+H3 variant: before start, Manage → No Pause control; Archive applies at start; no Resume control can reactivate archived future-start habit.

**Persisted oracle/count:** Main variant 2 habits, ≤5 schedule versions total, 0 logs; future-start variant 1 habit/1 edited-in-place initial schedule. Record expected dates, not immediate pause assumptions.

**Coverage/evidence:** HT historicalDetailUsesTodaysLifecycleForPauseResumeControl; archivedFutureStartHabitDoesNotExposeResumeInManagement. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="hab-013"></a>

### HAB-013 — Scheduled versus other habits and compact Today preview

**Priority:** P1 · **Level:** UI/crossscreen · **Status:** NOT RUN

**Fixture/setup:** B; H-set.

1. More → Habits at Monday D → H1 in scheduled group; H2 (Tue/Thu) and H3 (future) under Other habits; empty scheduled message differs from empty database.
2. Expand Other habits → Open H2 → Log disabled with not-scheduled reason; H3 disabled with future/start context.
3. Today at D → Preview prioritizes scheduled H1; at most two habit previews; total/count-to-open-full-list remains accurate for three definitions.
4. Log H1 from Today; open full Habits → H1 count 1/2; open same detail → Same log ID and date.

**Persisted oracle/count:** 3 habits, 1 log; preview cap is presentation only, not data loss.

**Coverage/evidence:** HT todayPreviewShowsScheduledRowsBeforeOlderUnscheduledHabitsAndCapsAtTwo; compactPreviewKeepsSeparateFortyEightDpActionsAtLargeText. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="hab-014"></a>

### HAB-014 — Timeline and Review use the same corrected log

**Priority:** P1 · **Level:** UI/crossscreen · **Status:** NOT RUN

**Fixture/setup:** B; H1 + HL1 at D and HL3 at D−1.

1. Timeline → select D → Open the H1 repetition card → Habit detail highlights exact HL1, not arbitrary latest record.
2. Edit selected HL1 → move to D−1 with no time; Save → D count 0 and D−1 count 2.
3. Timeline D−1 → Both distinct repetitions visible; select HL1 → Correct selected-log arrival/detail.
4. Review current week including D → Habit summary reflects schedule and D count 0; past-week/date context agrees with persisted logs; no moved record counted twice.

**Persisted oracle/count:** 1 habit, 2 logs; HL1 UUID constant across Timeline/detail/Review; counts D=0,D−1=2.

**Coverage/evidence:** HT HabitTimelineFlowTest/HabitReviewFlowTest; targeted selected-log arrival. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="hab-015"></a>

### HAB-015 — Single-time reminder, log-one and target suppression

**Priority:** P0 · **Level:** Device/UI + notification · **Status:** NOT RUN

**Fixture/setup:** B; H1 target 2 count 0; D noon; notifications allowed.

1. Open H1 detail → Reminder time → set 12:02; enable reminder → Configured/pending status; relaunch → Time/enabled retained.
2. At eligible due slot → One habit notification; tap Log one → H1 D count 1/2, one log.
3. Open H1; add second log → Target reached; reminder action from original slot must not add another repetition on replay.
4. Disable reminder → Status/config off and no further delivery; target/logs unchanged.

**Persisted oracle/count:** 1 habit, 2 logs, one enabled-then-disabled configuration; replay rejection requires captured token harness if OS removes notification action.

**Coverage/evidence:** HabitReminderPlanner.dueReminderSlot; detail one-time UI; ReminderWork/Platform. UI does not expose interval or multiple times. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="hab-016"></a>

### HAB-016 — Reminder cancel, blocked permission, quiet hours and snooze

**Priority:** P1 · **Level:** Device/UI + planner harness · **Status:** NOT RUN

**Fixture/setup:** B; H1; notification due 12:02, enabled.

1. Change reminder time → select 12:30 then Cancel → Persisted 12:02 retained.
2. Revoke notification permission; return → Blocked status, habit/count unaffected; grant again → Reconciliation resumes eligibility.
3. Set global quiet hours 12:00–13:00 → 12:02 slot suppressed. Fresh baseline with quiet hours off: deliver slot → Snooze → Next action due ten minutes later, no log.
4. Harness at 23:55 with quiet hours 22:00–08:00 and a slot expiring midnight → Snooze rejected; no next-day phantom slot. Restore real clock/preferences after evidence.

**Persisted oracle/count:** 1 habit, 0 logs throughout; at most one current regular/snooze runtime; exact expiry/quiet logic measured by planner, not a promised exact OS alarm.

**Coverage/evidence:** HabitReminderPlanner.snoozedReminder; integration subcase uses valid ReminderSlot/config input, no UI interval setup invented. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="hab-017"></a>

### HAB-017 — Log/undo/save failures retain exact retry context

**Priority:** P0 · **Level:** Integration/harness · **Status:** NOT RUN

**Fixture/setup:** B; H1; inject one-shot repository gates; run each variant on B+H1.

1. Fail log before commit → Pending repetition error and explicit Retry tied to H1/date D; navigate to another selected date → Retained action still identifies original habit/date.
2. Remove gate, Retry → Exactly one log on D, none on newly selected date. Commit-and-throw variant → Retry reconciles same pending log ID, not a duplicate.
3. Fail deleteLog during Undo → Undo failure offers Retry for same ID; remove gate → Retry removes only that log.
4. Fail habit editor save before commit → Fields remain; retry by Save after gate removal → One habit/update, no duplicate schedule at same effective date.

**Persisted oracle/count:** Per isolated log variant ≤1 log; final undo variant 0. Persist pending IDs/payload/date and mutation invocation evidence.

**Coverage/evidence:** HT detailRendersRetryForFailedLog, HabitsViewModelTest/HabitEditorViewModelTest; operation failures require delegate harness. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="hab-018"></a>

### HAB-018 — Stale date observation and presentation matrix

**Priority:** P1 · **Level:** Integration/harness + UI · **Status:** NOT RUN

**Fixture/setup:** B; H1; harness delays/fails observeDay(D+1), then releases latest data.

1. Select D+1 while old D row is on screen; invoke captured old Log callback → No D or D+1 log; stale generation cannot write.
2. Fail new day observation → Retry shown; stale row cannot log. Retry and return to D → Valid fresh row can log once.
3. Rotate/recreate, then change device zone to UTC while tracking zone remains Asia/Tehran → Stored log date/zone and selected tracking date do not silently migrate; restore device zone.
4. Repeat row/detail/editor/correction in Dark/System, FR then FA RTL, 200% font and TalkBack → Separate named Open/Log controls, progress semantics, readable local dates/numbers and ≥48 dp controls; no hidden footer behind IME/navigation.

**Persisted oracle/count:** 1 habit/1 valid log; tracking zone fixed from first habit creation. Capture stale callback invocation and all matrix screenshots; restore EN Light/zone.

**Coverage/evidence:** HT HabitDateSelectionFlowTest/large-text preview; trackingZone persistence. Platform midnight transition should also be repeated at D→D+1 with actual clock recorded. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-001"></a>

### FOC-001 — Required setup, presets and duration boundaries

**Priority:** P0 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; no tasks/sessions.

1. More → Focus → With empty name inspect Start → Disabled with required-name explanation; no session.
2. Enter `F1 Draft report`; switch Timed; choose 15,25,50 presets → Each updates selected duration; Count up removes target requirement.
3. Timed → Custom → enter blank,0,181 and nonnumeric text in separate attempts → Start blocked with duration validation; no write.
4. Enter 1 then 180 → Start enabled for each; return 1; Start once → One Timed session with target 60,000 ms; End and save before target.

**Persisted oracle/count:** 1 terminal session/1 run maximum; rejected configurations create zero sessions; no need to wait 180 minutes.

**Coverage/evidence:** FT FocusSetupContentTest/FocusSetupStateTest; FocusMetadata min 1/max 180. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-002"></a>

### FOC-002 — Name/tag limits, Unicode and draft recreation

**Priority:** P1 · **Level:** UI + integration for raw control characters · **Status:** NOT RUN

**Fixture/setup:** B; no sessions; clipboard A×120/121, T×50/51.

1. Enter 121-character name → Start blocked; replace 120 → Valid. Expand tag; enter 51 ASCII characters → Invalid; replace 50 → Valid.
2. Set name `F1 تمرکز café`, tag `Work`; rotate/recreate and leave/reopen setup → Supported setup draft remains; no auto-start.
3. Replace tag with spaces → Invalid explicit nonempty tag; clear completely → Untagged is valid.
4. Harness raw newline/control-character name/tag → Normalization rejects before write. Start valid 120-code-point name/empty tag once then end → Exact normalized name retained.

**Persisted oracle/count:** 1 terminal session/1 run maximum, no tag when empty. Name limit is 120 Unicode code points; tag limit 50 UTF-16 code units, not a guessed code-point rule.

**Coverage/evidence:** FocusMetadata.normalizeFocusStartInput; FT setup tests; IMEs may sanitize controls so harness variant required. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-003"></a>

### FOC-003 — Count-up pause/resume/finish persistence

**Priority:** P0 · **Level:** UI + deterministic integration oracle · **Status:** NOT RUN

**Fixture/setup:** B; execute F1 recipe; inspect each transition.

1. Start F1 Count up → Running/elapsed visible, one session and one open run; normal bottom navigation guarded.
2. After≥6 seconds Pause → Paused label; credit C1 freezes during 5-second wait.
3. Resume → Same session ID, second run; End and save after≥6 seconds → Terminal count-up ended summary; Saved duration C excludes paused time.
4. Done; History → F1 → time breakdown → Two closed segments summing C; relaunch → Same terminal row and total.

**Persisted oracle/count:** 1 FINISHED session,2 runs; zero open sessions; deterministic samples yield exactly 20,000 ms. Capture C before any delete.

**Coverage/evidence:** FT pauseResumeStopPersistsExactActiveTimeAndCommandReceipts; lifecycle tests. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-004"></a>

### FOC-004 — Running Back choices and paused leave

**Priority:** P0 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; F-start F1 Count up without terminal recipe.

1. Press system Back → Dialog has Keep focusing, Pause and leave, End and save; timer remains coherent.
2. Keep focusing → Running same session, no write to finish.
3. Back → Pause and leave → Paused session saved; app destination becomes usable; Today/More advertises resumable state.
4. Reopen Focus → Same paused ID and frozen elapsed; Resume then Back → End and save → One terminal session and no active guard.

**Persisted oracle/count:** 1 session, at most 2 runs; leaving paused never implicitly resumes.

**Coverage/evidence:** FT recreationSystemBackPauseResumeAndStopKeepOneFocusSession. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-005"></a>

### FOC-005 — Activity recreation and same-process background

**Priority:** P0 · **Level:** UI/lifecycle · **Status:** NOT RUN

**Fixture/setup:** B; F-start F1 Count up.

1. Run≥6 seconds; rotate/recreate Activity → Same ID, running elapsed does not reset or duplicate.
2. Press Home; wait 10 seconds without terminating process; return via launcher → Focus still running and elapsed includes background time.
3. Pause; Home/wait 5 seconds/return → Paused credit unchanged.
4. End and save; relaunch → One terminal row; saved total includes active background time, excludes pause.

**Persisted oracle/count:** 1 session,1 run if not resumed after pause; no negative/doubled duration. Record process ID and actual time interval.

**Coverage/evidence:** FT FocusLifecycleTest; runtime application-owned checkpoint loop. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-006"></a>

### FOC-006 — Timed target expiry, notification and early stop

**Priority:** P0 · **Level:** Device/UI + deterministic integration · **Status:** NOT RUN

**Fixture/setup:** B; F2 only; notifications allowed.

1. Start Timed 1 minute → Remaining countdown decreases; no count-up label substituted.
2. Leave app background within same process until target passes; return → Target-reached terminal state (not paused/negative remaining); saved credit exactly 60,000 ms.
3. Inspect target alert if posted → Opening it returns correct completion; repeated open does not create a new session or duplicate finish.
4. Separate B variant: Timed 1 minute; End and save before 60 seconds → Stopped before target summary, credit strictly below 60,000 ms and one closed run.

**Persisted oracle/count:** Each isolated variant 1 session/1 run. Denied alerts must not prevent durable target settlement; exact delivery timing is not guaranteed by UI.

**Coverage/evidence:** FT completionSummaryDistinguishesEarlyEndTargetAndInterrupted; FocusExpiryPlatform/timing rules. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-007"></a>

### FOC-007 — Process death explicit recovery choices

**Priority:** P0 · **Level:** Integration/harness + device lifecycle · **Status:** NOT RUN

**Fixture/setup:** B; F1 running; checkpoint captured after≥6 seconds; do not clear data or force-stop workers as a substitute for process death.

1. Terminate only app process using disposable-device test controls; wait 5 seconds; relaunch → Recovery required for changed process, no silent unlimited credit.
2. Choose Continue elapsed (`focus_recovery_continue`) → Same session continues and eligible gap credited once; Pause/finish → One terminal row.
3. Fresh B variant with same checkpoint/process-change condition → Choose End at checkpoint (`focus_recovery_end`) → Interrupted/uncertain terminal result uses checkpoint credit, excludes unconfirmed gap.
4. Relaunch either variant → No repeat recovery or second terminal row.

**Persisted oracle/count:** Each variant 1 session; continue variant≤2 runs, end variant 1 run; exact sample/receipt proof in harness. Capture checkpoint credit and boot/process identity.

**Coverage/evidence:** FT changedProcessRequiresExplicitRecoveryAndEndsOnlyAtTheSavedCheckpoint; processRecoveryContinueCreditsGapOnceAndEndChoiceUsesCheckpoint. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-008"></a>

### FOC-008 — Reboot and wall-clock discontinuity recovery

**Priority:** P0 · **Level:** Integration/harness · **Status:** NOT RUN

**Fixture/setup:** B; valid running F1 snapshot/checkpoint at elapsed 10,000 and captured boot ID; use FocusTimeSample constructor, no real production clock corruption.

1. Reconcile with different boot ID → Interrupted at last checkpoint; no wall-clock gap credited; terminal reason identifies recovery.
2. Fresh baseline: supply coherent next sample with regressed elapsed or incompatible wall-clock jump according to FocusTiming rules → Safe interrupted/recovery outcome, no negative duration or over-credit.
3. Inspect History breakdown → Saved checkpoint-only runs, recovered/uncertain guidance where applicable.
4. Try duplicate reconciliation/old-generation command → AlreadyApplied/conflict-safe result; credit unchanged.

**Persisted oracle/count:** Each variant 1 session/1 run, checkpoint credit 10,000 ms in deterministic fixture; no terminal total inflation.

**Coverage/evidence:** FT changedBootInterruptsAtLastCheckpointAndRetainsExactSubMinuteCredit; FocusTimingTest. Additional wall-discontinuity harness must identify exact tested branch and expected terminal reason before execution; otherwise BLOCKED. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-009"></a>

### FOC-009 — Break default, background continuity and expiry

**Priority:** P1 · **Level:** UI/device lifecycle · **Status:** NOT RUN

**Fixture/setup:** B; F-terminal.

1. Completion → expand Break options → Take a 5-minute break → Break countdown; no new Focus session.
2. Done → Navigate elsewhere, wait≥5 seconds then return Focus → Break continues from elapsed time, does not restart at 5:00.
3. End break → Break cleared; F1 terminal duration unchanged.
4. Separate B+F-terminal variant: Customize break 1 minute; let expire → Break over offers Done/Use previous settings; using settings prepares setup only, no auto-start.

**Persisted oracle/count:** 1 terminal session/2 runs;0 break records after end/expiry clearing; break time contributes 0 to focus totals.

**Coverage/evidence:** FT activeBreakContinuesAfterDoneAndCanBeEndedOnReturn; expiredBreakOffersDoneAndPreviousSettingsWithoutStartingFocus. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-010"></a>

### FOC-010 — Break validation and replacing an active break

**Priority:** P1 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; F-terminal.

1. Customize break → Enter 0,61,blank → Start break disabled/error; invalid value stays for correction; Cancel → No break.
2. Customize →60 → Valid Start; Cancel before start; Customize →1 → Start → Active 1-minute break.
3. Use previous settings/prepare another Focus start while break active; tap Start → Replace-break confirmation.
4. Cancel → Existing break remains; attempt again → Confirm → Break ends and exactly one new Focus session starts; stop it.

**Persisted oracle/count:** 2 terminal sessions maximum; F1 two runs + new session one run =3; break duration allowed 1..60 and never counted as focus.

**Coverage/evidence:** FT customBreakKeepsInvalidMinutesForCorrection; startingFocusDuringBreakRequiresConfirmationBeforeReplacingIt. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-011"></a>

### FOC-011 — Reflection save, edit and remove optional content

**Priority:** P1 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; F-terminal.

1. Completion → Add reflection → Select Made progress; enter Next step `  Review section 2  `; Save reflection → Saved summary with trimmed next step.
2. Done → History → expand F1 → Reflection shows same outcome/step; Edit → replace outcome Finished intended work and clear next step → Save.
3. Relaunch detail → Outcome updated, next step absent; total C unchanged.
4. Edit → attempt 281-character next step → Input rejected with limit feedback, prior value retained and Save blocked while rejected; replace with 280 → Saves; do not create another session.

**Persisted oracle/count:** 1 session/2 runs,1 reflection payload on same session; names/timing unchanged.

**Coverage/evidence:** FT reflection editor, FocusReportsTest; normalizeFocusReflection 280-code-point limit/control rejection. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-012"></a>

### FOC-012 — Unsaved reflection leave choices and lifecycle

**Priority:** P0 · **Level:** UI/lifecycle · **Status:** NOT RUN

**Fixture/setup:** B; F-terminal, no reflection.

1. Add reflection → Made progress + `Draft next step`; rotate → Draft retained within supported saved editor state.
2. Tap Done → Unsaved-reflection dialog; Keep editing → Draft remains, no reflection persisted.
3. Done → Discard and leave → Leaves completion; history has no reflection.
4. Fresh B+F-terminal variant at completion: edit reflection, tap Done → Save and leave → Navigation occurs only after saved outcome; no extra session.

**Persisted oracle/count:** 1 terminal session/2 runs; final reflection only from explicit save; discarded draft absent.

**Coverage/evidence:** FT completionNavigationOffersCancelDiscardAndPreservesReflectionDraft. Save-and-leave dialog is completion navigation; history editor Cancel uses its own cancel behavior. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-013"></a>

### FOC-013 — Repeat and start another never auto-start

**Priority:** P1 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; F-terminal with reflection next step `Review section 2`.

1. Completion → Use session settings (`focus_repeat`) → Setup copies F1 name/Work/count-up; Next step hint shown; no running session.
2. Change name to `Local draft`; repeat F1 through Recent work/History where offered → Replacement confirmation; Cancel → Local draft retained.
3. Confirm replacement when offered → Saved session settings load; task selection validated; Start still explicit.
4. Return completion/history; Start another (`focus_start_another`) → Fresh setup rather than duplicate F1 start; close without Start.

**Persisted oracle/count:** 1 terminal session/2 runs throughout; setup actions create 0 records.

**Coverage/evidence:** FT completionRepeatUsesTheSavedSessionWithoutStartingOne; reducer repeat loading/replacement handling. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-014"></a>

### FOC-014 — Task selection, cancel/remove and task-editor handoff

**Priority:** P0 · **Level:** UI/crossscreen · **Status:** NOT RUN

**Fixture/setup:** B; T2,T5,T6; no sessions.

1. Tasks → T2 editor → Focus on task → Focus setup shows T2 task link/name; no session yet.
2. Choose/change task → Search `Outline` → Only open T6 with T5 context and independent importance; Back → Previous selection/draft retained.
3. Select T6 → Setup task link changes; Remove task → Link clears without deleting either task.
4. Separate B+T2 editor with unsaved note `Draft`: Focus action → Cancel → Draft retained; reopen prompt → Save and continue → Note saved before setup; Discard and continue variant keeps original note.

**Persisted oracle/count:** Main variant 3 tasks,0 sessions; separate variants 1 task,0 sessions; no unintended completion or task creation.

**Coverage/evidence:** FocusTaskPicker; TodoEditorScreen focus confirmation; FT setup/task tests. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-015"></a>

### FOC-015 — Linked task total, explicit completion and unlink

**Priority:** P0 · **Level:** UI/crossscreen · **Status:** NOT RUN

**Fixture/setup:** B; T2 only; start Count-up F1 linked to T2; run≥6 seconds then End and save.

1. Completion → Linked T2 shown; task remains open until Mark complete is explicitly tapped.
2. Tap Mark complete → T2 completed; no second session; open Tasks/Completed → Same ID.
3. Return saved F1 History detail → Unlink task → Explicit link removal; session remains terminal with original credit/task-origin classification.
4. Reopen T2 → Focus total excludes explicitly unlinked session; reopen F1 → Link removed state; global saved Focus total still includes its credit.

**Persisted oracle/count:** 1 task completed,1 terminal session/1 run; final taskId=null but taskOrigin retained; no loss of focus duration.

**Coverage/evidence:** FT failedTaskCompletionAndUnlinkKeepSeparateExplicitRetryActions; FocusProductRepository task totals/unlink semantics. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-016"></a>

### FOC-016 — Completed/deleted task selection conflict and family completion

**Priority:** P0 · **Level:** Integration/harness + UI · **Status:** NOT RUN

**Fixture/setup:** B; T5,T6,T7; prepare linked F1 setup for T6; second repository client.

1. After selecting T6, second client completes or deletes it; tap Start → Selection conflict/link-removed handling requires explicit correction, no session silently links stale revision.
2. Fresh B: choose T5, start/finish short F1 → Completion Mark complete → Family preview includes 2 open children.
3. Cancel → T5/T6/T7 remain open; Mark complete → Confirm → Family completes atomically.
4. Inspect Tasks/Completed → Three original task IDs; Focus total remains the one session credit.

**Persisted oracle/count:** Conflict variant 0 sessions; family variant 3 completed tasks,1 terminal session/1 run. Capture exact conflict result rather than treating silent relink as success.

**Coverage/evidence:** FocusViewModel task selection and TodoFamilyActions; additional second-client setup required (BLOCKED if absent). Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-017"></a>

### FOC-017 — History filters, detail, delete cancellation and totals

**Priority:** P0 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; F-set.

1. Focus → History → Two terminal sessions; expand F1 → name/tag, total C, two-run breakdown; paused gap not displayed as credited work.
2. Search `F2 Reading` → One F2; clear; tag filter Work → F1; Untagged → Empty; All → Both.
3. F1 Delete → Cancel → Two sessions and total C+60,000 unchanged.
4. Delete F1 → Confirm → Only F2 remains; Today/Review/Focus saved total becomes 60,000 ms; relaunch → Deleted session stays absent.

**Persisted oracle/count:** 1 terminal session/1 run at end; delete removes only confirmed session and its product data; no stale total C.

**Coverage/evidence:** FT historyShowsCreditedRunsAndOnlyDeletesAfterConfirmation; FocusHistoryViewModel. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-018"></a>

### FOC-018 — Saved session metadata and tag rename/merge

**Priority:** P1 · **Level:** UI · **Status:** NOT RUN

**Fixture/setup:** B; F-set with Work and Study tags.

1. History → F1 detail → Edit session details → name `F1 Revised`, tag `Study`; Cancel → Original F1/Work remains.
2. Edit again → same values → Save → Row/search/insights show F1 Revised/Study; C unchanged.
3. Focus Settings → Tag manager → rename Work to `Writing` → Catalog updates; then rename Writing to existing `Study` → Merge confirmation identifies source/target.
4. Cancel merge → Both catalog IDs retained; repeat rename/Confirm merge → Source resolves to Study; saved session filters use canonical target without duplicates.

**Persisted oracle/count:** 2 sessions/3 runs, canonical tags 1 after merge (source may remain as merge alias metadata); session IDs/credit unchanged.

**Coverage/evidence:** FocusSessionMetadataEditor/FocusTagManager; FT tagCatalogConflictKeepsTheEditedNameVisibleAndRecoverable. Capture catalog aliases, not raw tag-row count alone. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-019"></a>

### FOC-019 — Weekly goal validation and next-Monday activation

**Priority:** P1 · **Level:** UI/date boundary · **Status:** NOT RUN

**Fixture/setup:** B; no goals/sessions; D Monday noon.

1. Focus Settings → expand Weekly focus goal → Target 0/10081 or no preferred days → Save goal shows validation, no goal version.
2. Enter 60 minutes; Monday/Wednesday only; use device zone Asia/Tehran; Save goal → Scheduled for D+7 (Monday 2026-11-23), not today.
3. Leave/reopen → Pending status/date/zone retained; today/Review does not invent active goal progress before activation.
4. Advance to D+7 noon → Active 60-minute weekly goal with selected days; Disable goal → Disabled without deleting saved sessions; restore clock during cleanup.

**Persisted oracle/count:** 1 goal version maximum,0 sessions; target range 1..10080, activation strictly next Monday. Persist settings revision/zone/effectiveMonday.

**Coverage/evidence:** FocusGoalSettings; nextGoalMonday; FT pendingGoalIsShownAsScheduledUntilItsEffectiveMonday. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-020"></a>

### FOC-020 — Goal amendment and failure retry

**Priority:** P1 · **Level:** Integration/harness + UI · **Status:** NOT RUN

**Fixture/setup:** B; schedule 60-minute Mon/Wed goal via FOC-019 setup recipe without executing its date advance; one-shot scheduleGoal gate.

1. Edit pending target 90, Mon/Fri → Fail before commit on Save → Save error, edited values/effective date retained; original 60-minute persisted version unchanged.
2. Remove gate → Save goal again →90-minute pending configuration for same D+7; no duplicate effective-Monday versions.
3. Simulate lost acknowledgement after applying Disable goal → Error/retry with retained context → Reconciles one disable; saved goal records not duplicated.
4. Reopen Settings → Saved status matches repository enabled flag/revision.

**Persisted oracle/count:** ≤1 goal version at same zone/Monday,0 sessions; retry command context stable for unavailable outcomes.

**Coverage/evidence:** FocusGoalSettings PendingGoalWrite; FocusProductRepository generation/revisions; targeted harness variant required, otherwise BLOCKED. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-021"></a>

### FOC-021 — Focus reminder scheduling, permission and invitation open

**Priority:** P1 · **Level:** Device/UI + notification · **Status:** NOT RUN

**Fixture/setup:** B; no Focus sessions; D noon; quiet hours default; notifications allowed.

1. Focus Settings → expand Focus reminders → Deselect all days while disabled; enable → Weekday-required error and no enabled config.
2. Select Monday only; time 12:02; enable → Each valid change auto-saves; summary shows schedule and notification permission separately; no Save button invented.
3. At eligible due slot → One Focus invitation; tap Open → Focus setup prepared, no session running until explicit Start.
4. Disable schedule; reopen Settings → Disabled persisted. Deny notifications then return → Blocked status is truthful; enabling schedule cannot grant OS permission by itself.

**Persisted oracle/count:** 0 sessions/runs,1 schedule configuration; at most 1 delivered invitation for D; opening notification never auto-starts.

**Coverage/evidence:** FocusInvitationSettings/saveConfig; FocusInvitationWork/Platform; inclusive one-hour expiry requires harness boundary case below. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-022"></a>

### FOC-022 — Invitation suppression, expiry and draft replacement

**Priority:** P1 · **Level:** Integration/harness + UI · **Status:** NOT RUN

**Fixture/setup:** B; valid Monday 12:02 invitation config; capture due slot/notification intent; isolated variants.

1. At due time start valid F1 already running or paused → Worker evaluates slot → Suppressed according to current open-session eligibility; no duplicate Focus session or disruptive auto-start.
2. Fresh B variant: open invitation at 12:30 while setup contains `Local draft` → Replacement dialog; Keep draft → Local draft retained; repeat eligible open and Replace → Prepared invitation setup, still no auto-start.
3. Harness deliver at 13:02 exactly for 12:02 slot → Deadline boundary remains eligible if other rules permit; at 13:02:00.001 → Expired; no delivery/open mutation.
4. Enable quiet hours 12:00–13:00 before 12:02 → Due slot skipped per quiet policy; disabling later must not replay duplicate claimed slot.

**Persisted oracle/count:** Each variant 0 sessions except suppression variant 1 open/terminal cleanup session; captured config revision, claim token/deadline and worker result required.

**Coverage/evidence:** FocusInvitationRules deadline inclusive; RoomFocusInvitationRepository/FocusInvitationWork. Missing worker/intent injection harness = BLOCKED; do not fabricate notification UI. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-023"></a>

### FOC-023 — App shortcuts discovery, running freeze and unavailable app

**Priority:** P1 · **Level:** Device/UI + platform harness · **Status:** NOT RUN

**Fixture/setup:** B; install one disposable launchable test app A in personal profile; record package/label; no sessions.

1. Focus Settings → expand App shortcuts; search exact A label → Select A; leave/reopen → Selection retained; search impossible text → No matches, selection not lost.
2. Start F1 Count up → Expand shortcuts → A available; tap A → External app launches after checkpoint; return → Same running session/credit, no second session.
3. While running, Settings selection controls are frozen when reachable; pause/end then change selection → Allowed only outside running state.
4. Uninstall/disable A on disposable device then refresh → Unavailable selection visible with Remove; launching captured shortcut via harness gives unavailable feedback and Retry/Manage; remove selection → No stale shortcut.

**Persisted oracle/count:** 1 terminal session/1 run after cleanup stop; selected package list contains no A at end. System apps outside selection remain Android-accessible; do not expect app blocking.

**Coverage/evidence:** FT FocusAllowedAppsTest/FocusShortcutHandoffTest; AllowedFocusApps personal-profile discovery. Captured unavailable callback can require platform fake. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-024"></a>

### FOC-024 — Insights, Today and weekly report consistency

**Priority:** P1 · **Level:** UI/crossscreen + deterministic date fixture · **Status:** NOT RUN

**Fixture/setup:** B; F-set; use recorded C; no open session.

1. Focus → Expand View progress → Today/This week/All time → Saved total C+60,000 for this fixture; average=(C+60,000)/2 with UI rounding; longest F2 if C<60,000.
2. Today Focus card → Saved total same; open a fresh count-up session then Pause/leave → In-progress credit shown separately, never added to saved terminal total.
3. End that session and save → Saved total increases by exactly its persisted credit; open day history from Today → Date range D and correct session IDs.
4. Review current week → Daily bars/contributions and active-day count agree; no previous-week data yields empty-baseline explanation rather than invented percentage. Repeat week-start preference Saturday → Week bounds/labels adjust consistently.

**Persisted oracle/count:** 3 terminal sessions maximum/4 runs; saved total=sum of terminal run credit;1 active day for all-on-D fixture. Capture selected week/range/zone.

**Coverage/evidence:** FT todaySeparatesSavedTotalsFromTheOpenSessionAndOpensDayHistory; weeklyReportSupportsSaturdayStartAndDoesNotInventPercentForEmptyBaseline; FocusInsightsTest. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-025"></a>

### FOC-025 — Cross-midnight report contribution and exact session target

**Priority:** P1 · **Level:** Integration/harness · **Status:** NOT RUN

**Fixture/setup:** B; deterministic F3 session named `F3 Midnight`, Count up, Work; two valid runs: D−1 23:59:30–D00:00:30 (60s), pause 30s, then D00:01:00–00:01:30 (30s); total 90,000 ms.

1. Query/open Today D report → D contribution 60,000 ms; D−1 report →30,000; full session duration 90,000 in both ranged history contexts.
2. Open session-specific detail from longest/recent work card → Exact F3 UUID, full 90,000 total and credited segments; paused 30s not credited.
3. Switch device display zone to UTC → Range contributions recomputed for selected zone without changing canonical run credit/IDs.
4. Delete F3 from history with confirmation → Both day totals lose respective contributions once; no orphan report row.

**Persisted oracle/count:** At most 1 session/2 runs/tag 1;0 sessions after delete. Use coherent repository samples/commands, not arbitrary invalid persisted snapshots.

**Coverage/evidence:** FocusStatistics/Insights range contribution rules; FT FocusReportPersistenceTest and FocusInsightsTest. Requires new two-day harness setup; BLOCKED until fixture can be supplied. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-026"></a>

### FOC-026 — Write/load failures, retries and emergency exit

**Priority:** P0 · **Level:** Integration/harness · **Status:** NOT RUN

**Fixture/setup:** B; one-shot FocusRepository gates; each operation variant starts independently; capture operation ID/generation/payload.

1. Fail initialization read → Loading ends in Retry/error; emergency exit remains reachable; remove gate → Retry initializes correct state.
2. Fail Start before commit, then separately commit-and-return Unavailable → Setup preserves captured name/tag/task/timer; Retry reconciles one session; no edited payload reused under old operation ID.
3. With running F1 fail Pause/Resume/End individually before commit → Specific operation error, last durable state retained; remove gate → Retry applies original intent once.
4. With checkpoint/write permanently unavailable open Help → Emergency exit → Cancel keeps guard; Confirm records marker when possible and exits; on next healthy initialization matching marker finalizes at checkpoint. If marker also fails, failure guidance remains truthful and uncertain work is not silently counted.

**Persisted oracle/count:** Per independent variant≤1 session/2 runs; emergency result has no inflated credit; final 0 open sessions when reconciliation succeeds. Capture failed marker branch separately.

**Coverage/evidence:** FT ambiguousActionRetryKeepsTheOriginalReceiptPayloadAndGeneration; matchingEmergencyMarkerFinalizesAtCheckpointAndClearsTheMarker; runtime emergency/initialization UI. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

<a id="foc-027"></a>

### FOC-027 — Product failures and accessible bounded presentation

**Priority:** P1 · **Level:** Integration/harness + UI matrix · **Status:** NOT RUN

**Fixture/setup:** B; F-terminal; optional T2-linked variant; one-shot product/read gates. For presentation states, restore B separately for setup, running, paused and terminal F1 variants; inspect each state of that one session rather than accumulating sessions.

1. Fail saveReflection → Completion stays; next step/outcome draft retained; Done→Save and leave cannot navigate until successful retry. Remove gate → Retry saves one reflection.
2. Linked variant: fail task completion then fail unlink independently → Separate explicit retries; retrying one does not execute the other; duplicate response does not double-complete/delete task.
3. Fail History/insights load → Visible unavailable/retry state; remove gate → Same IDs/credit restored, no fabricated empty-success totals.
4. Inspect setup/running/paused/completion/history/settings at 200% font, Dark/System, FR and FA RTL, TalkBack → Timer announced with meaningful elapsed/remaining label, independent named controls, correct local text/numbers,48 dp targets, readable charts/special Focus visuals, no IME/inset-obscured action. Restore EN Light.

**Persisted oracle/count:** At most 1 session/2 runs and ≤1 task per isolated variant; use separate B restores for presentation states.

**Coverage/evidence:** FT failedReflectionSaveDoesNotLeaveCompletion; failedTaskCompletionAndUnlinkKeepSeparateExplicitRetryActions; FocusScreenTest/ReportsTest. Product gates require separate integration setup; unavailable setup = BLOCKED. Capture action-specific state plus the execution-contract evidence. **Cleanup:** restore B as specified above after collecting counts and IDs.

## Explicit execution gaps and discrepancy handling

- No QA was executed. Cases document source-supported behavior and expected safety invariants, not known results. Record PASS/FAIL/BLOCKED per variant with device evidence; do not silently accept a behavior mismatch because a current unit test exists.
- Error, stale-plan, stale-callback, lost-acknowledgement, recovery-sample and worker deadline variants need the constructor/delegate harness described above. Current named tests are examples, not a claim that every variant is already implemented. If unavailable, mark the precise variant BLOCKED and run the independent UI paths.
- The 50-record cap deliberately excludes multi-page lists/history requiring a 51st entity, existing 101-child and large scale tests, stress loops, long-running battery tests and notification floods. A 50-task first-page boundary is included; it is a correctness check.
- Task recurrence is absent from the current editor/model; older recurrence roadmap QA is not executable scope. Habit UI supports one reminder time, not the richer interval domain model. Focus shortcuts do not enforce system-wide app blocking. No unsupported feature should be reported as missing from these cases.
- New habit past-start validation means historical fixtures require clock-before-create or a valid repository fixture. Keep habit tracking zone fixed after first creation. Goal versions activate strictly next Monday; habit schedule/lifecycle changes normally apply tomorrow, with future-start edits at their start date. Capture all boundaries explicitly.
- OS notification permission/channel state and background delivery may prevent physical notification evidence. Distinguish blocked delivery from a local save failure. Planner/worker harness success cannot substitute for a physical-notification result; report both levels separately.
- Counts for tag merging distinguish canonical visible tags from retained merge aliases; deletion may retain receipt/tombstone metadata. Compare active entities and immutable IDs, not arbitrary total SQLite rows.
