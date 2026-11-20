# IP-I01 — Today action hub Implementation Plan

**Status:** SOURCE COMPLETE — QA PENDING.

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

- [x] **1.** Keep Today’s current TODO_DEFAULT_PAGE_SIZE (50) and nested scroll affordance. Inspect existing task count/overflow and full-list bucket labels. Extend the current projection only if a necessary display fact is missing. Keep repository pagination and task identity.
- [x] **2.** Place current-day actions together in TodayScreen. Keep selected-day notice/capture and historical mode behavior. Preserve section keys/tags where callers or tests rely on them.
- [x] **3.** Render one explicit overflow count and preserve Load more and the full Tasks destination. Keep labels distinct for the three requested task groups and retain mutation/Undo routing.
- [x] **4.** Review source and localization against IP-I01-R1, IP-I01-R2, IP-I01-R3. Record the changed files, interface decisions, risks and source evidence for IP-Q01.

## QA handoff

- [x] Map `todayOverflowCountsOnlyLoadedMatchingRows`, `pastDayKeepsDatedHabitsAndOffersReturn`, `familyCompletionRemainsUndoable` to [QA-01](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-01). IP-Q01 will extend TodayTasksStateTest, TodayTasksFlowTest and TodayScreenTest where needed and execute them during QA.
- [x] Record source status as **SOURCE COMPLETE — QA PENDING** after handoff and leave the QA result **NOT RUN** until IP-Q01 executes.

**Acceptance evidence** Use 57 matching open tasks and the current 50-row page, plus a completed, future and undated task. Assert 7 unloaded initially, correct updates on paging/completion, and correct full-list buckets. Verify past-day, midnight/device zone, hidden-provider and family/Undo paths.

## Source handoff — 20 November 2026

**Status:** SOURCE COMPLETE — QA PENDING. **Base revision:** `c7e3267eb6576e72dc27c18ceb12e6bae9161f09`.

**Changed paths:** `TodayScreen.kt`, `TodayTasksCard.kt`, and the EN/FR/FA string resources; this plan, the IP-I01 specification and the product task index record the source handoff.

**Requirement mapping and decisions:**

- **R1:** The Today card derives unloaded work from `TodayTasksState.Data.total - rows.size`, clamped at zero, from one immutable projection emission. It displays the exact localized unloaded count only above zero and uses the same count to retain Load more. Every nonzero total is visible: totals of one or two use a compact count badge with a localized singular/plural accessible description; larger totals keep the existing scroll hint. View all, retry and task identity remain tied to the existing projection.
- **R2:** The full Tasks route remains unfiltered. `TodoListScreen` already renders distinct Overdue, Today, Upcoming and No date buckets, and `TodoRow` labels due dates in each mode. Existing list filters and mutation/Undo routing are unchanged.
- **R3:** Today groups current-day Tasks, date-aware Habits and privacy-gated Focus under a shared section heading. Historical dates keep their date notice, date-scoped Habits and Focus history; TodayRoute still omits the live Today task projection. Existing focus authorization, family confirmation, retry and Undo callbacks are unchanged.
- **QA-01 mapping:** `todayOverflowCountsOnlyLoadedMatchingRows` → TodayTasksStateTest/TodayTasksFlowTest; `pastDayKeepsDatedHabitsAndOffersReturn` → TodayScreenTest; `familyCompletionRemainsUndoable` → TodayTasksFlowTest and family-action coverage. These tests and runtime cases remain NOT RUN for IP-Q01.
- **Test phase:** Tests were not authored or run during implementation, per the shared delivery workflow. The user-requested `./gradlew check` ran after implementation; QA-01 remains NOT RUN and is not replaced by this check.
- **Known uncertainty:** Locale rendering, dynamic 57→7→0 updates, midnight/device-zone transitions and rendered accessibility remain for IP-Q01.

**Source patch SHA-256:** `4609c629af0338e8e7a8b7fb9c0386ba8e49b18e731628ff8bdd14a3f1ad7158` (app source/resources only; handoff documents excluded).

**Static check:** `git diff --check` passed with exit code 0.

**Gradle check:** `./gradlew check` FAILED (exit 1, 3m02s). `:core:data:compileDebugUnitTestKotlin` and `:core:data:compileReleaseUnitTestKotlin` failed because `FocusMetadataArchiveAdapterTest.kt:120, :123 and :125` omit required `taskId` and `taskOrigin` arguments. No IP-I01 test ran. IP-Q01 remains NOT RUN.

**Suggested commit message:** `feat(today): add action hub and task overflow count`.
