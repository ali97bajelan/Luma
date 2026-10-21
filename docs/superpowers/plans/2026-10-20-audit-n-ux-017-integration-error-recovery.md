# UX-017 — Successful integration refresh leaves obsolete error text Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use superpowers:subagent-driven-development only if the user selects delegation. Steps use checkbox syntax for tracking. This documentation request does not start implementation or delegate work.

**Goal:** Clear stale integration feedback only when the operation that failed has recovered.

**Architecture:** Give Usage and Health settings separate observation, access-refresh, import and preference error channels. Render and retry each channel independently while preserving access/import consent boundaries.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, lifecycle StateFlow and the existing Android/Room modules; existing dependencies.

**Spec:** [UX-017 specification](../specs/2026-10-20-audit-n-ux-017-integration-error-recovery-design.md)

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

- **RF-1 → AC-1:** observeState fails, then emits after retryConfiguration. OBSERVE error clears and current status is shown. Covered by `observationRetryClearsOwnError` in the regression step below.
- **RF-2 → AC-2:** refreshAccess fails once then succeeds. ACCESS_REFRESH error disappears in Usage and Health cards. Covered by `accessRetryClearsRefreshError` in the regression step below.
- **RF-3 → AC-3:** Import fails while state observation succeeds. IMPORT failure remains actionable until its own successful retry. Covered by `importErrorSurvivesUnrelatedEmission` in the regression step below.
- **RF-4 → AC-4:** An older refresh succeeds after a newer refresh fails. The newest ACCESS_REFRESH failure remains visible. Covered by `lateSuccessCannotEraseNewFailure` in the regression step below.
- **RF-5 → AC-5:** Disable import while busy or cancel a coroutine. Preference boundaries remain; cancellation creates no false failure and access alone never enables import. Covered by `consentAndCancellationRemainCorrect` in the regression step below.

---

## Task 1: Deliver UX-017

**Files and responsibilities:**

- Modify: `app/src/main/java/com/luma/lifeos/ui/usage/UsageSettingsViewModel.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/usage/UsageSettingsRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/health/HealthSettingsViewModel.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/health/HealthSettingsCard.kt`
- Create: `app/src/main/java/com/luma/lifeos/ui/integrations/IntegrationSettingsError.kt`
- Create test: `app/src/test/java/com/luma/lifeos/ui/integrations/IntegrationSettingsRecoveryTest.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/usage/UsageSettingsFlowTest.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/health/HealthSettingsFlowTest.kt`
- Localized resources, only when copy changes: `app/src/main/res/values/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fr/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fa/strings.xml`

Keep changes in their current screen/state/repository responsibility. New files own only the interface or component named in the specification. If a listed existing file has changed since this plan, reconcile with the live code before editing; do not revert user changes to match the audit.

**Interfaces:**

- Consumes the current route/ViewModel/repository contracts in the files listed above; no new cross-task API is required.
- Produces: enum class IntegrationSettingsOperation { OBSERVE, ACCESS_REFRESH, IMPORT, PREFERENCE }; data class IntegrationSettingsError(val operation: IntegrationSettingsOperation, val requestId: Long). Both settings states replace actionError: Boolean with errors: Map<IntegrationSettingsOperation, IntegrationSettingsError>. Existing retryConfiguration(), refresh(), importNow(), setEnabled(Boolean) remain public.
- Other consumers: migrate every caller affected by these interface changes; preserve callback target identity and existing readiness/busy guards.

- [x] **Step 1: Read the spec and confirm prerequisites.** Inspect each listed file and the working-tree diff; preserve unrelated edits. No implementation prerequisite is required. Classify missing tests as additions, not evidence of an already passing regression.

- [ ] **Step 2: Add the behavior regressions and manual checks.** Use the listed test suites, keeping state/repository assertions in JVM tests where provided and navigation/Compose assertions in device tests. Use the named cases, fixtures and assertions below; extend existing fixtures instead of introducing a parallel harness. For speech, gesture comfort and visual reachability, automate the semantics/bounds that can be checked and record actual manual device evidence under the same case ID. A test cannot certify TalkBack speech merely by finding a live-region node. For copy-only changes, extend existing UI assertions.

| Case | Regression / evidence case | Fixture / action | Assertions |
|---|---|---|---|
| AC-1 | `observationRetryClearsOwnError` | observeState fails, then emits after retryConfiguration. | OBSERVE error clears and current status is shown. |
| AC-2 | `accessRetryClearsRefreshError` | refreshAccess fails once then succeeds. | ACCESS_REFRESH error disappears in Usage and Health cards. |
| AC-3 | `importErrorSurvivesUnrelatedEmission` | Import fails while state observation succeeds. | IMPORT failure remains actionable until its own successful retry. |
| AC-4 | `lateSuccessCannotEraseNewFailure` | An older refresh succeeds after a newer refresh fails. | The newest ACCESS_REFRESH failure remains visible. |
| AC-5 | `consentAndCancellationRemainCorrect` | Disable import while busy or cancel a coroutine. | Preference boundaries remain; cancellation creates no false failure and access alone never enables import. |

- [ ] **Step 3: Establish the regression.** Run the relevant targeted command below before implementation. New-interface tests may initially fail to compile; existing-behavior tests must fail on the specified mismatch. Record the actual failure. For a runtime-risk finding, first exercise its constrained fixture; if it already passes, record that result and retain the reachability regression without claiming a reproduced failure.

```bash
./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.ui.integrations.IntegrationSettingsRecoveryTest'
```

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.usage.UsageSettingsFlowTest,com.luma.lifeos.health.HealthSettingsFlowTest
```

Device commands require a connected, configured test device/emulator. Locale/font/window and TalkBack settings are deliberate validation inputs; the command alone does not configure or certify that matrix. Do not install dependencies or alter the execution environment as part of this documentation task.

- [x] **Step 4: Implement the owning contract.** Add typed operation error maps and request generations in both ViewModels; tag every observation/write outcome.

- [x] **Step 5: Connect the behavior.** Update cards/routes to show relevant operation copy and callbacks, including ViewModel refresh instead of only availability refresh.

- [ ] **Step 6: Exercise the complete flow.** Test interleaved failures/successes, observer retry, preference-disable races and cancellation.

- [ ] **Step 7: Verify the task.** Rerun the targeted commands above and require zero failures. Then run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` to catch all-call-site, resource and lint regressions. Exercise the relevant acceptance scenarios in EN/FR/FA, semantic light/dark, 100%/130%/200% font and narrow/landscape where layout or semantics changed. Record app/build revision, device/API, locale, font/window inputs and results; label missing checks pending. Expected outcome: AC-1 through AC-5 hold and affected compilation/lint checks pass.

- [ ] **Step 8: Review and integrate this task alone.** Inspect the diff against the spec and confirm every AC/RF has evidence. Stage only this task's hunks in existing modified files and its new files; do not stage the pre-existing dirty tree wholesale. If the execution workflow uses commits, commit with `fix: ux-017 integration error recovery`. Update the index status only from actual evidence.

## Handoff

Deliver this task's code changes and outstanding verification together. No Gradle or emulator checks were run, and no new tests were added, at the user's request. Do not mark runtime acceptance complete solely from source changes.
