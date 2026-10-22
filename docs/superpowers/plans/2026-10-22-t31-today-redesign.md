# T31 — Today visual hierarchy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan does not request subagent delegation.

**Goal:** Make date context, capture, habit progress, tasks and recent saved records scan clearly while preserving the bounded Today previews.

**Architecture:** Existing repositories/view models retain data ownership and LifeOsApp retains navigation. Shared T30 roles/components provide presentation; the spec defines any optional new interface. Keep current product behavior.

**Tech Stack:** Kotlin, Compose Material 3, Navigation Compose, JUnit/Compose instrumentation, Gradle and JDK 17.

**Spec:** [Corresponding specification](../specs/2026-10-22-t31-today-redesign-design.md).

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

- TodayScreenTest and habit date-selection fixtures: 2/8 → 25%, 1/5 → 20%; pending/failure leaves counts; confirmed undo restores once. Historical date must not reuse prior rows.
- Inspect tracking/device-zone mismatch, two-row preview, full count, separate open/+1 actions and long units in both themes.
- TodayTasksFlowTest: four eligible rows still yield a three-row preview and full total; hidden-row conflict/retry remains reachable. TodayScreenTest retains selected-date hiding/capture behavior.
- Inspect overdue/today headings for only returned rows, Add/View all, recent activity provenance and summary absence/retry in both themes.
- Long French/Persian at 320 dp/2.0, restored context and system/IME insets must retain every action and exact value.

## Files and interfaces

- Modify: `app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/today/TodayRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/today/TodayCaptureActions.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/TodayHabitsCard.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitsScreen.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitProgress.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/todo/TodayTasksCard.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/todo/TodoRow.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/summary/DailySummaryCard.kt`.

Consumes: current signatures/state/callbacks in these files and shared T30 roles. Produces: the linked spec’s screen contract. Preserve existing composable signatures except extensions explicitly named in that spec; new named interfaces use the exact types defined there. String additions go into all three existing values/values-fr/values-fa catalogs with matching placeholders/plurals.

### Regression owners

- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/TodayScreenTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/TodayPersistenceTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/DayNavigationTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/habits/HabitDateSelectionFlowTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/todo/TodayTasksFlowTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/todo/TodayTasksSemanticsTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/summary/DailySummaryFlowTest.kt`.

### Task 1: Habit and date hierarchy

**Interfaces:** Implement this unit against the file map and contract above; no independent repository observations for decorative content. Existing state/callback contracts remain authoritative.

- [ ] Establish the baseline with existing fixtures and a before capture. For behavior/semantics additions, add the focused regression scenario before production changes: TodayScreenTest and habit date-selection fixtures: 2/8 → 25%, 1/5 → 20%; pending/failure leaves counts; confirmed undo restores once. Historical date must not reuse prior rows.
- [ ] Run the selected test; a new behavior test must fail for its intended missing behavior. Existing visual-only baseline tests should remain green; do not invent failing padding/token tests.
- [ ] Recompose Today header and TodayHabitsCard with existing state/callbacks and shared tokens; style HabitRow and HabitProgressContent without changing their interfaces or repository observations.
- [ ] Rerun the affected regression tests and inspect: Inspect tracking/device-zone mismatch, two-row preview, full count, separate open/+1 actions and long units in both themes.
- [ ] Record results/evidence for this unit and review the diff against every owned spec requirement. Commit the reviewed implementation/tests with a task-specific message when executing this plan.

### Task 2: Task, records and summary hierarchy

**Interfaces:** Implement this unit against the file map and contract above; no independent repository observations for decorative content. Existing state/callback contracts remain authoritative.

- [ ] Establish the baseline with existing fixtures and a before capture. For behavior/semantics additions, add the focused regression scenario before production changes: TodayTasksFlowTest: four eligible rows still yield a three-row preview and full total; hidden-row conflict/retry remains reachable. TodayScreenTest retains selected-date hiding/capture behavior.
- [ ] Run the selected test; a new behavior test must fail for its intended missing behavior. Existing visual-only baseline tests should remain green; do not invent failing padding/token tests.
- [ ] Style TodayTasksCard/TodoRow and Today record/summary sections. Partition only already-returned preview tasks into overdue/today headings; keep full totals, feedback and selected-date rules.
- [ ] Rerun the affected regression tests and inspect: Inspect overdue/today headings for only returned rows, Add/View all, recent activity provenance and summary absence/retry in both themes.
- [ ] Record results/evidence for this unit and review the diff against every owned spec requirement. Commit the reviewed implementation/tests with a task-specific message when executing this plan.

## Planned verification and completion

Run focused classes while iterating; extend their fixtures rather than duplicate the same state contract in a new suite. Also run any new test classes named in the spec. Commands are planned checks and have not run in this documentation change.

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.TodayScreenTest,com.luma.lifeos.TodayPersistenceTest,com.luma.lifeos.DayNavigationTest,com.luma.lifeos.habits.HabitDateSelectionFlowTest,com.luma.lifeos.todo.TodayTasksFlowTest,com.luma.lifeos.todo.TodayTasksSemanticsTest,com.luma.lifeos.summary.DailySummaryFlowTest
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: BUILD SUCCESSFUL, zero selected test failures/lint errors, actual device results for instrumentation. Missing prerequisites or skipped device tasks are NOT RUN with the exact reason. Confirm each acceptance scenario in the spec against implementation and evidence; record commands, build/device configuration and screenshots in the T33 ledger. Do not repeat passing checks unless changes/failures justify it.

## Handoff

Depends on: T30. Execute after dependency/spec review and hand results to T33. This plan does not begin app implementation.
