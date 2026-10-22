# T31 — Today visual hierarchy specification

Date: 2026-10-22  
Status: Proposed / pending implementation. This document is a planning artifact, not release acceptance.  
Depends on: T30

## Intent and sources

Make date context, capture, habit progress, tasks and recent saved records scan clearly while preserving the bounded Today previews.

References: today-light.png + today-light.html; today-dark.png + today-dark.html. Read the [shared reference brief](../../luma-redesign/README.md) and [task index](../../UI_REDESIGN_TASKS.md) with this specification. The brief resolves inconsistencies and provides common tokens; it is normative for every task.

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

- Order: title/date/current-day notice; habit tracking-zone context; existing Mood/Journal/Memory capture choices; scheduled habit preview; task preview; recent entries and saved daily summary. Tracking-zone text must come from habit state, not the device zone or the sample Asia/Tehran.
- Retain two-habit preview limit, full scheduled count and View all/Create actions. Show count, target/unit, rounded progress track, status and a separate +1 target. Use sage and ember as decorative variants, not a new category or per-habit setting. Stable row identity must not derive from translated names.
- Keep `habitProgressFraction(count: Int, target: Int): Float` and confirmed-write animation semantics: numeric/accessible count is exact, fill clamps to 0..1, pending/failed writes do not advance, first observation and changed date do not animate from stale values.
- Keep the existing three-item due/overdue task preview and its full eligible count. Group only the returned visible tasks by existing due-date classification, preserving order. No completed tab or undated query in required work; the reference full panel is O03.
- Retain historical-date capture confirmation, task-preview visibility only on device-today, tracking/device-date differences, load guards and off-preview retry/undo controls. Existing View all routes remain the path to more items.
- Style saved activity/source/unit/timestamp and summary content using actual values, including unavailable states. Retain summary detail/disclosure behavior; do not invent a new summary destination. Reflect is O07.

## Implementation boundaries

- `app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/today/TodayRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/today/TodayCaptureActions.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/habits/TodayHabitsCard.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/habits/HabitsScreen.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/habits/HabitProgress.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/todo/TodayTasksCard.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/todo/TodoRow.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/summary/DailySummaryCard.kt` — existing integration surface.

Existing public/internal composable signatures remain compatible except additions explicitly named above. Existing repositories/view models own data and mutations; decorative components receive values and callbacks. New files named in the contract are proposed implementation files, not files already created by this documentation change. Consult the paired plan for test ownership.

## Acceptance scenarios

1. Two fixture habits (2/8 and 1/5) show exact 25% and 20% progress with separate open/log actions; pending, failure and undo preserve stored counts.
2. More than two habits/three eligible tasks retains full totals and reachable View all/recovery actions; no completed or undated task appears in the base Today query.
3. Navigating to a historical date shows explicit context and correct capture choices, hides Today tasks and never displays the previous date’s habit rows beneath a new date.
4. Empty/loading/error/missing-summary/integration-disabled states remain useful and all existing callbacks work in both themes and RTL.

## Verification and completion

Use focused existing regression coverage plus visual/manual checks for the changed surfaces. Add tests for changed behavior and meaningful state/semantics risks; do not add tests that merely assert padding or duplicate token assignments. Required font/contrast/RTL/inset checks are acceptance work, not a reason to add fake data. Record PASS/FAIL/NOT RUN and evidence in the T33 ledger. No checks are claimed to have run in this specification.

[Implementation plan](../plans/2026-10-22-t31-today-redesign.md)
