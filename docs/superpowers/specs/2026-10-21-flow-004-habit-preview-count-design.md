# FLOW-004 — Show the scheduled habit count in Today’s preview

**Status:** Implementation complete; focused runtime verification pending.
**Date:** 21 October 2026
**Source:** [App flow and UI/UX review](../../APP_FLOW_UX_REVIEW_2026-10-21.md), Additional live observation 5; visual direction 5.
**Priority:** P2
**Plan:** [Implementation plan](../plans/2026-10-21-flow-004-habit-preview-count.md)
**Index:** [Task index and integration order](../../APP_FLOW_UX_REVIEW_2026-10-21-PLANS.md)

## Intent and success

Help a user discover scheduled habits beyond Today’s two-row preview without expanding the whole card. Success means the five acceptance scenarios below hold while the existing data and action guarantees remain intact.

## Evidence and current behavior

TodayHabitsCard.kt currently calls splitHabitRows(rows).scheduled.take(2) twice and shows generic “View all”. The existing HabitCaptureFlowTest still expects a cap of three; that expectation predates the local two-row edit and must be reconciled during implementation.

The review contains a light emulator walkthrough, not a reproduction on the checked-out binary. This document does not claim new runtime verification or implementation.

## Dependencies

None. Preserve the existing local two-row preview change; FLOW-005 uses this header.

## Global constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies.
- Localize changed copy in EN/FR/FA with matching resource names and numbered placeholders; preserve RTL and Gregorian storage/display policy.
- Keep semantic light/dark colors, interactive targets of at least 48 dp, and reachable content at 100%/130%/200% text in compact portrait and short landscape.
- Preserve private offline storage, consent boundaries, stable record/command IDs, original retry targets, accepted drafts, and Undo protections.
- Preserve unrelated edits, including the existing two-row limit and 4 dp internal gap in TodayHabitsCard.kt.
- Implement only this task and its declared prerequisites; no database migration, new provider permission, or dependency upgrade is included.

## Behavior and design decisions

1. Retain the two scheduled-row limit and current row ordering. Use the same visible ID set for preview rendering and off-preview retained action handling.
2. For successfully loaded, current-date data with at least one scheduled row, show “View all (%1$s)” using the total scheduled count, not the total habit catalog or current visible-row count.
3. Use the original generic View all while loading, on a load error, or with zero scheduled rows; do not show a stale count from another date. Keep the existing empty/no-scheduled distinction.
4. The button’s accessible label additionally names that the count is scheduled for the displayed date. The existing onViewAll callback remains the only navigation behavior.
5. Keep pending actions for hidden scheduled rows, unscheduled rows, and other dates visible in the retained-actions region. Count copy must not alter write/undo behavior.

## Interfaces and data ownership

Keep TodayHabitsCard’s public signature. Introduce internal const val TODAY_HABIT_PREVIEW_LIMIT = 2 in TodayHabitsCard.kt, compute scheduled once, and reuse scheduled.take(TODAY_HABIT_PREVIEW_LIMIT) for rendering and visible IDs. Add habit_view_all_count (%1$s) and habit_view_all_scheduled_accessibility (%1$s count, %2$s date) in EN/FR/FA. Preserve habits_view_all and existing retained-action tags.

Only paths explicitly marked Create are new. Existing callbacks, field identities, and state owners remain the contract unless this section explicitly changes them. No stored record data is transformed by presentation changes.

## Acceptance scenarios

| ID | Case | Input/action | Required result |
|---|---|---|---|
| FLOW-004-AC-1 | `scheduledCountIsTotal` | Use five scheduled habits in a loaded Today fixture. | Two rows show and the View all label names five scheduled habits. |
| FLOW-004-AC-2 | `unscheduledAreExcluded` | Mix older unscheduled and scheduled habits. | Only scheduled rows and their count contribute to the preview. |
| FLOW-004-AC-3 | `genericLabelDuringLoading` | Load another selected date or show its load error. | No previous-date count appears as current. |
| FLOW-004-AC-4 | `hiddenActionsRemainReachable` | Retain a pending action for the third scheduled habit. | It stays in retained actions even though that habit is outside the preview. |
| FLOW-004-AC-5 | `viewAllUsesExistingRoute` | Activate the counted View all action. | The existing full Habits destination opens with the selected date context. |

## Scope boundary

No third preview row, habit sorting change, catalog count substitution, new navigation destination, pending-action truncation, or change to the existing 4 dp card gap.

## Completion evidence

Use the matching plan's focused verification. A screenshot demonstrates appearance for its recorded build/configuration, not every device or assistive-technology state. Document unavailable checks as pending. No app tests, build, or implementation were performed while preparing this spec.
