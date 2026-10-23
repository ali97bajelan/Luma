# T36 — Mood check-in redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan does not request subagent delegation.

**Goal:** Apply the Mood check-in hierarchy, score scale and emotion chips to the existing mood editor without turning a mood record into a journal record.

**Architecture:** Keep existing route/view-model/repository ownership. Refine Compose presentation with T30 roles and current editor components; preserve public signatures and recovery branches.

**Tech Stack:** Kotlin, Compose Material 3, Navigation Compose, Gradle/JDK 17, existing JUnit and Compose instrumentation.

**Spec:** [T36 specification](../specs/2026-10-23-t36-mood-editor-redesign-design.md).

**Status:** Planned only. No code or tests were executed by this documentation change.

## Global constraints

- Native Android, Kotlin, Compose Material 3 and Navigation Compose; keep minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Use T30 semantic roles and bundled fonts; support System/Light/Dark and English/French/Persian offline.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL, IME and system insets.
- Preserve route IDs, state/callback signatures, validation, draft persistence, save/delete/retry semantics and test tags.
- Required work adds no dependencies, permissions, schema changes, network services or sample records.
- Optional tasks default to deferred; documentation does not authorize their implementation.

## Review Focus

- Unselected score and delayed draft persistence keep Save correctly disabled (M36-3).
- Long/custom emotion labels wrap and retain their original IDs (M36-1/5).
- Draft storage or delete recovery retains read-only preview and Retry (M36-3).
- Changing locale/12–24-hour display changes formatting without changing the timestamp (M36-4).
- Shared timestamp styling and IME retain Journal/Memory confirmation actions (M36-5).

## Files and interfaces

- Modify `app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorScreen.kt`: `MoodEditorScreen`, `OptionalScale` and recovery presentation.
- Modify `app/src/main/java/com/luma/lifeos/ui/common/CaptureTimestampRow.kt` only for compatible presentation shared with Memory/Journal; T36 owns this shared pill refinement.
- Reuse `app/src/main/java/com/luma/lifeos/ui/mood/MoodUi.kt`, existing top bar/action/field/surface components and `MoodEditorRoute.kt` slots.
- Preserve `MoodEditorViewModel.kt`, `FileMoodDraftStore.kt` and `MoodDraftStore.kt` in the same mood package.
- Copy changes belong in all three `app/src/main/res/values{,-fr,-fa}/strings.xml` catalogs.

Consumes: current screen state, callbacks and shared components named in the spec. Produces: the spec's visual and accessibility contract with unchanged state/mutation signatures. Existing test tags remain stable. No domain or storage change belongs in this required task.

### Task 1: Implement the reference composition

**Interfaces:** Use the existing screen signature listed below and the spec's exact field semantics; retain all callback types and route inputs.

- [ ] Record the existing create/edit/recovery states and `MoodEditorScreen` signature, including its people/place content slots and `onRetryDraft`.
- [ ] Refine `CaptureTimestampRow(milliseconds, enabled, timeError, onTime, modifier)` presentation with unchanged signature, tags and dialogs; review Memory/Journal consumers in the same change.
- [ ] Restyle mood header, five score controls and repository-backed emotion chips. Keep existing score callbacks, UUID selection, accessibility descriptions and durability-gated Save.
- [ ] Restyle the current disclosure and expanded optional scales/note/context; keep its initialization and recovery branches. O10 must remain unselected unless explicitly requested.
- [ ] Verify M36-1 through M36-4 with the existing mood regression owners; extend coverage only for missing selected semantics, recovery reachability or timestamp regressions.
- [ ] Inspect M36-5, including error/recovery states and shared Journal/Memory timestamp dialogs, then record light/dark evidence.

### Task 2: Record acceptance and integrate

**Files:** Update `docs/luma-redesign/route-coverage.md`, `docs/UI_REDESIGN_TASKS.md` and the T33 acceptance ledger; create `docs/luma-redesign/evidence/<actual-run-date>/t36/acceptance.md`.

- [ ] Run the focused unit and device commands below after implementation. Confirm all selected tests ran and passed; unavailable device/build checks are NOT RUN.
- [ ] Cover each Review Focus item with the corresponding acceptance scenario; extend its existing owner if automation does not yet cover the semantic risk, and use rendered/TalkBack checks for geometry, contrast and focus.
- [ ] Record every spec acceptance ID with PASS/FAIL/NOT RUN and evidence; document omitted optional features as intentional differences.
- [ ] Review the implementation diff for shared-component consumers, changed callbacks and unrelated edits; update task status without closing older blockers.
- [ ] Commit the scoped implementation and evidence with `feat(ui): refine T36 mood editor`.

## Planned verification commands

```bash
./gradlew :app:testDebugUnitTest --tests com.luma.lifeos.ui.mood.MoodEditorTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.MoodCaptureTest,com.luma.lifeos.CaptureAccessibilityTest,com.luma.lifeos.ContextOptionPickerTest,com.luma.lifeos.JournalCaptureTest,com.luma.lifeos.MemoryCaptureTest
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: BUILD SUCCESSFUL, selected tests executed with zero failures, zero lint errors, plus passing recorded visual/accessibility scenarios. Run on an isolated test install/AVD without user data; connected-test teardown may uninstall the package. Do not repeat passing checks without a new change or unresolved failure. These are future implementation checks, not results of this planning pass.

