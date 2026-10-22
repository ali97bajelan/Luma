# T32 — Remaining routes rollout coordinator specification

Date: 2026-10-22  
Status: Proposed / pending implementation. This document is a planning artifact, not release acceptance.  
Depends on: T30; complete T32-A through T32-D before T33

## Intent and sources

Replace the old catch-all remaining-screen task with four independently reviewable screen-family tasks and a route coverage ledger.

References: Timeline/Review pairs directly; other routes extrapolate T30. Read the [shared reference brief](../../luma-redesign/README.md) and [task index](../../UI_REDESIGN_TASKS.md) with this specification. The brief resolves inconsistencies and provides common tokens; it is normative for every task.

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

- T32 is a coordination task, not permission to redesign unrelated behavior. T32-A owns Timeline, T32-B Review, T32-C supporting browse/settings surfaces and T32-D capture/detail/modal surfaces.
- Create `docs/luma-redesign/route-coverage.md` during implementation from every registered route in LifeOsApp.kt, including optional integration/widget routes when their providers exist. For each row record owner, light/dark evidence, locale/width checks and unresolved blockers.
- Do not claim More, Settings or editor mockups were supplied: none are in this reference folder. Use shared visual extrapolation and preserve current information architecture.
- Coordinate EventCard ownership: T32-A changes the shared content card; T31 and T32-C/D consume it and verify context-specific actions. Avoid separate card forks.
- Existing 2026-10-21 fresh-refresh specs remain the behavior baseline where implemented. This redesign supersedes their visual choices only where explicitly stated; preserve their draft, feedback, disclosure and state protections.

## Implementation boundaries

- `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/navigation/AppDestinations.kt` — existing integration surface.

Existing public/internal composable signatures remain compatible except additions explicitly named above. Existing repositories/view models own data and mutations; decorative components receive values and callbacks. New files named in the contract are proposed implementation files, not files already created by this documentation change. Consult the paired plan for test ownership.

## Acceptance scenarios

1. Every route and major sheet/dialog has one implementation owner and an evidence row; absent providers are recorded rather than silently omitted.
2. T32-A/B/C/D acceptance is verified individually; no legacy hard-coded screen palette remains except explicitly documented platform/launcher surfaces.
3. Optional feature omissions appear as intentional differences, not failed visual requirements.

## Verification and completion

Use focused existing regression coverage plus visual/manual checks for the changed surfaces. Add tests for changed behavior and meaningful state/semantics risks; do not add tests that merely assert padding or duplicate token assignments. Required font/contrast/RTL/inset checks are acceptance work, not a reason to add fake data. Record PASS/FAIL/NOT RUN and evidence in the T33 ledger. No checks are claimed to have run in this specification.

[Implementation plan](../plans/2026-10-22-t32-remaining-routes.md)
