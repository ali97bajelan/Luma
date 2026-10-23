# T38 — Memory editor redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan does not request subagent delegation.

**Goal:** Apply the Record a memory composition to the existing direct-memory and promoted-memory paths without changing their source-record semantics.

**Architecture:** Keep existing route/view-model/repository ownership. Refine Compose presentation with T30 roles and current editor components; preserve public signatures and recovery branches.

**Tech Stack:** Kotlin, Compose Material 3, Navigation Compose, Gradle/JDK 17, existing JUnit and Compose instrumentation.

**Spec:** [T38 specification](../specs/2026-10-23-t38-memory-editor-redesign-design.md).

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

- Blank direct-memory description keeps Save disabled; marker note remains optional (R38-1/3).
- Promoted source timestamp and content cannot become editable direct-memory fields (R38-3).
- Null importance and retapping a score retain clearable selection (R38-2).
- Photo import failure expands and scrolls to Retry without dropping other fields (R38-4).
- Long actual names/photo rows and mixed Persian/Latin text retain actions under IME (R38-5).

## Files and interfaces

- Modify `app/src/main/java/com/luma/lifeos/ui/memory/MemoryEditorScreen.kt`: retain `MemoryEditorScreen` signature and people/place/photos slots.
- Reuse `app/src/main/java/com/luma/lifeos/ui/common/CaptureTimestampRow.kt` after T36, plus existing editor/optional-section/surface components.
- Preserve `MemoryEditorRoute.kt` and `MemoryEditorViewModel.kt` in the memory package and `app/src/main/java/com/luma/lifeos/ui/photos/PhotoGallery.kt` behavior.
- Preserve `core/domain/src/main/kotlin/com/luma/lifeos/domain/model/Memory.kt` and current photo-import limits.
- Copy changes belong in all three `app/src/main/res/values{,-fr,-fa}/strings.xml` catalogs.

Consumes: current screen state, callbacks and shared components named in the spec. Produces: the spec's visual and accessibility contract with unchanged state/mutation signatures. Existing test tags remain stable. No domain or storage change belongs in this required task.

### Task 1: Implement the reference composition

**Interfaces:** Use the existing screen signature listed below and the spec's exact field semantics; retain all callback types and route inputs.

- [ ] Record direct-create/edit/promote states and the `MemoryEditorScreen` signature, including all three content slots and nullable importance callback.
- [ ] Restyle the header, timestamp usage, direct title and description/marker-note fields; keep source preview, locked promotion timestamp and exact text limits.
- [ ] Restyle current importance disclosure/summary/choices and context disclosure/slots with rounded surfaces. Keep initialization, retap-to-clear and photo-error auto-expansion.
- [ ] Restyle Save/remove/discard and error surfaces; retain direct-delete versus promoted-unmark behavior. Omit sample names, Draft/High value badges and writing helpers.
- [ ] Verify R38-1 through R38-4 using existing memory/photo/context suites; extend only for missing semantic or recovery regressions.
- [ ] Inspect R38-5 including an imported photo failure, promotion source preview and long linked names; record light/dark evidence.

### Task 2: Record acceptance and integrate

**Files:** Update `docs/luma-redesign/route-coverage.md`, `docs/UI_REDESIGN_TASKS.md` and the T33 acceptance ledger; create `docs/luma-redesign/evidence/<actual-run-date>/t38/acceptance.md`.

- [ ] Run the focused unit and device commands below after implementation. Confirm all selected tests ran and passed; unavailable device/build checks are NOT RUN.
- [ ] Cover each Review Focus item with the corresponding acceptance scenario; extend its existing owner if automation does not yet cover the semantic risk, and use rendered/TalkBack checks for geometry, contrast and focus.
- [ ] Record every spec acceptance ID with PASS/FAIL/NOT RUN and evidence; document omitted optional features as intentional differences.
- [ ] Review the implementation diff for shared-component consumers, changed callbacks and unrelated edits; update task status without closing older blockers.
- [ ] Commit the scoped implementation and evidence with `feat(ui): refine T38 memory editor`.

## Planned verification commands

```bash
./gradlew :app:testDebugUnitTest --tests com.luma.lifeos.ui.memory.MemoryEditorTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.MemoryCaptureTest,com.luma.lifeos.PhotoCaptureTest,com.luma.lifeos.ContextOptionPickerTest,com.luma.lifeos.CaptureAccessibilityTest,com.luma.lifeos.DetailNavigationTest
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: BUILD SUCCESSFUL, selected tests executed with zero failures, zero lint errors, plus passing recorded visual/accessibility scenarios. Run on an isolated test install/AVD without user data; connected-test teardown may uninstall the package. Do not repeat passing checks without a new change or unresolved failure. These are future implementation checks, not results of this planning pass.

