# UX-015 — Habit failures display the wrong operation's message Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use superpowers:subagent-driven-development only if the user selects delegation. Steps use checkbox syntax for tracking.

**Goal:** Give history, correction and whole-habit deletion failures accurate operation-specific messages and recovery.

**Architecture:** Replace HabitDetailUiState.actionError's untyped string with a typed operation/target. Render errors beside their owning actions rather than a universal correction error.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, lifecycle StateFlow and the existing Android/Room modules; existing dependencies.

**Spec:** [UX-015 specification](../specs/2026-10-20-audit-n-ux-015-habit-operation-errors-design.md)

**Status:** Implemented / acceptance pending. Gradle and emulator checks were not run per request. Relative effort: Low.

## Global Constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Review Focus

These five concrete failure modes are pinned to the regression cases owned by this task. The assertions below are requirements for tests to add, not claims that those tests exist or currently pass.

- **RF-1 → AC-1:** Loading offset 50 throws. Older-repetitions copy appears at Load older; existing history stays and retry uses offset 50. Covered by `historyErrorNamesHistory` in the regression step below.
- **RF-2 → AC-2:** Whole-habit delete throws with the habit still present. Deletion-specific recovery appears with no claim that a correction was retained. Covered by `deleteErrorNamesDeletion` in the regression step below.
- **RF-3 → AC-3:** deleteHabit commits then throws; get returns null. Deletion completes; no repeated destructive write is required. Covered by `committedDeleteReconciles` in the regression step below.
- **RF-4 → AC-4:** A repetition correction fails. Correction-specific error appears and selected correction values remain. Covered by `correctionErrorKeepsDraft` in the regression step below.
- **RF-5 → AC-5:** History fails while a management action succeeds, or an old failure arrives late. Only its matching operation clears/replaces feedback; unknown deletion outcome remains honest. Covered by `unrelatedSuccessCannotEraseError` in the regression step below.

---

## Task 1: Deliver UX-015

**Files and responsibilities:**

- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitUiState.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitsViewModel.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitDetailRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitManagementSection.kt`
- Extend test: `app/src/test/java/com/luma/lifeos/ui/habits/HabitsViewModelTest.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/habits/HabitCaptureFlowTest.kt`
- Localized resources, only when copy changes: `app/src/main/res/values/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fr/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fa/strings.xml`

Keep changes in their current screen/state/repository responsibility. New files own only the interface or component named in the specification. If a listed existing file has changed since this plan, reconcile with the live code before editing; do not revert user changes to match the audit.

**Interfaces:**

- Consumes the current route/ViewModel/repository contracts in the files listed above; no new cross-task API is required.
- Produces: HabitDetailOperation { HISTORY, DELETE }; HabitDetailActionError is sealed: History(offset: Int), Delete(habitId: UUID, outcomeUnknown: Boolean). Replace detail.actionError with actionErrors: Map<HabitDetailOperation, HabitDetailActionError>. Keep correctionError: HabitCorrectionError? as the correction-only channel; remove correction failures from the generic detail channel.
- Other consumers: migrate every caller affected by these interface changes; preserve callback target identity and existing readiness/busy guards.

- [x] **Step 1: Read the spec and confirm prerequisites.** Inspect each listed file and the working-tree diff; preserve unrelated edits. No implementation prerequisite is required. Classify missing tests as additions, not evidence of an already passing regression.

- [ ] **Step 2: Add the behavior regressions and manual checks.** Use the listed test suites, keeping state/repository assertions in JVM tests where provided and navigation/Compose assertions in device tests. Use the named cases, fixtures and assertions below; extend existing fixtures instead of introducing a parallel harness. For speech, gesture comfort and visual reachability, automate the semantics/bounds that can be checked and record actual manual device evidence under the same case ID. A test cannot certify TalkBack speech merely by finding a live-region node. For copy-only changes, extend existing UI assertions.

| Case | Regression / evidence case | Fixture / action | Assertions |
|---|---|---|---|
| AC-1 | `historyErrorNamesHistory` | Loading offset 50 throws. | Older-repetitions copy appears at Load older; existing history stays and retry uses offset 50. |
| AC-2 | `deleteErrorNamesDeletion` | Whole-habit delete throws with the habit still present. | Deletion-specific recovery appears with no claim that a correction was retained. |
| AC-3 | `committedDeleteReconciles` | deleteHabit commits then throws; get returns null. | Deletion completes; no repeated destructive write is required. |
| AC-4 | `correctionErrorKeepsDraft` | A repetition correction fails. | Correction-specific error appears and selected correction values remain. |
| AC-5 | `unrelatedSuccessCannotEraseError` | History fails while a management action succeeds, or an old failure arrives late. | Only its matching operation clears/replaces feedback; unknown deletion outcome remains honest. |

- [ ] **Step 3: Establish the regression.** Run the relevant targeted command below before implementation. New-interface tests may initially fail to compile; existing-behavior tests must fail on the specified mismatch. Record the actual failure. For a runtime-risk finding, first exercise its constrained fixture; if it already passes, record that result and retain the reachability regression without claiming a reproduced failure.

```bash
./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.ui.habits.HabitsViewModelTest'
```

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.habits.HabitCaptureFlowTest
```

Device commands require a connected, configured test device/emulator. Locale/font/window and TalkBack settings are deliberate validation inputs; the command alone does not configure or certify that matrix. Do not install dependencies or alter the execution environment.

- [x] **Step 4: Implement the owning contract.** Audit every detail.actionError assignment and replace it with its concrete operation and target; leave correctionError focused on correction.

- [x] **Step 5: Connect the behavior.** Render per-operation copy/Retry and implement delete existence reconciliation before assigning a certain outcome.

- [ ] **Step 6: Exercise the complete flow.** Test independent error clearing, retained history pages and correction draft preservation.

- [ ] **Step 7: Verify the task.** Rerun the targeted commands above and require zero failures. Then run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` to catch all-call-site, resource and lint regressions. Exercise the relevant acceptance scenarios in EN/FR/FA, semantic light/dark, 100%/130%/200% font and narrow/landscape where layout or semantics changed. Record app/build revision, device/API, locale, font/window inputs and results; label missing checks pending. Expected outcome: AC-1 through AC-5 hold and affected compilation/lint checks pass.

- [ ] **Step 8: Review and integrate this task alone.** Inspect the diff against the spec and confirm every AC/RF has evidence. Stage only this task's hunks in existing modified files and its new files; do not stage the pre-existing dirty tree wholesale. If the execution workflow uses commits, commit with `fix: ux-015 habit operation errors`. Update the index status only from actual evidence.

## Handoff

Implementation is complete. Regression tests and runtime/device acceptance remain pending; no Gradle or emulator command was run per request. Do not treat source inspection as runtime acceptance.

