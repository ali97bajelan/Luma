# O01 — Optional — four-tab navigation specification

Date: 2026-10-22  
Status: Optional / deferred; requires a separate scope decision before implementation.  
Depends on: T30 and T32-C

## Intent and sources

Match the four reference tabs while keeping Habits reachable as a secondary destination.

References: All reference PNG navigation bars. Read the [shared reference brief](../../luma-redesign/README.md) and [task index](../../UI_REDESIGN_TASKS.md) with this specification. The brief resolves inconsistencies and provides common tokens; it is normative for every task.

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

- Change primary navigation to Today, Timeline, Review, More; add a clearly labeled Habits row to More and preserve Today/Review View habits entry points.
- Retain the `habits` route and old saved-back-stack resolution; do not delete its screen, widget/deep-link targets or selected-date contract.
- Return from Habits preserves the originating destination and selected date. A restored Habits destination must show a coherent navigation state with More selected and a usable Back path.
- This is a navigation behavior change, excluded from required visual acceptance. No account/profile destination is added.

## Implementation boundaries

- `app/src/main/java/com/luma/lifeos/ui/navigation/AppDestinations.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/navigation/PrimaryNavigationBar.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/more/MoreRoute.kt` — existing integration surface.

Existing public/internal composable signatures remain compatible except additions explicitly named above. Existing repositories/view models own data and mutations; decorative components receive values and callbacks. New files named in the contract are proposed implementation files, not files already created by this documentation change. Consult the paired plan for test ownership.

## Acceptance scenarios

1. Exactly four primary actions remain and Habits can be reached from More, Today, Review and existing widget links.
2. Restoring an old Habits stack and switching tabs retains dates/state and a valid Back path.

## Verification and completion

Use focused existing regression coverage plus visual/manual checks for the changed surfaces. Add tests for changed behavior and meaningful state/semantics risks; do not add tests that merely assert padding or duplicate token assignments. Required font/contrast/RTL/inset checks are acceptance work, not a reason to add fake data. Record PASS/FAIL/NOT RUN and evidence in the T33 ledger. No checks are claimed to have run in this specification.

[Implementation plan](../plans/2026-10-22-o01-optional-four-tab-navigation.md)
