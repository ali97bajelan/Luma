# UX-007 — A conflicted Today task cannot be opened for review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use superpowers:subagent-driven-development only if the user selects delegation. Steps use checkbox syntax for tracking. Implementation was completed inline; no delegation or commit was performed.

**Goal:** Let users open an existing task in conflict while keeping unsafe completion actions disabled.

**Architecture:** Split task-opening eligibility from mutation eligibility in TodoRow and all callers. Use the same Open recovery action for visible and off-preview conflicts.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, lifecycle StateFlow and the existing Android/Room modules; existing dependencies.

**Spec:** [UX-007 specification](../specs/2026-10-20-audit-n-ux-007-task-conflict-opening-design.md)

**Status:** Implemented / acceptance pending. Regression coverage was added but not run; Gradle, emulator and device acceptance remain pending per request. No commit was created. Relative effort: Low.

## Global Constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Review Focus

These five concrete failure modes are pinned to regression cases owned by this task. Coverage was added for visible and retained conflict opening, missing-task guards and dismiss-without-write; tests have not been run.

- **RF-1 → AC-1:** Render an existing task with CONFLICT and tap Open. The matching editor opens; completion remains disabled. Covered by `existingConflictCanOpen` in the regression step below.
- **RF-2 → AC-2:** Render MISSING feedback. Neither task open nor completion can dispatch. Covered by `missingTaskCannotOpen` in the regression step below.
- **RF-3 → AC-3:** Tap completion repeatedly during a pending request. No extra mutation is issued. Covered by `pendingDoesNotDuplicateCompletion` in the regression step below.
- **RF-4 → AC-4:** Move a conflicted task out of the preview. Both presentations navigate to the same task ID. Covered by `visibleAndRetainedRecoveryAgree` in the regression step below.
- **RF-5 → AC-5:** Dismiss CONFLICT without opening. Only feedback changes; repository mutation call count stays unchanged. Covered by `dismissDoesNotWrite` in the regression step below.

---

## Task 1: Deliver UX-007

**Files and responsibilities:**

- Modify: `app/src/main/java/com/luma/lifeos/ui/todo/TodoRow.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/todo/TodayTasksCard.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/todo/TodoListScreen.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/todo/TodayTasksSemanticsTest.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/todo/TodayTasksFlowTest.kt`
- Localized resources, only when copy changes: `app/src/main/res/values/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fr/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fa/strings.xml`

Keep changes in their current screen/state/repository responsibility. New files own only the interface or component named in the specification. If a listed existing file has changed since this plan, reconcile with the live code before editing; do not revert user changes to match the audit.

**Interfaces:**

- Consumes the current route/ViewModel/repository contracts in the files listed above; no new cross-task API is required.
- Produces: TodoRow replaces enabled: Boolean with canOpen: Boolean and canMutate: Boolean; pending remains an independent guard. onOpen is available for existing conflict targets, while onSetCompleted remains blocked by conflict/pending. Missing targets have canOpen=false.
- Other consumers: migrate every caller affected by these interface changes; preserve callback target identity and existing readiness/busy guards.

- [x] **Step 1: Read the spec and confirm prerequisites.** Inspect each listed file and the working-tree diff; preserve unrelated edits. No implementation prerequisite is required. Classify missing tests as additions, not evidence of an already passing regression.

- [x] **Step 2: Add the behavior regressions and manual checks.** Regression coverage was added for the changed conflict/opening behavior. The tests and device/manual checks listed here have not been run; TalkBack and layout acceptance remain pending.

| Case | Regression / evidence case | Fixture / action | Assertions |
|---|---|---|---|
| AC-1 | `existingConflictCanOpen` | Render an existing task with CONFLICT and tap Open. | The matching editor opens; completion remains disabled. |
| AC-2 | `missingTaskCannotOpen` | Render MISSING feedback. | Neither task open nor completion can dispatch. |
| AC-3 | `pendingDoesNotDuplicateCompletion` | Tap completion repeatedly during a pending request. | No extra mutation is issued. |
| AC-4 | `visibleAndRetainedRecoveryAgree` | Move a conflicted task out of the preview. | Both presentations navigate to the same task ID. |
| AC-5 | `dismissDoesNotWrite` | Dismiss CONFLICT without opening. | Only feedback changes; repository mutation call count stays unchanged. |

- [ ] **Step 3: Establish the regression.** Not run per request. The listed Gradle command remains available for later acceptance; no failure/pass result is claimed.

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.todo.TodayTasksSemanticsTest,com.luma.lifeos.todo.TodayTasksFlowTest
```

Device commands require a connected, configured test device/emulator. Locale/font/window and TalkBack settings are deliberate validation inputs; the command alone does not configure or certify that matrix. Do not install dependencies or alter the execution environment as part of this documentation task.

- [x] **Step 4: Implement the owning contract.** Change TodoRow's API and migrate Today/full-list call sites with explicit open and mutate predicates.

- [x] **Step 5: Connect the behavior.** Wire visible conflict Open to existing task navigation and preserve missing/pending guards.

- [ ] **Step 6: Exercise the complete flow.** Verify visible and off-preview conflicts use the same task identity and recovery behavior.

- [ ] **Step 7: Verify the task.** Not run per request. Targeted Gradle tests/build/lint and EN/FR/FA, theme, font/window and device acceptance remain pending; do not mark AC-1 through AC-5 verified.

- [ ] **Step 8: Review and integrate this task alone.** Inspect the diff against the spec and confirm every AC/RF has evidence. Stage only this task's hunks in existing modified files and its new files; do not stage the pre-existing dirty tree wholesale. If the execution workflow uses commits, commit with `fix: ux-007 task conflict opening`. Update the index status only from actual evidence.

## Handoff

Implementation and regression coverage are present. Runtime and accessibility acceptance remain pending because Gradle/emulator/device checks were not run per request. No commit was created. Do not mark the finding Verified until the remaining checks are completed.

