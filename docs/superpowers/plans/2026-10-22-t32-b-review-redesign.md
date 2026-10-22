# T32-B — Weekly Review modules and comparisons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan does not request subagent delegation.

**Goal:** Apply the editorial Review layout to existing saved metrics, comparison periods, source links and section navigation.

**Architecture:** Existing repositories/view models retain data ownership and LifeOsApp retains navigation. Shared T30 roles/components provide presentation; the spec defines any optional new interface. Keep current product behavior.

**Tech Stack:** Kotlin, Compose Material 3, Navigation Compose, JUnit/Compose instrumentation, Gradle and JDK 17.

**Spec:** [Corresponding specification](../specs/2026-10-22-t32-b-review-redesign-design.md).

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

- WeeklyReviewFlowTest: current/previous partial-week ranges restore, every section remains reachable and methodology opens/closes.
- Inspect period badge/caption and sticky section controls with long French/Persian labels.
- ReviewOverviewTest/HabitWeekPresentationTest and ReviewUsageFlowTest: missing vs recorded zero, fractional activity, partial coverage, source IDs and per-habit future slots remain distinct.
- Inspect personal→memory→signals overview and detailed comparisons without invented streak/chart/active-day text; all current/previous sources still work.
- Long French/Persian at 320 dp/2.0, restored context and system/IME insets must retain every action and exact value.

## Files and interfaces

- Modify: `app/src/main/java/com/luma/lifeos/ui/review/WeeklyReviewRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/review/ReviewHeader.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/review/ReviewSections.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/review/ReviewOverview.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/review/ReviewOverviewSignals.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/review/ReviewOverviewMemory.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/review/ReviewValues.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/review/ReviewSignals.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/review/ReviewHabits.kt`.

Consumes: current signatures/state/callbacks in these files and shared T30 roles. Produces: the linked spec’s screen contract. Preserve existing composable signatures except extensions explicitly named in that spec; new named interfaces use the exact types defined there. String additions go into all three existing values/values-fr/values-fa catalogs with matching placeholders/plurals.

### Regression owners

- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/review/WeeklyReviewFlowTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/habits/HabitReviewFlowTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/usage/ReviewUsageFlowTest.kt`.
- Extend only when behavior/semantics change: `app/src/test/java/com/luma/lifeos/ui/review/ReviewOverviewTest.kt`.
- Extend only when behavior/semantics change: `app/src/test/java/com/luma/lifeos/ui/review/ReviewSectionsTest.kt`.
- Extend only when behavior/semantics change: `app/src/test/java/com/luma/lifeos/ui/review/HabitWeekPresentationTest.kt`.

### Task 1: Period and section navigation

**Interfaces:** Implement this unit against the file map and contract above; no independent repository observations for decorative content. Existing state/callback contracts remain authoritative.

- [ ] Establish the baseline with existing fixtures and a before capture. For behavior/semantics additions, add the focused regression scenario before production changes: WeeklyReviewFlowTest: current/previous partial-week ranges restore, every section remains reachable and methodology opens/closes.
- [ ] Run the selected test; a new behavior test must fail for its intended missing behavior. Existing visual-only baseline tests should remain green; do not invent failing padding/token tests.
- [ ] Restyle ReviewHeader, ReviewSectionNavigation and ReviewSection containers using T30, preserving sticky anchors, methodology dialogs and selected-week behavior.
- [ ] Rerun the affected regression tests and inspect: Inspect period badge/caption and sticky section controls with long French/Persian labels.
- [ ] Record results/evidence for this unit and review the diff against every owned spec requirement. Commit the reviewed implementation/tests with a task-specific message when executing this plan.

### Task 2: Overview and detailed metrics

**Interfaces:** Implement this unit against the file map and contract above; no independent repository observations for decorative content. Existing state/callback contracts remain authoritative.

- [ ] Establish the baseline with existing fixtures and a before capture. For behavior/semantics additions, add the focused regression scenario before production changes: ReviewOverviewTest/HabitWeekPresentationTest and ReviewUsageFlowTest: missing vs recorded zero, fractional activity, partial coverage, source IDs and per-habit future slots remain distinct.
- [ ] Run the selected test; a new behavior test must fail for its intended missing behavior. Existing visual-only baseline tests should remain green; do not invent failing padding/token tests.
- [ ] Apply shared value, absence and delta treatments to overview/signals/memory and detailed sections; keep data calculations and order unchanged. Use existing habit-day slots only where their current model applies.
- [ ] Rerun the affected regression tests and inspect: Inspect personal→memory→signals overview and detailed comparisons without invented streak/chart/active-day text; all current/previous sources still work.
- [ ] Record results/evidence for this unit and review the diff against every owned spec requirement. Commit the reviewed implementation/tests with a task-specific message when executing this plan.

## Planned verification and completion

Run focused classes while iterating; extend their fixtures rather than duplicate the same state contract in a new suite. Also run any new test classes named in the spec. Commands are planned checks and have not run in this documentation change.

```bash
./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.ui.review.ReviewOverviewTest' --tests 'com.luma.lifeos.ui.review.ReviewSectionsTest' --tests 'com.luma.lifeos.ui.review.HabitWeekPresentationTest'
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.review.WeeklyReviewFlowTest,com.luma.lifeos.habits.HabitReviewFlowTest,com.luma.lifeos.usage.ReviewUsageFlowTest
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: BUILD SUCCESSFUL, zero selected test failures/lint errors, actual device results for instrumentation. Missing prerequisites or skipped device tasks are NOT RUN with the exact reason. Confirm each acceptance scenario in the spec against implementation and evidence; record commands, build/device configuration and screenshots in the T33 ledger. Do not repeat passing checks unless changes/failures justify it.

## Handoff

Depends on: T30. Execute after dependency/spec review and hand results to T33. This plan does not begin app implementation.
