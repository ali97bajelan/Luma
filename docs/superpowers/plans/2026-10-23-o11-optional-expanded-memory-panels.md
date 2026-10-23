# O11 — Optional expanded memory panels Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan does not request subagent delegation.

**Goal:** Start the importance and context panels expanded on new memory forms to match the reference's immediately visible controls.

**Architecture:** Keep existing model/repository/callback contracts and change only the explicitly selected UI behavior. Derive display state locally and retain all recovery and validation branches.

**Tech Stack:** Existing Kotlin, Compose Material 3, Navigation Compose, Gradle/JDK 17 and JUnit/Compose tests.

**Spec:** [O11 specification](../specs/2026-10-23-o11-optional-expanded-memory-panels-design.md).

**Status:** Optional / deferred / not selected. Start only after explicit selection; no checks have run.

## Global constraints

- Native Android/Kotlin/Compose; preserve minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Use T30 semantic roles and bundled fonts; support System/Light/Dark and English/French/Persian offline.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL, IME and system insets.
- This task is optional, deferred and not selected; base redesign acceptance does not depend on it.
- Preserve all product behavior outside the explicit changes below; no new permissions, dependencies or network services.

## Review Focus

- First ready frame expands each panel once (O11-1).
- A user's collapse survives recreation and late load (O11-2).
- Photo-error recovery intentionally overrides collapse (O11-3).
- Null importance stays null until an explicit choice (O11-1/4).
- Expanded panels do not obscure source preview, photo Retry or Save (O11-4).

## File map

- Modify `app/src/main/java/com/luma/lifeos/ui/memory/MemoryEditorScreen.kt`: existing `importanceExpanded`, `more` and initialization effects.
- Reuse `app/src/main/java/com/luma/lifeos/ui/common/EditorOptionalSection.kt`; no shared default changes for other editors.
- Preserve `MemoryEditorViewModel.kt` and `MemoryEditorRoute.kt` in the memory package; existing callbacks remain unchanged.

### Task 1: Change initialization without overriding restored choices

**Interfaces:** Keep `MemoryEditorScreen`, nullable importance callback and content slots unchanged. Produce the new initial disclosure policy through existing saveable UI state.

- [ ] Extend `app/src/androidTest/java/com/luma/lifeos/MemoryCaptureTest.kt` for O11-1/2; assert fresh visibility, manual collapse, recreation and unrelated draft updates.
- [ ] Extend `PhotoCaptureTest.kt` for O11-3's collapsed-before-failure recovery and retained values; confirm the fresh-default assertion fails before changing base T38.
- [ ] Update only initialization/default policy; preserve saveable initialized flags, manual toggles, photo-error effect and bring-into-view behavior.
- [ ] Rerun the focused device cases plus `app/src/test/java/com/luma/lifeos/ui/memory/MemoryEditorTest.kt`.
- [ ] Inspect O11-4 on direct/edit/promote screens at narrow/large-text/RTL bounds with IME and long real names; record evidence.

### Final task: Accept the selected option

**Files:** Create `docs/luma-redesign/evidence/<actual-run-date>/o11/acceptance.md`; update the task index and affected T33/route acceptance rows.

- [ ] Run the focused commands below on an isolated test install/AVD, then app assembly/lint. Confirm tests actually execute; unavailable prerequisites mean NOT RUN.
- [ ] Review every spec acceptance ID and Review Focus item, adding a meaningful regression assertion to its owning suite where coverage is missing and rendered/TalkBack evidence for visual/focus behavior.
- [ ] Record real results, intentional differences and any failures; leave the option incomplete while required checks fail or remain NOT RUN.
- [ ] Review and commit only the selected feature and evidence with `feat: add O11 expanded memory panels`.

## Planned verification

```bash
./gradlew :app:testDebugUnitTest --tests com.luma.lifeos.ui.memory.MemoryEditorTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.MemoryCaptureTest,com.luma.lifeos.PhotoCaptureTest
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: BUILD SUCCESSFUL, selected cases executed with zero failures, zero lint errors, and passing recorded acceptance. A missing device or skipped test is not a pass. The supplied plans describe future execution only.

