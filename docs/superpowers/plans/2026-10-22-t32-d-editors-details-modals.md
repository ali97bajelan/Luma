# T32-D — Editors, details, sheets and dialogs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan does not request subagent delegation.

**Goal:** Make capture/edit/detail surfaces and modal UI consistent while retaining drafts, validation, editing and recovery.

**Architecture:** Existing repositories/view models retain data ownership and LifeOsApp retains navigation. Shared T30 roles/components provide presentation; the spec defines any optional new interface. Keep current product behavior.

**Tech Stack:** Kotlin, Compose Material 3, Navigation Compose, JUnit/Compose instrumentation, Gradle and JDK 17.

**Spec:** [Corresponding specification](../specs/2026-10-22-t32-d-editors-details-modals-design.md).

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

- JournalEditorScreenTest, MoodCaptureTest, MemoryCaptureTest, TodoEditorFlowTest and HabitCaptureFlowTest: validation, draft restore, Save/Cancel and now/selected-date capture rules retain stored values.
- Inspect IME, long fields, large text and picker/sheet scrolling; Quick Capture dismissal must write nothing.
- DetailNavigationTest, PhotoCaptureTest and FeedbackAccessibilityTest: missing/deleted/imported records keep correct actions, photo failures retry and consumed save feedback does not replay.
- Inspect detail photo/gallery/source metadata, correction dialogs, unsaved/delete/reset/restore confirmations and modal focus/Back behavior in both themes.
- Long French/Persian at 320 dp/2.0, restored context and system/IME insets must retain every action and exact value.

## Files and interfaces

- Modify: `app/src/main/java/com/luma/lifeos/ui/QuickCaptureSheet.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/journal/JournalEditorScreen.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/journal/JournalDetailRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorScreen.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/memory/MemoryEditorScreen.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/todo/TodoEditorScreen.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitEditorRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitDetailRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitLogCorrectionDialog.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/people/PersonEditorRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/people/PeopleRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/places/PlaceEditorRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/places/PlacesRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/timeline/EventDetailRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/common/EditorTopBar.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/common/DetailTopBar.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/common/ContextOptionPicker.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/common/CaptureTimestampRow.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/common/EditorOptionalSection.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/photos/PhotoGallery.kt`.

Consumes: current signatures/state/callbacks in these files and shared T30 roles. Produces: the linked spec’s screen contract. Preserve existing composable signatures except extensions explicitly named in that spec; new named interfaces use the exact types defined there. String additions go into all three existing values/values-fr/values-fa catalogs with matching placeholders/plurals.

### Regression owners

- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/CaptureAccessibilityTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/JournalEditorScreenTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/MoodCaptureTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/MemoryCaptureTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/PhotoCaptureTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/ContextOptionPickerTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/DetailNavigationTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/todo/TodoEditorFlowTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/habits/HabitCaptureFlowTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/FeedbackAccessibilityTest.kt`.

### Task 1: Editors and chooser

**Interfaces:** Implement this unit against the file map and contract above; no independent repository observations for decorative content. Existing state/callback contracts remain authoritative.

- [ ] Establish the baseline with existing fixtures and a before capture. For behavior/semantics additions, add the focused regression scenario before production changes: JournalEditorScreenTest, MoodCaptureTest, MemoryCaptureTest, TodoEditorFlowTest and HabitCaptureFlowTest: validation, draft restore, Save/Cancel and now/selected-date capture rules retain stored values.
- [ ] Run the selected test; a new behavior test must fail for its intended missing behavior. Existing visual-only baseline tests should remain green; do not invent failing padding/token tests.
- [ ] Restyle QuickCaptureSheet using the rounded surface/title/cancel treatment in corrected timeline-light.html, while retaining full-width Mood/Journal/Memory/Task choices, selected-date policy and existing dismissal behavior. Do not copy hard-coded Water/Stretch actions, three-column grid or encryption claim. Restyle editor top bars, fields, optional sections and pickers using shared tokens; keep view models, result types and drafts unchanged.
- [ ] Rerun the affected regression tests and inspect: Inspect IME, long fields, large text and picker/sheet scrolling; Quick Capture dismissal must write nothing.
- [ ] Record results/evidence for this unit and review the diff against every owned spec requirement. Commit the reviewed implementation/tests with a task-specific message when executing this plan.

### Task 2: Details and modal recovery

**Interfaces:** Implement this unit against the file map and contract above; no independent repository observations for decorative content. Existing state/callback contracts remain authoritative.

- [ ] Establish the baseline with existing fixtures and a before capture. For behavior/semantics additions, add the focused regression scenario before production changes: DetailNavigationTest, PhotoCaptureTest and FeedbackAccessibilityTest: missing/deleted/imported records keep correct actions, photo failures retry and consumed save feedback does not replay.
- [ ] Run the selected test; a new behavior test must fail for its intended missing behavior. Existing visual-only baseline tests should remain green; do not invent failing padding/token tests.
- [ ] Restyle detail routes, photo gallery containers and existing dialogs. Verify destructive/recovery flows and imported-record restrictions without adding new routes.
- [ ] Rerun the affected regression tests and inspect: Inspect detail photo/gallery/source metadata, correction dialogs, unsaved/delete/reset/restore confirmations and modal focus/Back behavior in both themes.
- [ ] Record results/evidence for this unit and review the diff against every owned spec requirement. Commit the reviewed implementation/tests with a task-specific message when executing this plan.

## Planned verification and completion

Run focused classes while iterating; extend their fixtures rather than duplicate the same state contract in a new suite. Also run any new test classes named in the spec. Commands are planned checks and have not run in this documentation change.

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.CaptureAccessibilityTest,com.luma.lifeos.JournalEditorScreenTest,com.luma.lifeos.MoodCaptureTest,com.luma.lifeos.MemoryCaptureTest,com.luma.lifeos.PhotoCaptureTest,com.luma.lifeos.ContextOptionPickerTest,com.luma.lifeos.DetailNavigationTest,com.luma.lifeos.todo.TodoEditorFlowTest,com.luma.lifeos.habits.HabitCaptureFlowTest,com.luma.lifeos.FeedbackAccessibilityTest
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: BUILD SUCCESSFUL, zero selected test failures/lint errors, actual device results for instrumentation. Missing prerequisites or skipped device tasks are NOT RUN with the exact reason. Confirm each acceptance scenario in the spec against implementation and evidence; record commands, build/device configuration and screenshots in the T33 ledger. Do not repeat passing checks unless changes/failures justify it.

## Handoff

Depends on: T30; consume T32-A EventCard treatment. Execute after dependency/spec review and hand results to T33. This plan does not begin app implementation.
