# IP-I01 — Today action hub Implementation Plan

> **For agentic workers** Use the applicable Superpowers implementation skill when this plan is authorized for execution. Steps use checkbox syntax for tracking.

**Goal** Make current commitments and their full lists easy to find while preserving historical-day browsing.

**Architecture** Reorder current route sections and use the current bounded page and explicit links. Expanding the dashboard into a full list would duplicate paging and make the summary harder to scan. Navigation destinations and models already support the chosen hierarchy.

**Tech stack** Kotlin, Jetpack Compose, Luma components and existing route/state/repository contracts.

**Spec** [IP-I01 specification](../specs/2026-11-20-product-improvements-i01-today-action-hub-design.md) · [Task index](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md) · [IP-Q01 scenario](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-01)

## Files and interfaces

Paths are from the project root. Unqualified sibling filenames share the preceding directory.

- `app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt + TodayRoute.kt` — Section order, date context and action callbacks.
- `app/src/main/java/com/luma/lifeos/ui/todo/TodayTasksCard.kt + TodayTasksState.kt` — Loaded-row/total contract, accessible count and existing retry/load-more actions.
- `app/src/main/java/com/luma/lifeos/ui/todo/TodoListScreen.kt + TodoListViewModel.kt` — Existing task bucket labels and filters.
- `app/src/main/java/com/luma/lifeos/ui/habits/TodayHabitsCard.kt + app/src/main/java/com/luma/lifeos/ui/focus/TodayFocusCard.kt` — Keep date-aware habit destination and Focus privacy-aware state.
- Update string resources and plurals in `app/src/main/res/values/`, `values-fr/` and `values-fa/` for affected copy.

TodayRoute/TodayScreen already expose onAllTasks: () -> Unit, onHabits: () -> Unit and onToday: () -> Unit. Use TodayTasksState.Data.total and .rows for overflow; keep selection owned by LifeOsApp.

## Global constraints

Existing Today, Tasks, Habits and Focus source. IP-I06 extends the resulting Today header. Apply the [shared implementation contract](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md#shared-implementation-contract). Record source handoff before IP-Q01.

## Review focus

Changing task visibility through completion can update rows and totals asynchronously. Keep the calculation tied to one emission.

## Implementation checklist

- [ ] **1.** Keep Today’s current TODO_DEFAULT_PAGE_SIZE (50) and nested scroll affordance. Inspect existing task count/overflow and full-list bucket labels. Extend the current projection only if a necessary display fact is missing. Keep repository pagination and task identity.
- [ ] **2.** Place current-day actions together in TodayScreen. Keep selected-day notice/capture and historical mode behavior. Preserve section keys/tags where callers or tests rely on them.
- [ ] **3.** Render one explicit overflow count and preserve Load more and the full Tasks destination. Keep labels distinct for the three requested task groups and retain mutation/Undo routing.
- [ ] **4.** Review source and localization against IP-I01-R1, IP-I01-R2, IP-I01-R3. Record the changed files, interface decisions, risks and source evidence for IP-Q01.

## QA handoff

- [ ] Map `todayOverflowCountsOnlyLoadedMatchingRows`, `pastDayKeepsDatedHabitsAndOffersReturn`, `familyCompletionRemainsUndoable` to [QA-01](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-01) for the final verification task. Extend the existing test surfaces where appropriate. TodayTasksStateTest; TodayTasksFlowTest; TodayScreenTest.
- [ ] Record source status as **SOURCE COMPLETE — QA PENDING** after handoff and leave the QA result **NOT RUN** until IP-Q01 executes.

**Acceptance evidence** Use 57 matching open tasks and the current 50-row page, plus a completed, future and undated task. Assert 7 unloaded initially, correct updates on paging/completion, and correct full-list buckets. Verify past-day, midnight/device zone, hidden-provider and family/Undo paths.
