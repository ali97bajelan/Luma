# UX-008 — Retained task errors can crowd out the task list Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use superpowers:subagent-driven-development only if the user selects delegation. Steps use checkbox syntax for tracking. Implementation was completed inline; no delegation or commit was performed.

**Goal:** Keep tasks and every retained recovery action reachable when many task operations fail.

**Architecture:** Keep search, filters, loading feedback, recovery cards and task rows in scrollable content when fixed controls would leave no usable list viewport. In loading-only state, show controls in a scrollable fallback and leave the keyed LazyList uncomposed to preserve its saved position.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, lifecycle StateFlow and the existing Android/Room modules; existing dependencies.

**Spec:** [UX-008 specification](../specs/2026-10-20-audit-n-ux-008-task-recovery-list-design.md)

**Status:** Implemented / acceptance partial. AC-1 passes 1/1 on API 36; AC-2–AC-5 and the wider device/accessibility matrix remain pending. No commit was created. Relative effort: Low.

## Global Constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Review Focus

These five concrete failure modes are pinned to regression cases owned by this task. Coverage is present for retained recovery reachability, Undo, stable item identity and task paging. AC-1 passed its exact connected regression on 23 November 2026; AC-2–AC-5 remain not run.

- **RF-1 → AC-1:** Render 12 off-page failures at 200% text in landscape. Scroll reaches every recovery action and at least one task without clipped pinned content. Covered by `manyFailuresDoNotHideTasks` in the regression step below.
- **RF-2 → AC-2:** A completed task moves off-page and exposes Undo. Undo is a reachable stable lazy item with the original token. Covered by `undoRemainsDiscoverable` in the regression step below.
- **RF-3 → AC-3:** Switch Open/Completed and due filters with retained failures. No command disappears or changes identity. Covered by `filterChangeKeepsActions` in the regression step below.
- **RF-4 → AC-4:** Emit an unrelated task change while scrolled. Stable item identity preserves the current content position and focus. Covered by `recompositionKeepsScroll` in the regression step below.
- **RF-5 → AC-5:** Insert recovery items before a 50-record task page. Load-more requests use task page boundaries, without skipped or duplicate tasks. Covered by `feedbackDoesNotAdvancePaging` in the regression step below.

---

## Task 1: Deliver UX-008

**Files and responsibilities:**

- Modify: `app/src/main/java/com/luma/lifeos/ui/todo/TodoListScreen.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/todo/TodoActionFeedback.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/todo/TodoAccessibilityTest.kt`
- Extend test: `app/src/test/java/com/luma/lifeos/ui/todo/TodoListViewModelTest.kt`
- Localized resources, only when copy changes: `app/src/main/res/values/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fr/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fa/strings.xml`

Keep changes in their current screen/state/repository responsibility. New files own only the interface or component named in the specification. If a listed existing file has changed since this plan, reconcile with the live code before editing; do not revert user changes to match the audit.

**Interfaces:**

- Consumes the current route/ViewModel/repository contracts in the files listed above; no new cross-task API is required.
- Produces: Use lazy item keys prefixed recovery:<existing-action-id>, undo:<existing-undo-id>, bucket:<bucket>, task:<task-id>. Reuse existing Retry/Open/Dismiss callbacks; do not generate replacement operation IDs during recomposition.
- Other consumers: migrate every caller affected by these interface changes; preserve callback target identity and existing readiness/busy guards.

- [x] **Step 1: Read the spec and confirm prerequisites.** Inspect each listed file and the working-tree diff; preserve unrelated edits. No implementation prerequisite is required. Classify missing tests as additions, not evidence of an already passing regression.

- [x] **Step 2: Add the behavior regressions and manual checks.** Regression coverage is present for the changed list and recovery behavior. AC-1 passes 1/1; AC-2–AC-5, TalkBack and broader layout acceptance remain pending.

| Case | Regression / evidence case | Fixture / action | Assertions | Result |
|---|---|---|---|---|
| AC-1 | `manyFailuresDoNotHideTasks` | Render 12 off-page failures at 200% text in landscape. | Scroll reaches every recovery action and at least one task without clipped pinned content. | PASS 1/1; API 36 retest saved in the QA-01 report. |
| AC-2 | `undoRemainsDiscoverable` | A completed task moves off-page and exposes Undo. | Undo is a reachable stable lazy item with the original token. | NOT RUN |
| AC-3 | `filterChangeKeepsActions` | Switch Open/Completed and due filters with retained failures. | No command disappears or changes identity. | NOT RUN |
| AC-4 | `recompositionKeepsScroll` | Emit an unrelated task change while scrolled. | Stable item identity preserves the current content position and focus. | NOT RUN |
| AC-5 | `feedbackDoesNotAdvancePaging` | Insert recovery items before a 50-record task page. | Load-more requests use task page boundaries, without skipped or duplicate tasks. | NOT RUN |

- [x] **Step 3: Establish and rerun the regression.** The pre-fix AC-1 assertion failure is preserved in the 2026-11-23 QA report. After moving controls into the scrollable content and switching the case to stable-tag scrolling, the exact method passes 1/1 on API 36. AC-2–AC-5 remain unverified.

```bash
./gradlew :app:testProductionDebugUnitTest --tests 'com.luma.lifeos.ui.todo.TodoListViewModelTest'
```

```bash
./gradlew :app:connectedProductionDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.todo.TodoAccessibilitySemanticsTest#manyFailuresDoNotHideTasks
```

Device commands require a connected, configured test device/emulator. Locale/font/window and TalkBack settings are deliberate validation inputs; the command alone does not configure or certify that matrix. Do not install dependencies or alter the execution environment as part of this documentation task.

- [x] **Step 4: Implement the owning contract.** Keep controls and recovery/task content in scrollable content at constrained sizes, with recovery/Undo as stable LazyColumn items and separate key namespaces.

- [x] **Step 5: Connect the behavior.** Adjust task paging/scroll and focus targets to account for inserted feedback items while preserving all retained commands.

- [x] **Step 6: Exercise the constrained recovery flow.** The API 36 AC-1 test uses a 640×320 dp viewport with font scale 2.0 and reaches both the final recovery card and a task action.

- [ ] **Step 7: Verify the full task.** AC-1 is verified; AC-2–AC-5, TalkBack, targeted wider suites and EN/FR/FA, theme, font/window and device acceptance remain pending.

- [ ] **Step 8: Review and integrate this task alone.** Inspect the diff against the spec and confirm every AC/RF has evidence. Stage only this task's hunks in existing modified files and its new files; do not stage the pre-existing dirty tree wholesale. If the execution workflow uses commits, use `fix(todo): keep recovery rows reachable at large text sizes`. Update the index status only from actual evidence.

## Handoff

Implementation and regression coverage are present. AC-1 passes its exact connected test; AC-2–AC-5 and wider runtime/accessibility acceptance remain pending. No commit was created. Do not mark the full finding Verified until the remaining checks are completed.

