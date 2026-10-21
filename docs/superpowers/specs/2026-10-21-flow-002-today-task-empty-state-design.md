# FLOW-002 — Remove duplicate empty-task messaging

**Status:** Implementation complete; focused runtime verification pending.
**Date:** 21 October 2026
**Source:** [App flow and UI/UX review](../../APP_FLOW_UX_REVIEW_2026-10-21.md), Additional live observation 2; visual direction 7.
**Priority:** P2
**Plan:** [Implementation plan](../plans/2026-10-21-flow-002-today-task-empty-state.md)
**Index:** [Task index and integration order](../../APP_FLOW_UX_REVIEW_2026-10-21-PLANS.md)

## Intent and success

Let a user understand an empty Today task preview with one status sentence and one Add task action. Success means the five acceptance scenarios below hold while the existing data and action guarantees remain intact.

## Evidence and current behavior

TodayTasksCard.kt always renders todo_today_count for Data and then todo_today_empty when tasks is empty. The walkthrough displayed both zero-count and empty sentences. Recovery summaries are rendered independently below the preview and must remain available.

The review contains a light emulator walkthrough, not a reproduction on the checked-out binary. This document does not claim new runtime verification or implementation.

## Dependencies

None. Coordinate TodayTasksCard.kt changes with FLOW-005 after this task lands.

## Global constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies.
- Localize changed copy in EN/FR/FA with matching resource names and numbered placeholders; preserve RTL and Gregorian storage/display policy.
- Keep semantic light/dark colors, interactive targets of at least 48 dp, and reachable content at 100%/130%/200% text in compact portrait and short landscape.
- Preserve private offline storage, consent boundaries, stable record/command IDs, original retry targets, accepted drafts, and Undo protections.
- Preserve unrelated edits, including the existing two-row limit and 4 dp internal gap in TodayHabitsCard.kt.
- Implement only this task and its declared prerequisites; no database migration, new provider permission, or dependency upgrade is included.

## Behavior and design decisions

1. For Data(total = 0, tasks = emptyList()), render the existing todo_today_empty message once and suppress today_tasks_count. Retain one today_tasks_add action and the existing View all navigation.
2. For a positive total, keep the localized total count even when the preview contains only a subset. Preserve existing task ordering and preview selection.
3. If total is positive but the visible list is temporarily empty, keep the positive count and View all; do not claim there are no open tasks.
4. Loading, error, pending mutation, retained recovery, conflict, and missing-task states stay distinct. An empty preview never hides an off-preview recovery action.
5. Reuse the existing localized empty sentence and action labels. No new task, date, or provider data is created to improve appearance.

## Interfaces and data ownership

Preserve TodayTasksCard’s signature and TodayTasksState.Data. Change only rendering guards: show count when state.total > 0 and show empty copy only when state.total == 0 && state.tasks.isEmpty(). Keep today_tasks_empty, today_tasks_add, today_tasks_all, and recovery tags; today_tasks_count exists only for a positive total.

Only paths explicitly marked Create are new. Existing callbacks, field identities, and state owners remain the contract unless this section explicitly changes them. No stored record data is transformed by presentation changes.

## Acceptance scenarios

| ID | Case | Input/action | Required result |
|---|---|---|---|
| FLOW-002-AC-1 | `oneEmptyMessage` | Render the ordinary zero-task Data state. | One empty status is shown; no redundant zero count is present. |
| FLOW-002-AC-2 | `oneAddAction` | Open the empty Today preview. | Exactly one Add task action is present in the card and opens the existing editor. |
| FLOW-002-AC-3 | `positiveCountIsPreserved` | Use a populated preview with more eligible tasks than visible rows. | The count remains the full eligible total. |
| FLOW-002-AC-4 | `positiveTotalIsNotCalledEmpty` | Render a positive total with no currently visible rows. | The positive count and View all remain; zero-task copy is absent. |
| FLOW-002-AC-5 | `recoveryIsIndependent` | Render a zero-task preview with retained task feedback. | Feedback stays available and its existing callback identity is unchanged. |

## Scope boundary

No task query change, change to what “due today or earlier” means, completion policy, task preview limit, or recovery redesign.

## Completion evidence

Use the matching plan's focused verification. A screenshot demonstrates appearance for its recorded build/configuration, not every device or assistive-technology state. Document unavailable checks as pending. No app tests, build, or implementation were performed while preparing this spec.
