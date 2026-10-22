# T32-B — Weekly Review modules and comparisons specification

Date: 2026-10-22  
Status: Proposed / pending implementation. This document is a planning artifact, not release acceptance.  
Depends on: T30

## Intent and sources

Apply the editorial Review layout to existing saved metrics, comparison periods, source links and section navigation.

References: review-light.png + review-light.html; review-dark.png + review-dark.html. Read the [shared reference brief](../../luma-redesign/README.md) and [task index](../../UI_REDESIGN_TASKS.md) with this specification. The brief resolves inconsistencies and provides common tokens; it is normative for every task.

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

- Use a rounded selected-period container, explicit partial-week badge and comparable previous-period caption. Keep saved/restored week, current comparison calculation and all section anchors.
- Overview leads with personal entry/log/habit summary and mood check-in, followed by saved signals and latest memory/archive context. Existing overview order personal→memory→signals remains the base behavior; use visual grouping rather than moving content solely to reproduce screenshot order.
- Restyle detailed entry-count, mood, memory, people/place, usage, activity and habit sections as the same family. Do not remove a section because its shortcut is off-screen in the screenshot; retain Sections disclosure and sticky period/Overview actions.
- Use existing repetition/scheduled-day metrics and per-habit `habitWeekSlots` for cadence. Do not divide repetitions by scheduled-days and call it a completion rate. New aggregate cadence, active-day/streak/coaching calculations are O06.
- Values, units, previous/current source links, explanations, missing/recorded-zero/partial-coverage distinctions and numeric precision remain intact. A negative people/place delta is descriptive, not automatically a warning or adverse-health assessment.
- No fabricated sparkline, equal-width bar implying a quantity, rounded false total or synthetic daily sample. O05 owns charts. Preserve existing daily usage evidence and setup disclosure. No data-free +Link/+Tag control; link via existing editors and source routes.

## Implementation boundaries

- `app/src/main/java/com/luma/lifeos/ui/review/WeeklyReviewRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/review/ReviewHeader.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/review/ReviewSections.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/review/ReviewOverview.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/review/ReviewOverviewSignals.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/review/ReviewOverviewMemory.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/review/ReviewValues.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/review/ReviewSignals.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/review/ReviewHabits.kt` — existing integration surface.

Existing public/internal composable signatures remain compatible except additions explicitly named above. Existing repositories/view models own data and mutations; decorative components receive values and callbacks. New files named in the contract are proposed implementation files, not files already created by this documentation change. Consult the paired plan for test ownership.

## Acceptance scenarios

1. Partial and complete weeks retain their original ranges and comparison calculations, and section selection/restoration still works.
2. No-data, recorded zero, fractional activity and incomplete usage fixtures retain their exact meaning, units and explanations in both appearances.
3. All current/previous source links, View habits, memory open/retry, mood check-in and setup links reach the original routes.
4. Base UI contains no “100% streak”, fabricated active-day badge, invented chart or unsupported pinned-memory model.

## Verification and completion

Use focused existing regression coverage plus visual/manual checks for the changed surfaces. Add tests for changed behavior and meaningful state/semantics risks; do not add tests that merely assert padding or duplicate token assignments. Required font/contrast/RTL/inset checks are acceptance work, not a reason to add fake data. Record PASS/FAIL/NOT RUN and evidence in the T33 ledger. No checks are claimed to have run in this specification.

[Implementation plan](../plans/2026-10-22-t32-b-review-redesign.md)
