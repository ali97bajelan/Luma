# O10 — Optional visible mood note Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan does not request subagent delegation.

**Goal:** Show the existing mood note immediately, as pictured, while keeping structured energy/stress/people/place controls in More details.

**Architecture:** Keep existing model/repository/callback contracts and change only the explicitly selected UI behavior. Derive display state locally and retain all recovery and validation branches.

**Tech Stack:** Existing Kotlin, Compose Material 3, Navigation Compose, Gradle/JDK 17 and JUnit/Compose tests.

**Spec:** [O10 specification](../specs/2026-10-23-o10-optional-visible-mood-note-design.md).

**Status:** Optional / deferred / not selected. Start only after explicit selection; no checks have run.

## Global constraints

- Native Android/Kotlin/Compose; preserve minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Use T30 semantic roles and bundled fonts; support System/Light/Dark and English/French/Persian offline.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL, IME and system insets.
- This task is optional, deferred and not selected; base redesign acceptance does not depend on it.
- Preserve all product behavior outside the explicit changes below; no new permissions, dependencies or network services.

## Review Focus

- Collapsed new form exposes one bound note (O10-1).
- Repeated toggle/recreation preserves text without duplicate nodes (O10-1/2).
- Restored links and energy/stress keep current expansion semantics (O10-2).
- Non-durable draft cannot enable Save (O10-3).
- IME/large text retains note errors and disclosure actions (O10-4).

## File map

- Modify `app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorScreen.kt` only for note placement and disclosure copy.
- Reuse `MoodEditorViewModel.kt`, `MoodDraftStore.kt` and `MoodEditorRoute.kt` unchanged in the same package.
- Update `app/src/main/res/values{,-fr,-fa}/strings.xml` only if context help needs wording that no longer promises to reveal the note.

### Task 1: Move the existing field and retain disclosure behavior

**Interfaces:** Keep `MoodEditorScreen` and `onNote: (String) -> Unit` unchanged. Consume the current draft.note and error state; produce one visible `mood_note` node.

- [ ] Extend `app/src/androidTest/java/com/luma/lifeos/MoodCaptureTest.kt` with O10-1/2: enter text before expansion, toggle twice, recreate, and assert one note field plus retained note/structured values.
- [ ] Extend relevant recovery assertions in `CaptureAccessibilityTest.kt` for O10-3; run focused cases and confirm the collapsed-field case fails against base T36.
- [ ] Move the existing note and its validation outside the disclosure; remove the nested copy, adjust localized context help and retain recovery-only previews.
- [ ] Rerun the focused tests and existing `app/src/test/java/com/luma/lifeos/ui/mood/MoodEditorTest.kt` for unchanged persistence semantics.
- [ ] Inspect O10-4 with long note/error, IME, RTL and both themes; record evidence without claiming journal functionality.

### Final task: Accept the selected option

**Files:** Create `docs/luma-redesign/evidence/<actual-run-date>/o10/acceptance.md`; update the task index and affected T33/route acceptance rows.

- [ ] Run the focused commands below on an isolated test install/AVD, then app assembly/lint. Confirm tests actually execute; unavailable prerequisites mean NOT RUN.
- [ ] Review every spec acceptance ID and Review Focus item, adding a meaningful regression assertion to its owning suite where coverage is missing and rendered/TalkBack evidence for visual/focus behavior.
- [ ] Record real results, intentional differences and any failures; leave the option incomplete while required checks fail or remain NOT RUN.
- [ ] Review and commit only the selected feature and evidence with `feat: add O10 visible mood note`.

## Planned verification

```bash
./gradlew :app:testDebugUnitTest --tests com.luma.lifeos.ui.mood.MoodEditorTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.MoodCaptureTest,com.luma.lifeos.CaptureAccessibilityTest
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: BUILD SUCCESSFUL, selected cases executed with zero failures, zero lint errors, and passing recorded acceptance. A missing device or skipped test is not a pass. The supplied plans describe future execution only.

