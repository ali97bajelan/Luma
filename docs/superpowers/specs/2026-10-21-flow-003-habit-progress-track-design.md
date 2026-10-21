# FLOW-003 — Make habit progress visually legible

**Status:** Implementation complete on 21 October 2026; static source/palette review recorded in the plan. Build, emulator, and visual acceptance verification remain pending at the user's request.
**Date:** 21 October 2026
**Source:** [App flow and UI/UX review](../../APP_FLOW_UX_REVIEW_2026-10-21.md), Additional live observation 4; visual directions 3 and 5.
**Priority:** P2
**Plan:** [Implementation plan](../plans/2026-10-21-flow-003-habit-progress-track.md)
**Index:** [Task index and integration order](../../APP_FLOW_UX_REVIEW_2026-10-21-PLANS.md)

## Intent and success

Make a habit’s progress look like progress at zero and at partial completion while keeping the exact numeric count readable. Success means the five acceptance scenarios below hold while the existing data and action guarantees remain intact.

## Evidence and current behavior

HabitProgress.kt already renders a full-width 4 dp Material 3 LinearProgressIndicator and a numeric description. The isolated dot in the emulator may be a stop marker on a low-contrast track or a different installed build. Verify the matching build before treating this as a missing progress component.

The review contains a light emulator walkthrough, not a reproduction on the checked-out binary. This document does not claim new runtime verification or implementation.

## Dependencies

None. FLOW-005 consumes the resulting compact progress presentation.

## Global constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies.
- Localize changed copy in EN/FR/FA with matching resource names and numbered placeholders; preserve RTL and Gregorian storage/display policy.
- Keep semantic light/dark colors, interactive targets of at least 48 dp, and reachable content at 100%/130%/200% text in compact portrait and short landscape.
- Preserve private offline storage, consent boundaries, stable record/command IDs, original retry targets, accepted drafts, and Undo protections.
- Preserve unrelated edits, including the existing two-row limit and 4 dp internal gap in TodayHabitsCard.kt.
- Implement only this task and its declared prerequisites; no database migration, new provider permission, or dependency upgrade is included.

## Behavior and design decisions

1. Retain the existing numeric count/target/unit and habitProgressFraction policy. A count over target remains exact in text while the track stays clamped to 1.
2. Make the complete 4 dp track visible at zero progress. Set explicit semantic colors: primary for filled progress and outline for the unfilled track; check at least 3:1 contrast against the card in both existing palettes.
3. An end/stop marker may remain only when the full track is also visible. If the matching build already has a visible full track, record that finding and make only the verified color/presentation adjustment.
4. Keep the existing full-width layout and accessible count description. Expose one determinate progress range using ProgressBarRangeInfo on the indicator; avoid a second conflicting numeric announcement on an ancestor.
5. Do not shorten or hide numeric progress to meet density goals. A long unit may use its existing ellipsis in the row while the accessible description keeps the full unit.

## Interfaces and data ownership

Keep internal HabitProgressContent(day: HabitDay) and habitProgressFraction(count: Int, target: Int): Float. Add testTag("habit_progress_track_${day.habit.id}") to the existing indicator. Configure its supported color and trackColor parameters explicitly; do not require a new Material dependency or introduce a custom drawing implementation. Preserve parent habit_progress_<id> identity and count description.

Only paths explicitly marked Create are new. Existing callbacks, field identities, and state owners remain the contract unless this section explicitly changes them. No stored record data is transformed by presentation changes.

## Acceptance scenarios

| ID | Case | Input/action | Required result |
|---|---|---|---|
| FLOW-003-AC-1 | `zeroStillHasATrack` | Inspect an ordinary 0/8 habit. | A full horizontal track is visible, rather than only an isolated dot. |
| FLOW-003-AC-2 | `partialMatchesCount` | Inspect the demo Drink water value 1/8. | The numeric count and visible filled fraction agree. |
| FLOW-003-AC-3 | `completionKeepsExactCount` | Inspect a complete habit fixture. | The track is full and numeric count/target remain readable. |
| FLOW-003-AC-4 | `bothPalettesAreLegible` | Inspect the same fixture under semantic light and dark palettes. | Track/card contrast meets 3:1 and progress does not rely on color alone. |
| FLOW-003-AC-5 | `progressAndActionsStayDistinct` | Open/log the same habit once and inspect semantics. | Progress range, readable count, row-open action, and +1 action retain distinct meaning. |

## Scope boundary

No new tracker type, animated celebration, streak pressure, target calculation, progress rounding, Material upgrade, or custom chart framework.

## Completion evidence

The matching plan records implementation and static source/palette evidence. No Gradle command, app test, or emulator check was run for this implementation, as requested. Matching-build reproduction and visual/assistive-technology acceptance remain pending. A screenshot, when available, demonstrates appearance for its recorded build/configuration, not every device or assistive-technology state.
