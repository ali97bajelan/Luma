# FLOW-005 — Refine Today’s information hierarchy

**Status:** Scoped implementation complete on 21 October 2026. Prerequisite integration and build, emulator, and visual verification remain pending; no Gradle or emulator tests were run at the user's request.
**Date:** 21 October 2026
**Source:** [App flow and UI/UX review](../../APP_FLOW_UX_REVIEW_2026-10-21.md), Visual direction 2; integrates live observations 1, 2, 4, and 5.
**Priority:** P2
**Plan:** [Implementation plan](../plans/2026-10-21-flow-005-today-information-hierarchy.md)
**Index:** [Task index and integration order](../../APP_FLOW_UX_REVIEW_2026-10-21-PLANS.md)

## Intent and success

Give Today a stable first glance for capture and daily actions, with personal entries before the collapsed daily summary. Success means the five acceptance scenarios below hold while the existing data and action guarantees remain intact.

## Evidence and current behavior

At review time, TodayScreen already used stable lazy keys in this order: header, capture, habits, tasks, recent entries, daily summary. TodayCaptureActions inserted an empty-entry sentence above its three shortcuts, moving those shortcuts when the first entry was saved. The implementation refines that existing structure rather than inventing a new dashboard.

The review contains a light emulator walkthrough, not a reproduction on the checked-out binary. FLOW-005 now removes entry-state ownership from capture actions, uses equal-priority tonal capture buttons, and renders the empty sentence after Recent entries. No new runtime verification is claimed.

## Dependencies

Requires UX-021 and FLOW-001–FLOW-004. Keep UX-022 headings and UX-020 action labels when present.

## Global constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies.
- Localize changed copy in EN/FR/FA with matching resource names and numbered placeholders; preserve RTL and Gregorian storage/display policy.
- Keep semantic light/dark colors, interactive targets of at least 48 dp, and reachable content at 100%/130%/200% text in compact portrait and short landscape.
- Preserve private offline storage, consent boundaries, stable record/command IDs, original retry targets, accepted drafts, and Undo protections.
- Preserve unrelated edits, including the existing two-row limit and 4 dp internal gap in TodayHabitsCard.kt.
- Implement only this task and its declared prerequisites; no database migration, new provider permission, or dependency upgrade is included.

## Behavior and design decisions

1. Keep the existing section order and stable lazy keys. Use the shared compact header/gutter from UX-021, a 12 dp default inter-item gap, and 4 dp metadata gaps. Do not add nested scrolling to Today.
2. Keep Mood, Journal, and Memory as equally prominent one-tap choices in one compact FlowRow, each at least 48 dp tall. Use FilledTonalButton with the existing semantic palette to reduce repeated outlines; do not assume one capture type is more important.
3. Interpret the review’s primary-capture suggestion as one coherent capture group. A single generic Capture button was considered but adds a choice step; this draft keeps the established direct actions and date-choice contract.
4. Move today_no_entries out of TodayCaptureActions and render it under Recent entries only when TodayUiState.Empty. Keep the today_empty test tag. Capture controls then keep the same position for empty and populated entry states.
5. Use the compact habit/task previews delivered by FLOW-002–FLOW-004. Recent personal entry previews remain before the collapsed saved daily summary. Loading/errors/recovery may legitimately increase page length; never hide them to hit a viewport target.

## Interfaces and data ownership

Change internal TodayCaptureActions to TodayCaptureActions(onDateCapture: (EventType) -> Unit): Unit by removing its empty parameter. TodayScreen owns the existing empty sentence in the Empty branch. Retain all capture test tags, requestCapture logic, onCapture/onHabit/onTask callbacks, current-day-only task behavior, and the lazy section keys. No new screen state or repository interface is required.

Only paths explicitly marked Create are new. Existing callbacks, field identities, and state owners remain the contract unless this section explicitly changes them. No stored record data is transformed by presentation changes.

## Acceptance scenarios

| ID | Case | Input/action | Required result |
|---|---|---|---|
| FLOW-005-AC-1 | `capturePositionIsStable` | Compare empty and populated Today fixtures with identical habit/task state. | Header and capture group occupy the same position; empty copy belongs to Recent entries. |
| FLOW-005-AC-2 | `oneTapCaptureStillWorks` | Activate each direct capture choice once in the fixture. | The existing type callback and selected-date choice are preserved. |
| FLOW-005-AC-3 | `sectionOrderIsPredictable` | Inspect the five-record populated fixture. | Capture, habits, tasks, recent entries, and summary follow the specified order. |
| FLOW-005-AC-4 | `summaryDoesNotBuryEntries` | Open ordinary Today content. | At least the first recent-entry preview precedes the collapsed daily summary; the existing viewport acceptance remains valid. |
| FLOW-005-AC-5 | `recoveryAndAdaptationAreRetained` | Inspect existing recovery content and a wrapping capture row. | Feedback is not hidden, targets remain at least 48 dp, and labels wrap without reducing type size. |

## Scope boundary

No new primary tab, generic capture modal, prioritized mood choice, summary algorithm, event count policy, task query, habit schedule, or artificial fixed-height dashboard card.

## Completion evidence

Use the matching plan's focused verification. The scoped implementation preserves the existing section keys, date-choice callbacks, 12 dp section gaps, recovery content, and localized copy. UX-021 and FLOW-001/002/004 are not implemented in this change; their shared header/gutter, explicit selected-date context, task-empty refinement, and counted habit preview remain pending integration. FLOW-003's progress presentation is retained. A screenshot demonstrates appearance for its recorded build/configuration, not every device or assistive-technology state. No app tests, Gradle commands, emulator walkthrough, or visual verification were run for this implementation; the plan records static evidence and pending acceptance checks.
