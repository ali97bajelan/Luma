# O02 — Optional — Timeline event-type filters Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan does not request subagent delegation.

**Goal:** Add functional All events, Reflections, Habits and Health filters over the full paged feed.

**Architecture:** Existing repositories/view models retain data ownership and LifeOsApp retains navigation. Shared T30 roles/components provide presentation; the spec defines any optional new interface. Optional scope is deferred until selected.

**Tech Stack:** Kotlin, Compose Material 3, Navigation Compose, JUnit/Compose instrumentation, Gradle and JDK 17.

**Spec:** [Corresponding specification](../specs/2026-10-22-o02-optional-timeline-filters-design.md).

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

- Add `filterFindsRecordsOutsideInitialWindow` to TimelineViewModelTest/TimelinePagingRouteTest using >150 mixed records; assert matching records/counts/pages before and after filter change.
- Add `filterRestoresAndRejectsStaleDayResults`: race two date/filter requests, recreate, and assert only latest query renders, usage preference unchanged.
- Add `filteredEmptyStateCanReturnToAll` to TimelinePagingRouteTest; assert each enum predicate including manual mood/journal/memory vs Health Connect activity.
- Inspect active filter visibility, accessibility selected state, wrap/scroll and no-match recovery in RTL/large text.
- Long French/Persian at 320 dp/2.0, restored context and system/IME insets must retain every action and exact value.

## Files and interfaces

- Modify: `app/src/main/java/com/luma/lifeos/ui/timeline/TimelineRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/timeline/TimelineViewModel.kt`.
- Modify: `core/domain/src/main/kotlin/com/luma/lifeos/domain/repository/TimelineRepository.kt`.
- Modify: `core/data/src/main/kotlin/com/luma/lifeos/data/habit/HabitTimelineSql.kt`.
- Modify: `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomTimelineRepository.kt`.
- Create the models/components/tests explicitly specified under “Additional interfaces and data files” in the spec.

Consumes: current signatures/state/callbacks in these files and shared T30 roles. Produces: the linked spec’s screen contract. Preserve existing composable signatures except extensions explicitly named in that spec; new named interfaces use the exact types defined there. String additions go into all three existing values/values-fr/values-fa catalogs with matching placeholders/plurals.

### Regression owners

- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/TimelinePagingRouteTest.kt`.
- Extend only when behavior/semantics change: `app/src/test/java/com/luma/lifeos/ui/timeline/TimelineViewModelTest.kt`.

### Task 1: Repository and paging contract

**Interfaces:** Implement this unit against the file map and contract above; no independent repository observations for decorative content. Read the spec’s additional data interfaces before changing callers or fakes.

- [ ] Establish the baseline with existing fixtures and a before capture. For behavior/semantics additions, add the focused regression scenario before production changes: Add `filterFindsRecordsOutsideInitialWindow` to TimelineViewModelTest/TimelinePagingRouteTest using >150 mixed records; assert matching records/counts/pages before and after filter change.
- [ ] Run the selected test; a new behavior test must fail for its intended missing behavior. Existing visual-only baseline tests should remain green; do not invent failing padding/token tests.
- [ ] Create domain/habit/TimelineFilter.kt and extend TimelineRepository plus implementations with a default ALL filter. Apply identical predicates to item/count queries and pagination.
- [ ] Rerun the affected regression tests and inspect: Add `filterRestoresAndRejectsStaleDayResults`: race two date/filter requests, recreate, and assert only latest query renders, usage preference unchanged.
- [ ] Record results/evidence for this unit and review the diff against every owned spec requirement. Commit the reviewed implementation/tests with a task-specific message when executing this plan.

### Task 2: Filter controls and restoration

**Interfaces:** Implement this unit against the file map and contract above; no independent repository observations for decorative content. Read the spec’s additional data interfaces before changing callers or fakes.

- [ ] Establish the baseline with existing fixtures and a before capture. For behavior/semantics additions, add the focused regression scenario before production changes: Add `filteredEmptyStateCanReturnToAll` to TimelinePagingRouteTest; assert each enum predicate including manual mood/journal/memory vs Health Connect activity.
- [ ] Run the selected test; a new behavior test must fail for its intended missing behavior. Existing visual-only baseline tests should remain green; do not invent failing padding/token tests.
- [ ] Connect chips to a SavedStateHandle-owned filter in TimelineViewModel and reset the paging window on changes; preserve selected date and usage preference.
- [ ] Rerun the affected regression tests and inspect: Inspect active filter visibility, accessibility selected state, wrap/scroll and no-match recovery in RTL/large text.
- [ ] Record results/evidence for this unit and review the diff against every owned spec requirement. Commit the reviewed implementation/tests with a task-specific message when executing this plan.

## Planned verification and completion

Run focused classes while iterating; extend their fixtures rather than duplicate the same state contract in a new suite. Also run any new test classes named in the spec. Commands are planned checks and have not run in this documentation change.

```bash
./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.ui.timeline.TimelineViewModelTest'
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.TimelinePagingRouteTest
./gradlew :core:domain:test :core:data:test
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: BUILD SUCCESSFUL, zero selected test failures/lint errors, actual device results for instrumentation. Missing prerequisites or skipped device tasks are NOT RUN with the exact reason. Confirm each acceptance scenario in the spec against implementation and evidence; record commands, build/device configuration and screenshots in the T33 ledger. Do not repeat passing checks unless changes/failures justify it.

## Handoff

Depends on: T32-A. Select this optional scope explicitly before execution. This plan does not begin app implementation.
