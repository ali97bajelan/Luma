# O08 — Habit shortcut in Quick Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan does not request subagent delegation.

**Goal:** Offer the corrected Timeline reference’s habit intent through an optional Log a habit action that opens existing saved-habit selection.

**Architecture:** QuickCaptureSheet emits a date-aware optional callback; LifeOsApp owns dismissal and existing habit navigation/tracking-zone resolution. Existing Habits screens own selection, logging and recovery; selecting the shortcut never writes a log.

**Tech Stack:** Kotlin, Compose Material 3, Navigation Compose, existing Compose instrumentation and Gradle/JDK 17.

**Spec:** [O08 specification](../specs/2026-10-22-o08-optional-capture-habit-shortcut-design.md).

## Global constraints

- Native Android, Kotlin, Compose Material 3 and Navigation Compose; keep minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Offline operation; no account, cloud sync, AI service, telemetry, new permissions or schema migration in required visual work.
- System/Light/Dark selection and English/French/Persian support remain available offline.
- Preserve existing route IDs, callbacks, saved state, data semantics, loading/error/retry/undo and accessibility tags unless an optional task explicitly changes its own contract.
- Use actual saved data; never ship reference people, dates, percentages, measurements, quotes or charts as user records.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL and system animation scale.
- Optional tasks default to deferred and are not required for redesign acceptance. No optional feature is authorized for implementation by this planning document.

## Review focus

- Device and tracking-zone dates differ: Now resolves the habit date in the tracking zone.
- Historical date selected: callback retains that exact date and existing logging restrictions.
- Zone/rows unavailable: use existing initialization/retry rather than invent data or dates.
- Repeated taps or dismissal: at most one navigation, no implicit write.
- Long French/Persian and 2.0 text: choices and Close remain reachable with a named focus target.

## Files and interfaces

- Modify `app/src/main/java/com/luma/lifeos/ui/QuickCaptureSheet.kt`: append `onHabit: ((LocalDate?) -> Unit)? = null` and add `capture_habit`.
- Modify `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`: consume the nullable date, resolve Now in the actual tracking zone, dismiss capture and use existing Habits route wiring.
- Modify the three existing localized string catalogs for “Log a habit”.
- Extend `app/src/androidTest/java/com/luma/lifeos/CaptureAccessibilityTest.kt`, `DayNavigationTest.kt` and `habits/HabitsNavigationTest.kt` under the same test package root.

### Task 1: Optional choice and date-aware navigation

- [ ] Add a focused sheet callback scenario asserting omitted callback hides only Habit; provided callback receives null for Now or the exact selected LocalDate once. Assert all existing capture callbacks remain unchanged.
- [ ] Add a navigation fixture with differing device/tracking dates and a historical date. Assert opening Habits writes no log, Now uses tracking-zone date and history retains the chosen date.
- [ ] Run the new assertions before production changes; confirm failure comes from the missing action/wiring.
- [ ] Append the optional callback and full-width choice, wire it through LifeOsApp using existing habit date/initialization/navigation paths, and add all three translations. Do not create sample habits or replace existing record/task actions.
- [ ] Run focused tests, including empty/error habit data, rotation/dismissal and repeated taps. Inspect both themes at 320 dp/2.0 French/Persian and exercise the five-tab base; repeat four-tab entry only if O01 is selected.
- [ ] Review the spec’s four acceptance scenarios, record evidence for T33, and commit the reviewed implementation/tests only when executing this selected option.

## Planned verification

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.CaptureAccessibilityTest,com.luma.lifeos.DayNavigationTest,com.luma.lifeos.habits.HabitsNavigationTest
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: BUILD SUCCESSFUL, tests actually executed, zero failures/lint errors. Missing prerequisites or skipped device checks are NOT RUN with reasons. These commands were not run for this documentation update.

## Handoff

Deferred until explicitly selected, after T32-D and T32-A. Review the spec/plan before implementation and send resulting evidence to T33.
