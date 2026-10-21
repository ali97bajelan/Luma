# UX-025 — Restore confirmation has an unbounded warning body Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use superpowers:subagent-driven-development only if the user selects delegation. Steps use checkbox syntax for tracking. This documentation request does not start implementation or delegate work.

**Goal:** Keep complete restore-replacement warnings and confirm/cancel controls reachable in short or large-text layouts.

**Architecture:** Constrain the existing AlertDialog content body and make its warning/count content vertically scrollable. Keep actions in dialog action slots outside the scrolling body.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, lifecycle StateFlow and the existing Android/Room modules; existing dependencies.

**Spec:** [UX-025 specification](../specs/2026-10-20-audit-n-ux-025-restore-dialog-reachability-design.md)

**Status:** Source implementation complete. Automated regression tests, Gradle checks, emulator checks, and commit remain pending at the user's request. Relative effort: Low.

## Global Constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Review Focus

These five concrete failure modes are pinned to the regression cases owned by this task. The assertions below are requirements for tests to add, not claims that those tests exist or currently pass.

- **RF-1 → AC-1:** Restore preview includes counts/date, replacement warning and zero-task warning. Every warning can be scrolled into view. Covered by `allWarningsReachable` in the regression step below.
- **RF-2 → AC-2:** Use a short landscape viewport with 200% font. Confirm and Cancel remain visible/reachable outside body scroll. Covered by `actionsStayReachable` in the regression step below.
- **RF-3 → AC-3:** Show French and Persian preview warnings. Text wraps without clipping or shrinking and traversal order remains logical. Covered by `longTranslationsFit` in the regression step below.
- **RF-4 → AC-4:** Press Back, tap Cancel or dismiss as currently supported. Prepared restore is cancelled safely and no replacement commit occurs. Covered by `dismissDoesNotRestore` in the regression step below.
- **RF-5 → AC-5:** Compare nonzero-task and zero-task preview fixtures. All original consequences and conditional warnings remain accurate; no destructive action is implied by scrolling. Covered by `warningMeaningIsPreserved` in the regression step below.

---

## Task 1: Deliver UX-025

**Files and responsibilities:**

- Modify: `app/src/main/java/com/luma/lifeos/ui/backup/BackupSettingsCard.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/backup/BackupSettingsFlowTest.kt`
- Localized resources, only when copy changes: `app/src/main/res/values/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fr/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fa/strings.xml`

Keep changes in their current screen/state/repository responsibility. New files own only the interface or component named in the specification. If a listed existing file has changed since this plan, reconcile with the live code before editing; do not revert user changes to match the audit.

**Interfaces:**

- Consumes the current route/ViewModel/repository contracts in the files listed above; no new cross-task API is required.
- Produces: BackupCardContent retains existing onConfirmRestore/onCancelRestore callbacks. Dialog body uses a remembered vertical scroll state within dialog-measured available height; warnings keep separate semantics nodes and actions remain outside the body scroll.
- Other consumers: migrate every caller affected by these interface changes; preserve callback target identity and existing readiness/busy guards.

- [x] **Step 1: Read the spec and confirm prerequisites.** Inspect each listed file and the working-tree diff; preserve unrelated edits. No implementation prerequisite is required. Classify missing tests as additions, not evidence of an already passing regression.

- [ ] **Step 2: Add the behavior regressions and manual checks.** Use the listed test suites, keeping state/repository assertions in JVM tests where provided and navigation/Compose assertions in device tests. Use the named cases, fixtures and assertions below; extend existing fixtures instead of introducing a parallel harness. For speech, gesture comfort and visual reachability, automate the semantics/bounds that can be checked and record actual manual device evidence under the same case ID. A test cannot certify TalkBack speech merely by finding a live-region node. For copy-only changes, extend existing UI assertions.

| Case | Regression / evidence case | Fixture / action | Assertions |
|---|---|---|---|
| AC-1 | `allWarningsReachable` | Restore preview includes counts/date, replacement warning and zero-task warning. | Every warning can be scrolled into view. |
| AC-2 | `actionsStayReachable` | Use a short landscape viewport with 200% font. | Confirm and Cancel remain visible/reachable outside body scroll. |
| AC-3 | `longTranslationsFit` | Show French and Persian preview warnings. | Text wraps without clipping or shrinking and traversal order remains logical. |
| AC-4 | `dismissDoesNotRestore` | Press Back, tap Cancel or dismiss as currently supported. | Prepared restore is cancelled safely and no replacement commit occurs. |
| AC-5 | `warningMeaningIsPreserved` | Compare nonzero-task and zero-task preview fixtures. | All original consequences and conditional warnings remain accurate; no destructive action is implied by scrolling. |

- [ ] **Step 3: Establish the regression.** Run the relevant targeted command below before implementation. New-interface tests may initially fail to compile; existing-behavior tests must fail on the specified mismatch. Record the actual failure. For a runtime-risk finding, first exercise its constrained fixture; if it already passes, record that result and retain the reachability regression without claiming a reproduced failure.

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.backup.BackupSettingsFlowTest
```

Device commands require a connected, configured test device/emulator. Locale/font/window and TalkBack settings are deliberate validation inputs; the command alone does not configure or certify that matrix. Do not install dependencies or alter the execution environment as part of this documentation task.

- [x] **Step 4: Implement the owning contract.** Add constrained scrollable warning content to the existing dialog without moving or omitting warnings.

- [x] **Step 5: Connect the behavior.** Keep confirm/cancel outside scroll content and make their action arrangement fit translated labels.

- [ ] **Step 6: Exercise the complete flow.** Validate all warning combinations with a short landscape viewport, 200% font and EN/FR/FA.

- [ ] **Step 7: Verify the task.** Rerun the targeted commands above and require zero failures. Then run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` to catch all-call-site, resource and lint regressions. Exercise the relevant acceptance scenarios in EN/FR/FA, semantic light/dark, 100%/130%/200% font and narrow/landscape where layout or semantics changed. Record app/build revision, device/API, locale, font/window inputs and results; label missing checks pending. Expected outcome: AC-1 through AC-5 hold and affected compilation/lint checks pass.

- [ ] **Step 8: Review and integrate this task alone.** Inspect the diff against the spec and confirm every AC/RF has evidence. Stage only this task's hunks in existing modified files and its new files; do not stage the pre-existing dirty tree wholesale. If the execution workflow uses commits, commit with `fix: ux-025 restore dialog reachability`. Update the index status only from actual evidence.

## Handoff

Source implementation is complete. Automated regression and device evidence remain pending at the user's request; do not mark the finding verified until those checks pass.
