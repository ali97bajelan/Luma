# UX-026 — Weekly activity can render a nonzero value as zero Implementation Plan

> **For agentic workers:** Implementation is complete. Use the Status field below for remaining verification work. No Gradle or emulator checks were run at the user's request.

**Goal:** Keep small positive and negative activity values distinct from zero in Review and Daily Summary.

**Architecture:** Extract Daily Summary's existing precision-preserving NumberFormat policy into a pure shared activity formatter. Apply it to activity totals/deltas only and retain rating-average formatting.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, lifecycle StateFlow and the existing Android/Room modules; existing dependencies.

**Spec:** [UX-026 specification](../specs/2026-10-20-audit-n-ux-026-activity-number-precision-design.md)

**Status:** Implementation complete; automated, Gradle, and emulator verification pending at the user's request. No behavior tests were added. Relative effort: Low.

## Global Constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Review Focus

These five concrete failure modes are pinned to the regression cases owned by this task. The assertions below are requirements for tests to add, not claims that those tests exist or currently pass.

- **RF-1 → AC-1:** Format 0.001 activity in Daily Summary and Review. Both retain a visibly nonzero localized quantity. Covered by `smallActivityIsNotZero` in the regression step below.
- **RF-2 → AC-2:** Format explicit 0 and an absent metric/delta input. Zero renders zero; absent metric keeps its absence copy and absent comparison returns null. Covered by `zeroAndMissingStayDistinct` in the regression step below.
- **RF-3 → AC-3:** Compare 0.001 versus 0 and the reverse. The localized deltas remain nonzero with the correct positive/negative direction. Covered by `smallDeltasKeepSign` in the regression step below.
- **RF-4 → AC-4:** Format rating average 3.456 alongside activity 0.001. Rating remains two fraction digits; activity keeps necessary precision. Covered by `ratingPrecisionIsUnchanged` in the regression step below.
- **RF-5 → AC-5:** Format 0.0000001 and 1234567.125 in EN/FR/FA. Digits/separators follow locale, values stay nonzero and no quantity conversion or storage write occurs. Covered by `localeAndRangeArePreserved` in the regression step below.

---

## Task 1: Deliver UX-026

**Files and responsibilities:**

- Modify: `app/src/main/java/com/luma/lifeos/ui/review/ReviewValues.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/review/ReviewSignals.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/review/ReviewOverview.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/summary/DailySummaryCard.kt`
- Create: `app/src/main/java/com/luma/lifeos/language/ActivityFormats.kt`
- Create test: `app/src/test/java/com/luma/lifeos/language/ActivityFormatsTest.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/review/WeeklyReviewFlowTest.kt`
- Localized resources, only when copy changes: `app/src/main/res/values/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fr/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fa/strings.xml`

Keep changes in their current screen/state/repository responsibility. New files own only the interface or component named in the specification. If a listed existing file has changed since this plan, reconcile with the live code before editing; do not revert user changes to match the audit.

**Interfaces:**

- Consumes the current route/ViewModel/repository contracts in the files listed above; no new cross-task API is required.
- Produces: fun formatActivityQuantity(value: BigDecimal, locale: Locale): String uses NumberFormat.getNumberInstance(locale) with maximumFractionDigits = 340. fun formatActivityChange(current: BigDecimal?, previous: BigDecimal?, locale: Locale): String? preserves nonzero deltas with exact decimal subtraction, prepends localized positive sign convention and returns null if either sample is missing. Rating reviewNumber/reviewChange remain separate.
- Other consumers: migrate every caller affected by these interface changes; preserve callback target identity and existing readiness/busy guards.

- [x] **Step 1: Read the spec and confirm prerequisites.** Inspect each listed file and the working-tree diff; preserve unrelated edits. No implementation prerequisite is required. Classify missing tests as additions, not evidence of an already passing regression.

- [ ] **Step 2: Add the behavior regressions and manual checks.** Use the listed test suites, keeping state/repository assertions in JVM tests where provided and navigation/Compose assertions in device tests. Use the named cases, fixtures and assertions below; extend existing fixtures instead of introducing a parallel harness. For speech, gesture comfort and visual reachability, automate the semantics/bounds that can be checked and record actual manual device evidence under the same case ID. A test cannot certify TalkBack speech merely by finding a live-region node. For copy-only changes, extend existing UI assertions.

| Case | Regression / evidence case | Fixture / action | Assertions |
|---|---|---|---|
| AC-1 | `smallActivityIsNotZero` | Format 0.001 activity in Daily Summary and Review. | Both retain a visibly nonzero localized quantity. |
| AC-2 | `zeroAndMissingStayDistinct` | Format explicit 0 and an absent metric/delta input. | Zero renders zero; absent metric keeps its absence copy and absent comparison returns null. |
| AC-3 | `smallDeltasKeepSign` | Compare 0.001 versus 0 and the reverse. | The localized deltas remain nonzero with the correct positive/negative direction. |
| AC-4 | `ratingPrecisionIsUnchanged` | Format rating average 3.456 alongside activity 0.001. | Rating remains two fraction digits; activity keeps necessary precision. |
| AC-5 | `localeAndRangeArePreserved` | Format 0.0000001 and 1234567.125 in EN/FR/FA. | Digits/separators follow locale, values stay nonzero and no quantity conversion or storage write occurs. |

- [ ] **Step 3: Establish the regression.** Run the relevant targeted command below before implementation. New-interface tests may initially fail to compile; existing-behavior tests must fail on the specified mismatch. Record the actual failure. For a runtime-risk finding, first exercise its constrained fixture; if it already passes, record that result and retain the reachability regression without claiming a reproduced failure.

```bash
./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.language.ActivityFormatsTest'
```

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.review.WeeklyReviewFlowTest
```

Device commands require a connected, configured test device/emulator. Locale/font/window and TalkBack settings are deliberate validation inputs; the command alone does not configure or certify that matrix. Do not install dependencies or alter the execution environment as part of this documentation task.

- [x] **Step 4: Implement the owning contract.** Extract the precision-preserving formatter from Daily Summary into language/ActivityFormats.kt.

- [x] **Step 5: Connect the behavior.** Route activity totals and deltas through it in ReviewSignals/ReviewOverview and reuse it in DailySummaryCard.

- [ ] **Step 6: Exercise the complete flow.** Test small, zero, large, negative-delta and missing values across EN/FR/FA without changing scale averages.

- [ ] **Step 7: Verify the task.** Rerun the targeted commands above and require zero failures. Then run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` to catch all-call-site, resource and lint regressions. Exercise the relevant acceptance scenarios in EN/FR/FA, semantic light/dark, 100%/130%/200% font and narrow/landscape where layout or semantics changed. Record app/build revision, device/API, locale, font/window inputs and results; label missing checks pending. Expected outcome: AC-1 through AC-5 hold and affected compilation/lint checks pass.

- [ ] **Step 8: Review and integrate this task alone.** Inspect the diff against the spec and confirm every AC/RF has evidence. Stage only this task's hunks in existing modified files and its new files; do not stage the pre-existing dirty tree wholesale. If the execution workflow uses commits, commit with `fix: ux-026 activity number precision`. Update the index status only from actual evidence.

## Handoff

Deliver this task's code changes, five scenario results and remaining device limitations together. No implementation has been performed by generating this plan. Do not mark the finding fixed solely because a plan, test name or historical screenshot exists.
