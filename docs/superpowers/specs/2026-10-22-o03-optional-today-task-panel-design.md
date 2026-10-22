# O03 — Optional — expanded Today task panel specification

Date: 2026-10-22  
Status: Optional / deferred; requires a separate scope decision before implementation.  
Depends on: T31

## Intent and sources

Add the full task-status switch and undated group pictured on Today without silently changing the existing due-task preview.

References: today-light/dark PNGs and HTML: Open/Completed and No date. Read the [shared reference brief](../../luma-redesign/README.md) and [task index](../../UI_REDESIGN_TASKS.md) with this specification. The brief resolves inconsistencies and provides common tokens; it is normative for every task.

## Global constraints

- Native Android, Kotlin, Compose Material 3 and Navigation Compose; keep minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Offline operation; no account, cloud sync, AI service, telemetry, new permissions or schema migration in required visual work.
- System/Light/Dark selection and English/French/Persian support remain available offline.
- Preserve existing route IDs, callbacks, saved state, data semantics, loading/error/retry/undo and accessibility tags unless an optional task explicitly changes its own contract.
- Use actual saved data; never ship reference people, dates, percentages, measurements, quotes or charts as user records.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL and system animation scale.
- Optional tasks default to deferred and are not required for redesign acceptance. No optional feature is authorized for implementation by this planning document.

## Screen and behavior contract

- Replace the bounded preview only when this option is selected. Open shows overdue, due-today and undated open tasks; future-dated open tasks remain in View all. Completed shows completed tasks through existing repository list semantics, labeled independently of the due summary.
- Keep the headline count limited to open overdue/due-today items. Open tab count includes undated items; never equate these two counts.
- Use bounded repository-backed prefixes, initially 50 and increasing by 50 up to 300 with explicit Load more, not an unbounded collection. Introduce TodayTaskPanelFilter OPEN/COMPLETED and a saved selected filter; reset the prefix limit on date/status changes.
- Preserve historical-day hiding and all pending/conflict/missing/retry/reopen feedback, including when a completed item disappears from the active list. Continue to expose View all.
- Extend repository queries only if current list contracts cannot express this panel; no new persistence fields or migration are needed.

## Implementation boundaries

- `app/src/main/java/com/luma/lifeos/ui/todo/TodayTasksState.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/todo/TodayTasksViewModel.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/todo/TodayTasksCard.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/today/TodayRoute.kt` — existing integration surface.

Existing public/internal composable signatures remain compatible except additions explicitly named above. Existing repositories/view models own data and mutations; decorative components receive values and callbacks. New files named in the contract are proposed implementation files, not files already created by this documentation change. Consult the paired plan for test ownership.

### Additional interfaces and data files

Create `app/src/main/java/com/luma/lifeos/ui/todo/TodayTaskPanelState.kt` with `enum class TodayTaskPanelFilter { OPEN, COMPLETED }` and `data class TodayTaskPanelState(val filter: TodayTaskPanelFilter, val tasks: List<TodoTask>, val filteredTotal: Int, val dueOpenTotal: Int, val loadedLimit: Int, val loading: Boolean, val error: Boolean)`. Existing action-feedback state remains separate and unchanged.

Add to `core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoRepository.kt`:

```kotlin
fun observeTodayPanel(date: LocalDate, completed: Boolean, limit: Int = 50): Flow<TodoPage>
```

Implement in `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomTodoRepository.kt` and `core/data/src/main/kotlin/com/luma/lifeos/data/todo/TodoDao.kt`. OPEN uses `completed_at IS NULL AND (due_date <= :date OR due_date IS NULL)` in both rows and total; order overdue/today by due_date, then undated, with created_at and ID ties. COMPLETED uses existing completed-list eligibility/order. Keep dueOpenTotal from the original observeToday total. Do not derive it from the limited displayed items.

Use cumulative bounded windows: limit starts at 50; Load more increases by 50 up to 300 and replaces the observed prefix, rather than concatenating potentially stale pages. At 300, show View all instead of Load more when more exist. On filter/day change reset limit to 50; preserve the filter across recreation using the stable enum name, unknown value → OPEN. Keep each live prefix/count query coherent during writes. This specifies the paging policy and does not change full-list repository behavior.

## Acceptance scenarios

1. Three due/overdue plus one undated open task yields headline 3 and Open (4); future-dated tasks do not enter this panel.
2. More than 50 eligible tasks pages correctly; completing/reopening/filter switching/recreation keeps totals and recoverable feedback coherent.

## Verification and completion

Use focused existing regression coverage plus visual/manual checks for the changed surfaces. Add tests for changed behavior and meaningful state/semantics risks; do not add tests that merely assert padding or duplicate token assignments. Required font/contrast/RTL/inset checks are acceptance work, not a reason to add fake data. Record PASS/FAIL/NOT RUN and evidence in the T33 ledger. No checks are claimed to have run in this specification.

[Implementation plan](../plans/2026-10-22-o03-optional-today-task-panel.md)
