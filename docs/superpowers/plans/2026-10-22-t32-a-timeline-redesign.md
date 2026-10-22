# T32-A — Timeline stream and editorial cards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan does not request subagent delegation.

**Goal:** Present the existing mixed chronological feed with a continuous visual rail, type nodes and readable editorial cards.

**Architecture:** Existing repositories/view models retain data ownership and LifeOsApp retains navigation. Shared T30 roles/components provide presentation; the spec defines any optional new interface. Keep current product behavior.

**Tech Stack:** Kotlin, Compose Material 3, Navigation Compose, JUnit/Compose instrumentation, Gradle and JDK 17.

**Spec:** [Corresponding specification](../specs/2026-10-22-t32-a-timeline-redesign-design.md).

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

- TimelinePagingRouteTest: stable keys/order across bounded windows, equal timestamps, date-only logs, usage toggle and selected-day restoration; error/empty retains controls.
- Inspect decorative rail continuity and RTL placement, no extra semantic focus targets and last-row clearance above capture FAB.
- EventCardTest and HabitTimelineFlowTest: open/memory/correction targets retain record/log IDs, zone labels and exact activity units; standalone logs do not acquire inferred targets.
- Inspect serif journal title/excerpt, metadata-only photo count and all non-journal card variants in both themes and shared consumers.
- Long French/Persian at 320 dp/2.0, restored context and system/IME insets must retain every action and exact value.

## Files and interfaces

- Modify: `app/src/main/java/com/luma/lifeos/ui/timeline/TimelineRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/timeline/EventCard.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/timeline/HabitTimelineCard.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/timeline/DayNavigation.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/timeline/TimelineViewOptions.kt`.

Consumes: current signatures/state/callbacks in these files and shared T30 roles. Produces: the linked spec’s screen contract. Preserve existing composable signatures except extensions explicitly named in that spec; new named interfaces use the exact types defined there. String additions go into all three existing values/values-fr/values-fa catalogs with matching placeholders/plurals.

### Regression owners

- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/TimelineFlowTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/TimelinePagingRouteTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/habits/HabitTimelineFlowTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/EventCardTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/DetailNavigationTest.kt`.

### Task 1: Stream framing and controls

**Interfaces:** Implement this unit against the file map and contract above; no independent repository observations for decorative content. Existing state/callback contracts remain authoritative.

- [ ] Establish the baseline with existing fixtures and a before capture. For behavior/semantics additions, add the focused regression scenario before production changes: TimelinePagingRouteTest: stable keys/order across bounded windows, equal timestamps, date-only logs, usage toggle and selected-day restoration; error/empty retains controls.
- [ ] Run the selected test; a new behavior test must fail for its intended missing behavior. Existing visual-only baseline tests should remain green; do not invent failing padding/token tests.
- [ ] Update TimelineRoute lazy-row wrappers and existing header/View options styling. Keep the pager and query unchanged; connect decorative rail segments without collecting a second feed.
- [ ] Rerun the affected regression tests and inspect: Inspect decorative rail continuity and RTL placement, no extra semantic focus targets and last-row clearance above capture FAB.
- [ ] Record results/evidence for this unit and review the diff against every owned spec requirement. Commit the reviewed implementation/tests with a task-specific message when executing this plan.

### Task 2: Event and habit cards

**Interfaces:** Implement this unit against the file map and contract above; no independent repository observations for decorative content. Existing state/callback contracts remain authoritative.

- [ ] Establish the baseline with existing fixtures and a before capture. For behavior/semantics additions, add the focused regression scenario before production changes: EventCardTest and HabitTimelineFlowTest: open/memory/correction targets retain record/log IDs, zone labels and exact activity units; standalone logs do not acquire inferred targets.
- [ ] Run the selected test; a new behavior test must fail for its intended missing behavior. Existing visual-only baseline tests should remain green; do not invent failing padding/token tests.
- [ ] Restyle EventCard and HabitTimelineCard using existing models and callbacks. Apply serif journal headings and concise metadata; preserve memory action semantics and source/zone labels.
- [ ] Rerun the affected regression tests and inspect: Inspect serif journal title/excerpt, metadata-only photo count and all non-journal card variants in both themes and shared consumers.
- [ ] Record results/evidence for this unit and review the diff against every owned spec requirement. Commit the reviewed implementation/tests with a task-specific message when executing this plan.

## Planned verification and completion

Run focused classes while iterating; extend their fixtures rather than duplicate the same state contract in a new suite. Also run any new test classes named in the spec. Commands are planned checks and have not run in this documentation change.

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.TimelineFlowTest,com.luma.lifeos.TimelinePagingRouteTest,com.luma.lifeos.habits.HabitTimelineFlowTest,com.luma.lifeos.EventCardTest,com.luma.lifeos.DetailNavigationTest
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: BUILD SUCCESSFUL, zero selected test failures/lint errors, actual device results for instrumentation. Missing prerequisites or skipped device tasks are NOT RUN with the exact reason. Confirm each acceptance scenario in the spec against implementation and evidence; record commands, build/device configuration and screenshots in the T33 ledger. Do not repeat passing checks unless changes/failures justify it.

## Handoff

Depends on: T30. Execute after dependency/spec review and hand results to T33. This plan does not begin app implementation.
