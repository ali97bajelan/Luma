# UX-018 — Several important asynchronous results lack announcement semantics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use superpowers:subagent-driven-development only if the user selects delegation. Steps use checkbox syntax for tracking. This documentation request does not start implementation or delegate work.

**Goal:** Make important asynchronous results and errors discoverable through consistent live feedback semantics.

**Architecture:** Use existing AccessibleFeedback at scoped asynchronous result sites. Classify success versus error and keep Retry adjacent without making entire pages live regions.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, lifecycle StateFlow and the existing Android/Room modules; existing dependencies.

**Spec:** [UX-018 specification](../specs/2026-10-20-audit-n-ux-018-accessible-async-feedback-design.md)

**Status:** Implementation complete; regression tests and runtime verification remain pending at the user's request. Relative effort: Low.

## Global Constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Review Focus

These five concrete failure modes are pinned to the regression cases owned by this task. The assertions below are requirements for tests to add, not claims that those tests exist or currently pass.

- **RF-1 → AC-1:** Finish export successfully. Completion has polite live-region semantics and no error semantic. Covered by `backupSuccessIsNotError` in the regression step below.
- **RF-2 → AC-2:** Fail Today, Timeline, Summary or Event detail loading. Scoped feedback has error semantics and available Retry stays adjacent. Covered by `failureHasRetryAndSemantics` in the regression step below.
- **RF-3 → AC-3:** Language change fails. The failure is exposed as scoped live feedback without resetting unrelated focus. Covered by `languageFailureIsAnnounceable` in the regression step below.
- **RF-4 → AC-4:** Recompose or refresh with the same result repeatedly. No new app-driven announcement event is emitted for unchanged text. Covered by `unchangedRefreshIsQuiet` in the regression step below.
- **RF-5 → AC-5:** Exercise success/error transitions in EN/FR/FA. TalkBack announces the new outcome once and preserves usable focus; runtime evidence is recorded. Covered by `talkBackReadsOnceWithoutFocusJump` in the regression step below.

---

## Task 1: Deliver UX-018

**Files and responsibilities:**

- Modify: `app/src/main/java/com/luma/lifeos/ui/backup/BackupSettingsCard.kt`
- Modify: `app/src/main/java/com/luma/lifeos/language/LanguageSettingsCard.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/timeline/TimelineRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/summary/DailySummaryCard.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/timeline/EventDetailRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/common/AccessibleFeedback.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/FeedbackAccessibilityTest.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/backup/BackupSettingsFlowTest.kt`
- Localized resources, only when copy changes: `app/src/main/res/values/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fr/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fa/strings.xml`

Keep changes in their current screen/state/repository responsibility. New files own only the interface or component named in the specification. If a listed existing file has changed since this plan, reconcile with the live code before editing; do not revert user changes to match the audit.

**Interfaces:**

- Consumes the current route/ViewModel/repository contracts in the files listed above; no new cross-task API is required.
- Produces: Reuse AccessibleFeedback(message: String, modifier: Modifier = Modifier, isError: Boolean = true) and its existing polite/error semantics. Call sites explicitly set isError=false for successful completion. Feedback identities follow operation/status transitions rather than each recomposition.
- Other consumers: migrate every caller affected by these interface changes; preserve callback target identity and existing readiness/busy guards.

- [x] **Step 1: Read the spec and confirm prerequisites.** Inspect each listed file and the working-tree diff; preserve unrelated edits. No implementation prerequisite is required. Classify missing tests as additions, not evidence of an already passing regression.

- [ ] **Step 2: Add the behavior regressions and manual checks.** Use the listed test suites, keeping state/repository assertions in JVM tests where provided and navigation/Compose assertions in device tests. Use the named cases, fixtures and assertions below; extend existing fixtures instead of introducing a parallel harness. For speech, gesture comfort and visual reachability, automate the semantics/bounds that can be checked and record actual manual device evidence under the same case ID. A test cannot certify TalkBack speech merely by finding a live-region node. For copy-only changes, extend existing UI assertions.

| Case | Regression / evidence case | Fixture / action | Assertions |
|---|---|---|---|
| AC-1 | `backupSuccessIsNotError` | Finish export successfully. | Completion has polite live-region semantics and no error semantic. |
| AC-2 | `failureHasRetryAndSemantics` | Fail Today, Timeline, Summary or Event detail loading. | Scoped feedback has error semantics and available Retry stays adjacent. |
| AC-3 | `languageFailureIsAnnounceable` | Language change fails. | The failure is exposed as scoped live feedback without resetting unrelated focus. |
| AC-4 | `unchangedRefreshIsQuiet` | Recompose or refresh with the same result repeatedly. | No new app-driven announcement event is emitted for unchanged text. |
| AC-5 | `talkBackReadsOnceWithoutFocusJump` | Exercise success/error transitions in EN/FR/FA. | TalkBack announces the new outcome once and preserves usable focus; runtime evidence is recorded. |

- [ ] **Step 3: Establish the regression.** Run the relevant targeted command below before implementation. New-interface tests may initially fail to compile; existing-behavior tests must fail on the specified mismatch. Record the actual failure. For a runtime-risk finding, first exercise its constrained fixture; if it already passes, record that result and retain the reachability regression without claiming a reproduced failure.

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.FeedbackAccessibilityTest,com.luma.lifeos.backup.BackupSettingsFlowTest
```

Device commands require a connected, configured test device/emulator. Locale/font/window and TalkBack settings are deliberate validation inputs; the command alone does not configure or certify that matrix. Do not install dependencies or alter the execution environment as part of this documentation task.

- [x] **Step 4: Implement the owning contract.** Inventory specified result/error branches and replace ordinary result Text with correctly classified AccessibleFeedback.

- [x] **Step 5: Connect the behavior.** Keep feedback scoped to changed operation outcomes and remove nested/duplicate live regions.

- [ ] **Step 6: Exercise the complete flow.** Verify semantics for every covered family, then verify actual TalkBack speech/focus for success, failure and unchanged refresh.

- [ ] **Step 7: Verify the task.** Rerun the targeted commands above and require zero failures. Then run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` to catch all-call-site, resource and lint regressions. Exercise the relevant acceptance scenarios in EN/FR/FA, semantic light/dark, 100%/130%/200% font and narrow/landscape where layout or semantics changed. Record app/build revision, device/API, locale, font/window inputs and results; label missing checks pending. Expected outcome: AC-1 through AC-5 hold and affected compilation/lint checks pass.

- [ ] **Step 8: Review and integrate this task alone.** Inspect the diff against the spec and confirm every AC/RF has evidence. Stage only this task's hunks in existing modified files and its new files; do not stage the pre-existing dirty tree wholesale. If the execution workflow uses commits, commit with `fix: ux-018 accessible async feedback`. Update the index status only from actual evidence.

## Handoff

Deliver this task's code changes and outstanding verification together. No Gradle or emulator checks were run, and no new tests were added, at the user's request. Do not mark runtime acceptance complete solely from source changes.
