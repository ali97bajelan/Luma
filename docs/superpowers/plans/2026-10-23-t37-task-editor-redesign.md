# T37 — Task editor redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan does not request subagent delegation.

**Goal:** Apply the New task field, due-date card and footer composition while preserving standalone task data and revision-based recovery.

**Architecture:** Keep existing route/view-model/repository ownership. Refine Compose presentation with T30 roles and current editor components; preserve public signatures and recovery branches.

**Tech Stack:** Kotlin, Compose Material 3, Navigation Compose, Gradle/JDK 17, existing JUnit and Compose instrumentation.

**Spec:** [T37 specification](../specs/2026-10-23-t37-task-editor-redesign-design.md).

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

- Supplementary emoji at 200/4,000 code points preserve current acceptance boundaries (T37-2).
- No-date create and historical date caller never silently become today (T37-1).
- Long localized date places Change/Remove on reachable separate rows (T37-5).
- Dirty draft plus stale revision retains confirmation before Reload latest (T37-3/4).
- Busy completion/delete and missing record cannot expose active Save (T37-4).

## Files and interfaces

- Modify `app/src/main/java/com/luma/lifeos/ui/todo/TodoEditorScreen.kt`: retain `TodoEditorScreen` signature, autofocus, field-error focus and all modal branches.
- Reuse `app/src/main/java/com/luma/lifeos/ui/common/EditorTopBar.kt`, `EditorOptionalSection.kt` and `LumaUiComponents.kt`.
- Preserve `TodoEditorRoute.kt`, `TodoEditorViewModel.kt` and `TodoUiState.kt` in the same todo package.
- Preserve `core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoInputs.kt`, `TodoRules.kt` and `TodoRepository.kt`.
- Copy changes belong in all three `app/src/main/res/values{,-fr,-fa}/strings.xml` catalogs.

Consumes: current screen state, callbacks and shared components named in the spec. Produces: the spec's visual and accessibility contract with unchanged state/mutation signatures. Existing test tags remain stable. No domain or storage change belongs in this required task.

### Task 1: Implement the reference composition

**Interfaces:** Use the existing screen signature listed below and the spec's exact field semantics; retain all callback types and route inputs.

- [ ] Record existing new/edit/no-date/conflict states and the current `TodoEditorScreen` callbacks; preserve all tags and the route result contract.
- [ ] Restyle the title and note fields plus supporting hints; retain focus requesters, keyboard actions and error bring-into-view.
- [ ] Group the existing due value, Change and conditional Remove actions into a rounded responsive card; retain the nullable date callback and existing picker.
- [ ] Restyle the footer, edit-only completion/delete and recovery dialogs; omit tags and evening-review copy. Keep `TodoEditorViewModel` and domain/storage unchanged.
- [ ] Verify T37-1 through T37-4 using existing task suites; add only missing regression assertions for code-point boundaries, confirmation or pending controls.
- [ ] Inspect T37-5 with a long date, note error and IME, then record light/dark evidence.

### Task 2: Record acceptance and integrate

**Files:** Update `docs/luma-redesign/route-coverage.md`, `docs/UI_REDESIGN_TASKS.md` and the T33 acceptance ledger; create `docs/luma-redesign/evidence/<actual-run-date>/t37/acceptance.md`.

- [ ] Run the focused unit and device commands below after implementation. Confirm all selected tests ran and passed; unavailable device/build checks are NOT RUN.
- [ ] Cover each Review Focus item with the corresponding acceptance scenario; extend its existing owner if automation does not yet cover the semantic risk, and use rendered/TalkBack checks for geometry, contrast and focus.
- [ ] Record every spec acceptance ID with PASS/FAIL/NOT RUN and evidence; document omitted optional features as intentional differences.
- [ ] Review the implementation diff for shared-component consumers, changed callbacks and unrelated edits; update task status without closing older blockers.
- [ ] Commit the scoped implementation and evidence with `feat(ui): refine T37 task editor`.

## Planned verification commands

```bash
./gradlew :app:testDebugUnitTest --tests com.luma.lifeos.ui.todo.TodoEditorViewModelTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.todo.TodoEditorFlowTest,com.luma.lifeos.todo.TodoAccessibilityTest,com.luma.lifeos.todo.TodoLanguageFlowTest
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: BUILD SUCCESSFUL, selected tests executed with zero failures, zero lint errors, plus passing recorded visual/accessibility scenarios. Run on an isolated test install/AVD without user data; connected-test teardown may uninstall the package. Do not repeat passing checks without a new change or unresolved failure. These are future implementation checks, not results of this planning pass.

