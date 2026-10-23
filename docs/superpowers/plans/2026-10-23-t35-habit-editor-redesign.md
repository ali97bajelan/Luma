# T35 — Habit editor redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan does not request subagent delegation.

**Goal:** Apply the supplied New habit composition to creation and compatible edit states while keeping the existing count-based schedule contract.

**Architecture:** Keep existing route/view-model/repository ownership. Refine Compose presentation with T30 roles and current editor components; preserve public signatures and recovery branches.

**Tech Stack:** Kotlin, Compose Material 3, Navigation Compose, Gradle/JDK 17, existing JUnit and Compose instrumentation.

**Spec:** [T35 specification](../specs/2026-10-23-t35-habit-editor-redesign-design.md).

**Status:** Implemented (validation pending). Source changes and status/evidence updates are complete. No Gradle task, test or emulator run was performed per request.

## Global constraints

- Native Android, Kotlin, Compose Material 3 and Navigation Compose; keep minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Use T30 semantic roles and bundled fonts; support System/Light/Dark and English/French/Persian offline.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL, IME and system insets.
- Preserve route IDs, state/callback signatures, validation, draft persistence, save/delete/retry semantics and test tags.
- Required work adds no dependencies, permissions, schema changes, network services or sample records.
- Optional tasks default to deferred; documentation does not authorize their implementation.

## Review Focus

- Seven weekday choices at 320 dp/2.0 remain readable and independently selectable (H35-5).
- Localized long unit/name strings retain input and error text (H35-2/5).
- Device zone differs from tracking zone; date and guidance stay truthful (H35-3).
- Dirty Close or recreation during selection does not reset the weekday set (H35-2/4).
- Repeated Save while pending produces one completion (H35-4).

## Files and interfaces

- Modify `app/src/main/java/com/luma/lifeos/ui/habits/HabitEditorRoute.kt`: `HabitEditorScreen` presentation only; preserve its signature and route wiring.
- Reuse `ui/common/EditorTopBar.kt`, `EditorOptionalSection.kt` and `LumaUiComponents.kt` under `app/src/main/java/com/luma/lifeos/`; change a shared component only if its other consumers remain compatible.
- Preserve `app/src/main/java/com/luma/lifeos/ui/habits/HabitEditorViewModel.kt` and `core/domain/src/main/kotlin/com/luma/lifeos/domain/habit/HabitInputs.kt`.
- Copy changes, if needed, belong in all three `app/src/main/res/values{,-fr,-fa}/strings.xml` catalogs.

Consumes: current screen state, callbacks and shared components named in the spec. Produces: the spec's visual and accessibility contract with unchanged state/mutation signatures. Existing test tags remain stable. No domain or storage change belongs in this required task.

### Task 1: Implement the reference composition

**Interfaces:** Use the existing screen signature listed below and the spec's exact field semantics; retain all callback types and route inputs.

- [x] Capture the existing new/edit states and confirm the `HabitEditorScreen(state, deviceZone, onBack, onRetry, onName, onUnit, onTarget, onStart, onWeekdays, onSave)` callback contract.
- [x] Restyle the header, tracking context, inputs/target, date pill and Save footer in `HabitEditorRoute.kt`; keep loading, errors, edit restrictions and discard confirmation.
- [x] Restyle preset and weekday choices using the current `schedulePreset`/`schedulePresetDays` helpers; keep seven independent actions and selected semantics. Use adaptive wrapping instead of fixed screenshot dimensions.
- [ ] Verify H35-1 through H35-4 using the existing habit suites; NOT RUN by request.
- [ ] Inspect H35-5 with long localized labels, tracking-zone mismatch, target validation and IME; rendered captures NOT RUN by request.

### Task 2: Record acceptance and integrate

**Files:** Update `docs/luma-redesign/route-coverage.md`, `docs/UI_REDESIGN_TASKS.md` and the T33 acceptance ledger; create `docs/luma-redesign/evidence/<actual-run-date>/t35/acceptance.md`.

- [ ] Run the focused unit and device commands below after implementation. NOT RUN by request; no Gradle task or emulator was started.
- [ ] Cover each Review Focus item with the corresponding acceptance scenario; rendered/TalkBack checks NOT RUN by request.
- [x] Record every spec acceptance ID with PASS/FAIL/NOT RUN and evidence; document omitted optional features as intentional differences.
- [x] Review the implementation diff for shared-component consumers, changed callbacks and unrelated edits; update task status without closing older blockers.
- [ ] Commit the scoped implementation and evidence. Commit message prepared below; no commit was created.

Suggested commit message: `feat(ui): refine T35 habit editor`

## Planned verification commands

```bash
./gradlew :app:testDebugUnitTest --tests com.luma.lifeos.ui.habits.HabitEditorViewModelTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.habits.HabitCaptureFlowTest,com.luma.lifeos.habits.HabitDateSelectionFlowTest,com.luma.lifeos.CaptureAccessibilityTest
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: BUILD SUCCESSFUL, selected tests executed with zero failures, zero lint errors, plus passing recorded visual/accessibility scenarios. Run on an isolated test install/AVD without user data; connected-test teardown may uninstall the package. Do not repeat passing checks without a new change or unresolved failure. These are future implementation checks, not results of this planning pass.
