# O12 — Optional memory writing helpers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan does not request subagent delegation.

**Goal:** Add the reference's secondary writing prompt and a truthful derived word count beneath the direct-memory description.

**Architecture:** Keep existing model/repository/callback contracts and change only the explicitly selected UI behavior. Derive display state locally and retain all recovery and validation branches.

**Tech Stack:** Existing Kotlin, Compose Material 3, Navigation Compose, Gradle/JDK 17 and JUnit/Compose tests.

**Spec:** [O12 specification](../specs/2026-10-23-o12-optional-memory-writing-helpers-design.md).

**Status:** Optional / deferred / not selected. Start only after explicit selection; no checks have run.

## Global constraints

- Native Android/Kotlin/Compose; preserve minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Use T30 semantic roles and bundled fonts; support System/Light/Dark and English/French/Persian offline.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL, IME and system insets.
- This task is optional, deferred and not selected; base redesign acceptance does not depend on it.
- Preserve all product behavior outside the explicit changes below; no new permissions, dependencies or network services.

## Review Focus

- Emoji, punctuation and blank text do not become words (O12-1).
- Accents, digits and Persian ZWNJ use code-point-aware platform boundaries (O12-2).
- Locale change recomputes and formats without changing note (O12-2).
- Promotion never counts source body as marker text (O12-3).
- Helpers remain secondary and quiet for screen-reader typing (O12-4).

## File map

- Create `app/src/main/java/com/luma/lifeos/ui/memory/MemoryWritingHelpers.kt` for `memoryWordCount`.
- Modify `app/src/main/java/com/luma/lifeos/ui/memory/MemoryEditorScreen.kt` to render the derived direct-memory toolbar.
- Add aligned prompt/plural resources in `app/src/main/res/values{,-fr,-fa}/strings.xml`.
- Create `app/src/test/java/com/luma/lifeos/ui/memory/MemoryWritingHelpersTest.kt`; extend existing `MemoryCaptureTest.kt` for direct/promoted UI behavior.

### Task 1: Define and display a derived word count

**Interfaces:** Produce `internal fun memoryWordCount(text: String, locale: Locale): Int`; consume the unchanged `state.draft.note` and current locale in direct mode only.

- [ ] Create `MemoryWritingHelpersTest.kt` with O12-1 exact assertions, plus accented, supplementary-letter and Persian/ZWNJ cases comparing segments to the documented letter/digit rule. Use an Android device case where platform segmentation differs from the host JDK.
- [ ] Run the new focused unit test to confirm failure before implementing the helper.
- [ ] Implement the pure count helper with BreakIterator/code-point inspection; keep it independent of view model/persistence.
- [ ] Add the localized static prompt and pluralized count under direct-memory description. Recompute on note/locale changes, wrap text, and keep it outside the editable value and error region.
- [ ] Extend `app/src/androidTest/java/com/luma/lifeos/MemoryCaptureTest.kt` with O12-2/3 direct/edit/promote and locale-change assertions; confirm saved note is exact and limits unchanged.
- [ ] Rerun focused tests and inspect O12-4 in en/fr/fa with TalkBack, IME and both themes; record the platform segmentation behavior in evidence.

### Final task: Accept the selected option

**Files:** Create `docs/luma-redesign/evidence/<actual-run-date>/o12/acceptance.md`; update the task index and affected T33/route acceptance rows.

- [ ] Run the focused commands below on an isolated test install/AVD, then app assembly/lint. Confirm tests actually execute; unavailable prerequisites mean NOT RUN.
- [ ] Review every spec acceptance ID and Review Focus item, adding a meaningful regression assertion to its owning suite where coverage is missing and rendered/TalkBack evidence for visual/focus behavior.
- [ ] Record real results, intentional differences and any failures; leave the option incomplete while required checks fail or remain NOT RUN.
- [ ] Review and commit only the selected feature and evidence with `feat: add O12 memory writing helpers`.

## Planned verification

```bash
./gradlew :app:testDebugUnitTest --tests com.luma.lifeos.ui.memory.MemoryWritingHelpersTest --tests com.luma.lifeos.ui.memory.MemoryEditorTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.MemoryCaptureTest,com.luma.lifeos.CaptureAccessibilityTest
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: BUILD SUCCESSFUL, selected cases executed with zero failures, zero lint errors, and passing recorded acceptance. A missing device or skipped test is not a pass. The supplied plans describe future execution only.

