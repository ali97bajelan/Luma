# UX-013 — A historical habit link does not locate the requested repetition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use superpowers:subagent-driven-development only if the user selects delegation. Steps use checkbox syntax for tracking. This documentation request does not start implementation or delegate work.

**Goal:** Reveal the exact historical repetition selected from Timeline even when it is older than the first 50 history rows.

**Architecture:** Resolve selectedLogId independently using existing HabitRepository.getLog. Show the target in a dedicated arrival section before bounded paged history and omit its duplicate from regular rows.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, lifecycle StateFlow and the existing Android/Room modules; existing dependencies.

**Spec:** [UX-013 specification](../specs/2026-10-20-audit-n-ux-013-habit-targeted-history-design.md)

**Status:** Draft plan. All implementation, test and commit steps remain unchecked. Relative effort: Medium.

## Global Constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Review Focus

These five concrete failure modes are pinned to the regression cases owned by this task. The assertions below are requirements for tests to add, not claims that those tests exist or currently pass.

- **RF-1 → AC-1:** Open a target older than 50 lifetime logs. The exact UUID appears in the selected section and is brought into view. Covered by `oldTargetIsShown` in the regression step below.
- **RF-2 → AC-2:** The selected target also belongs to a loaded history page. It is rendered once while history offset/total remain based on repository records. Covered by `targetIsNotDuplicated` in the regression step below.
- **RF-3 → AC-3:** getLog returns null or a log owned by another habit. Target unavailable appears; no unrelated repetition is selected. Covered by `missingOrWrongHabitIsHonest` in the regression step below.
- **RF-4 → AC-4:** Navigate to another habit/target before an old lookup finishes. Late results are ignored and arrival/focus names the current target only. Covered by `lateTargetCannotReplaceNewNavigation` in the regression step below.
- **RF-5 → AC-5:** Correct then delete the selected repetition. The selected card updates by ID; deletion becomes unavailable; announcement is not repeated on unrelated refresh. Covered by `correctionAndDeleteRefreshTarget` in the regression step below.

---

## Task 1: Deliver UX-013

**Files and responsibilities:**

- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitUiState.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitsViewModel.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitDetailRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitHistory.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`
- Extend test: `app/src/test/java/com/luma/lifeos/ui/habits/HabitsViewModelTest.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/habits/HabitTimelineFlowTest.kt`
- Localized resources, only when copy changes: `app/src/main/res/values/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fr/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fa/strings.xml`

Keep changes in their current screen/state/repository responsibility. New files own only the interface or component named in the specification. If a listed existing file has changed since this plan, reconcile with the live code before editing; do not revert user changes to match the audit.

**Interfaces:**

- Consumes the current route/ViewModel/repository contracts in the files listed above; no new cross-task API is required.
- Produces: HabitDetailUiState gains selectedLogId: UUID?, selectedLog: HabitLog?, selectedLogStatus: HabitTargetStatus where status is NONE/LOADING/FOUND/MISSING/ERROR. detail target loading uses HabitRepository.getLog(id) and validates log.habitId. No new repository history-window API is required.
- Other consumers: migrate every caller affected by these interface changes; preserve callback target identity and existing readiness/busy guards.

- [ ] **Step 1: Read the spec and confirm prerequisites.** Inspect each listed file and the working-tree diff; preserve unrelated edits. No implementation prerequisite is required. Classify missing tests as additions, not evidence of an already passing regression.

- [ ] **Step 2: Add the behavior regressions and manual checks.** Use the listed test suites, keeping state/repository assertions in JVM tests where provided and navigation/Compose assertions in device tests. Use the named cases, fixtures and assertions below; extend existing fixtures instead of introducing a parallel harness. For speech, gesture comfort and visual reachability, automate the semantics/bounds that can be checked and record actual manual device evidence under the same case ID. A test cannot certify TalkBack speech merely by finding a live-region node. For copy-only changes, extend existing UI assertions.

| Case | Regression / evidence case | Fixture / action | Assertions |
|---|---|---|---|
| AC-1 | `oldTargetIsShown` | Open a target older than 50 lifetime logs. | The exact UUID appears in the selected section and is brought into view. |
| AC-2 | `targetIsNotDuplicated` | The selected target also belongs to a loaded history page. | It is rendered once while history offset/total remain based on repository records. |
| AC-3 | `missingOrWrongHabitIsHonest` | getLog returns null or a log owned by another habit. | Target unavailable appears; no unrelated repetition is selected. |
| AC-4 | `lateTargetCannotReplaceNewNavigation` | Navigate to another habit/target before an old lookup finishes. | Late results are ignored and arrival/focus names the current target only. |
| AC-5 | `correctionAndDeleteRefreshTarget` | Correct then delete the selected repetition. | The selected card updates by ID; deletion becomes unavailable; announcement is not repeated on unrelated refresh. |

- [ ] **Step 3: Establish the regression.** Run the relevant targeted command below before implementation. New-interface tests may initially fail to compile; existing-behavior tests must fail on the specified mismatch. Record the actual failure. For a runtime-risk finding, first exercise its constrained fixture; if it already passes, record that result and retain the reachability regression without claiming a reproduced failure.

```bash
./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.ui.habits.HabitsViewModelTest'
```

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.habits.HabitTimelineFlowTest
```

Device commands require a connected, configured test device/emulator. Locale/font/window and TalkBack settings are deliberate validation inputs; the command alone does not configure or certify that matrix. Do not install dependencies or alter the execution environment as part of this documentation task.

- [ ] **Step 4: Implement the owning contract.** Load and validate selectedLogId independently of initial history with generation-safe target state.

- [ ] **Step 5: Connect the behavior.** Render the selected arrival section and stable scroll target, selected semantics and one-time announcement; deduplicate regular history.

- [ ] **Step 6: Exercise the complete flow.** Refresh the target after correction/deletion and preserve independent history/target retry paths.

- [ ] **Step 7: Verify the task.** Rerun the targeted commands above and require zero failures. Then run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` to catch all-call-site, resource and lint regressions. Exercise the relevant acceptance scenarios in EN/FR/FA, semantic light/dark, 100%/130%/200% font and narrow/landscape where layout or semantics changed. Record app/build revision, device/API, locale, font/window inputs and results; label missing checks pending. Expected outcome: AC-1 through AC-5 hold and affected compilation/lint checks pass.

- [ ] **Step 8: Review and integrate this task alone.** Inspect the diff against the spec and confirm every AC/RF has evidence. Stage only this task's hunks in existing modified files and its new files; do not stage the pre-existing dirty tree wholesale. If the execution workflow uses commits, commit with `fix: ux-013 habit targeted history`. Update the index status only from actual evidence.

## Handoff

Deliver this task's code changes, five scenario results and remaining device limitations together. No implementation has been performed by generating this plan. Do not mark the finding fixed solely because a plan, test name or historical screenshot exists.

