# UX-024 — Leaving during backup has no explicit operation outcome Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use superpowers:subagent-driven-development only if the user selects delegation. Steps use checkbox syntax for tracking. This documentation request does not start implementation or delegate work.

**Goal:** Give export, restore preparation and protected restore an explicit navigation/cancellation outcome.

**Architecture:** Keep route-owned work but intercept Back before leaving. Cancellable export/preparation requires an explicit cancel-and-leave decision; await closure/cleanup before navigation. Committed restore remains protected and reports completion before leaving.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, lifecycle StateFlow and the existing Android/Room modules; existing dependencies.

**Spec:** [UX-024 specification](../specs/2026-10-20-audit-n-ux-024-backup-departure-outcome-design.md)

**Status:** Source implementation complete. Automated regression tests, Gradle checks, emulator checks, and commit remain pending at the user's request. Relative effort: Medium.

## Global Constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Review Focus

These five concrete failure modes are pinned to the regression cases owned by this task. The assertions below are requirements for tests to add, not claims that those tests exist or currently pass.

- **RF-1 → AC-1:** Use toolbar or system Back while an export stream is open. Both show Stay/Cancel and leave; navigation has not popped the work owner. Covered by `backDuringExportAsks` in the regression step below.
- **RF-2 → AC-2:** Confirm leave with deletion-supported provider. Job is cancelled/joined, stream closes, destination cleanup finishes and cancellation outcome is retained. Covered by `confirmedCancellationClosesAndCleans` in the regression step below.
- **RF-3 → AC-3:** Provider cannot delete the incomplete document. Departure feedback says an incomplete backup may remain; no success is reported. Covered by `cleanupFailureIsExplicit` in the regression step below.
- **RF-4 → AC-4:** Press Back during protected restore commit. The route remains, the commit continues and its real result is shown. Covered by `restoreCannotBeCancelledByBack` in the regression step below.
- **RF-5 → AC-5:** Export succeeds while leave confirmation is open; then confirm leave. Completed file is not deleted; completion result wins and prepared resources are closed exactly once. Covered by `completionRacePreservesBackup` in the regression step below.

---

## Task 1: Deliver UX-024

**Files and responsibilities:**

- Modify: `app/src/main/java/com/luma/lifeos/ui/backup/BackupViewModel.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/backup/BackupSettingsCard.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/settings/SettingsRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`
- Extend test: `app/src/test/java/com/luma/lifeos/backup/BackupViewModelTest.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/backup/BackupSettingsFlowTest.kt`
- Localized resources, only when copy changes: `app/src/main/res/values/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fr/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fa/strings.xml`

Keep changes in their current screen/state/repository responsibility. New files own only the interface or component named in the specification. If a listed existing file has changed since this plan, reconcile with the live code before editing; do not revert user changes to match the audit.

**Interfaces:**

- Consumes the current route/ViewModel/repository contracts in the files listed above; no new cross-task API is required.
- Produces: BackupOperation { IDLE, EXPORTING, PREPARING, RESTORING, CANCELLING }; BackupUiState gains operation. Track the active Job. export(open: () -> OutputStream, discardOutput: suspend () -> Boolean) records cleanup. cancelAndAwait() returns BackupDeparture(operation, result) after the active cancellable job/stream closes and cleanup completes. Results distinguish clean cancellation, a possible remaining output file, completion, failure, and protected restore-in-progress. Navigation serializes the leave result into the source entry's saved-state feedback.
- Other consumers: migrate every caller affected by these interface changes; preserve callback target identity and existing readiness/busy guards.

- [x] **Step 1: Read the spec and confirm prerequisites.** Inspect each listed file and the working-tree diff; preserve unrelated edits. No implementation prerequisite is required. Classify missing tests as additions, not evidence of an already passing regression.

- [ ] **Step 2: Add the behavior regressions and manual checks.** Use the listed test suites, keeping state/repository assertions in JVM tests where provided and navigation/Compose assertions in device tests. Use the named cases, fixtures and assertions below; extend existing fixtures instead of introducing a parallel harness. For speech, gesture comfort and visual reachability, automate the semantics/bounds that can be checked and record actual manual device evidence under the same case ID. A test cannot certify TalkBack speech merely by finding a live-region node. For copy-only changes, extend existing UI assertions.

| Case | Regression / evidence case | Fixture / action | Assertions |
|---|---|---|---|
| AC-1 | `backDuringExportAsks` | Use toolbar or system Back while an export stream is open. | Both show Stay/Cancel and leave; navigation has not popped the work owner. |
| AC-2 | `confirmedCancellationClosesAndCleans` | Confirm leave with deletion-supported provider. | Job is cancelled/joined, stream closes, destination cleanup finishes and cancellation outcome is retained. |
| AC-3 | `cleanupFailureIsExplicit` | Provider cannot delete the incomplete document. | Departure feedback says an incomplete backup may remain; no success is reported. |
| AC-4 | `restoreCannotBeCancelledByBack` | Press Back during protected restore commit. | The route remains, the commit continues and its real result is shown. |
| AC-5 | `completionRacePreservesBackup` | Export succeeds while leave confirmation is open; then confirm leave. | Completed file is not deleted; completion result wins and prepared resources are closed exactly once. |

- [ ] **Step 3: Establish the regression.** Run the relevant targeted command below before implementation. New-interface tests may initially fail to compile; existing-behavior tests must fail on the specified mismatch. Record the actual failure. For a runtime-risk finding, first exercise its constrained fixture; if it already passes, record that result and retain the reachability regression without claiming a reproduced failure.

```bash
./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.backup.BackupViewModelTest'
```

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.backup.BackupSettingsFlowTest
```

Device commands require a connected, configured test device/emulator. Locale/font/window and TalkBack settings are deliberate validation inputs; the command alone does not configure or certify that matrix. Do not install dependencies or alter the execution environment as part of this documentation task.

- [x] **Step 4: Implement the owning contract.** Expose operation identity and track active export/preparation jobs with awaitable cancellation and provider cleanup callbacks.

- [x] **Step 5: Connect the behavior.** Intercept page/system Back, implement Stay/Cancel-and-leave states and route protected restore Back through its status message.

- [ ] **Step 6: Exercise the complete flow.** Deliver actual completion/cancellation cleanup outcome to source settings feedback, handling completion-versus-cancel races.

- [ ] **Step 7: Verify the task.** Rerun the targeted commands above and require zero failures. Then run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` to catch all-call-site, resource and lint regressions. Exercise the relevant acceptance scenarios in EN/FR/FA, semantic light/dark, 100%/130%/200% font and narrow/landscape where layout or semantics changed. Record app/build revision, device/API, locale, font/window inputs and results; label missing checks pending. Expected outcome: AC-1 through AC-5 hold and affected compilation/lint checks pass.

- [ ] **Step 8: Review and integrate this task alone.** Inspect the diff against the spec and confirm every AC/RF has evidence. Stage only this task's hunks in existing modified files and its new files; do not stage the pre-existing dirty tree wholesale. If the execution workflow uses commits, commit with `fix: ux-024 backup departure outcome`. Update the index status only from actual evidence.

## Handoff

Source implementation is complete. Automated regression and device evidence remain pending at the user's request; do not mark the finding verified until those checks pass.
