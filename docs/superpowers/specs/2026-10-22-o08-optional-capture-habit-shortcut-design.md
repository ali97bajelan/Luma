# O08 — Optional — habit shortcut in Quick Capture specification

Date: 2026-10-22  
Status: Optional / deferred; not selected for implementation.  
Depends on: T32-D and T32-A

## Intent and sources

The corrected `timeline-light.html` contains a hidden Capture Moment sheet with Water, Stretch and Thought choices. Add a reusable entry into the existing habit selection/logging flow, without hard-coded habit names or writes on selection. Thought uses the existing Journal action; Mood, Memory and Task remain available.

Read the [shared reference brief](../../luma-redesign/README.md) and [task index](../../UI_REDESIGN_TASKS.md). Required capture-sheet styling remains T32-D; this option changes available actions.

## Global constraints

- Native Android, Kotlin, Compose Material 3 and Navigation Compose; keep minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Offline operation; no account, cloud sync, AI service, telemetry, new permissions or schema migration in required visual work.
- System/Light/Dark selection and English/French/Persian support remain available offline.
- Preserve existing route IDs, callbacks, saved state, data semantics, loading/error/retry/undo and accessibility tags unless an optional task explicitly changes its own contract.
- Use actual saved data; never ship reference people, dates, percentages, measurements, quotes or charts as user records.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL and system animation scale.
- Optional tasks default to deferred and are not required for redesign acceptance. No optional feature is authorized for implementation by this planning document.

## Behavior and interface contract

- Append `onHabit: ((LocalDate?) -> Unit)? = null` after `onTask` in `QuickCaptureSheet`. Null omits the action, preserving every current caller. Use a localized full-width “Log a habit” choice with `capture_habit` tag and at least 48 dp target; do not replace existing record/task choices with the reference’s three-column grid.
- Call `onHabit(captureDate)` once using the existing Now/selected-date policy. In LifeOsApp, dismiss the chooser and open the existing Habits destination. For a non-null selected date pass that LocalDate unchanged; for Now resolve the current date in the stored habit tracking zone at click time. If the zone is unavailable, use the existing Habits initialization/loading flow rather than guess a device-zone habit date. Keep all pre-existing capture paths unchanged.
- Opening Habits performs no log write. Users select their actual saved habit and explicitly use its existing log action, including disabled/pending/undo/retry rules. Empty/loading/error Habits states retain their current create/retry behavior.
- No Water or Stretch records are seeded, no name-based habit matching is introduced, and no hydration volume, direct one-tap mutation, custom favorite setting or new event type is added. A future per-habit direct shortcut would need its own product rules and is outside this option.
- Rotation preserves the selected capture date. Close/Back/outside dismissal writes nothing. Returning from Habits follows the existing navigation behavior (including O01 only if independently selected). Remove the mock “Encrypted automatically” claim in required work regardless of this option.

## Files

- Modify `app/src/main/java/com/luma/lifeos/ui/QuickCaptureSheet.kt`: optional callback and named choice.
- Modify `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`: pass the callback using existing habit date/navigation wiring.
- Reuse existing habit tracking-zone state and `openHabitsDate`; no repository/schema/permission change.
- Add aligned copy in `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` and `values-fa/strings.xml`.

## Acceptance scenarios

1. Callback absent leaves current choices untouched. Callback present shows one named habit action; choosing it emits the selected date once and saves no record.
2. With Now selected and device/tracking dates different, Habits opens on the current tracking-zone day; selected historical date remains explicit and unchanged.
3. Empty/failed habit loading retains create/retry; existing log/undo works only after explicit user action in Habits. Dismissing or rotating the chooser creates nothing.
4. French/Persian at 320 dp/2.0 keeps the habit action and all existing capture choices/Close accessible. Optional O01 does not break navigation into Habits.

## Verification

Extend existing CaptureAccessibilityTest, DayNavigationTest and HabitsNavigationTest with callback/date/no-write assertions; inspect sheet overflow, focus and dismissal in both themes. Record evidence through T33. No implementation or device acceptance is claimed by this spec.

[Implementation plan](../plans/2026-10-22-o08-optional-capture-habit-shortcut.md)
