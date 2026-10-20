# UX-020 — Entry card accessibility actions promise Edit but open details Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use superpowers:subagent-driven-development only if the user selects delegation. Steps use checkbox syntax for tracking. This documentation request does not start implementation or delegate work.

**Goal:** Make an entry card's announced action match its supplied navigation destination.

**Architecture:** Require callers to provide the card action label instead of deriving it solely from record type. Preserve separate bookmark/marker actions.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, lifecycle StateFlow and the existing Android/Room modules; existing dependencies.

**Spec:** [UX-020 specification](../specs/2026-10-20-audit-n-ux-020-entry-card-action-labels-design.md)

**Status:** Draft plan. All implementation, test and commit steps remain unchecked. Relative effort: Low.

## Global Constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Review Focus

These five concrete failure modes are pinned to the regression cases owned by this task. The assertions below are requirements for tests to add, not claims that those tests exist or currently pass.

- **RF-1 → AC-1:** Render editable Mood/Memory in Timeline, Search and Review sources. Card click action says Open and opens details. Covered by `detailCardSaysOpen` in the regression step below.
- **RF-2 → AC-2:** Render a Today card wired to its editor. Action says Edit and opens that editor. Covered by `directEditorCardSaysEdit` in the regression step below.
- **RF-3 → AC-3:** Card also offers a bookmark/memory action. The marker control retains its own label and callback. Covered by `markerActionRemainsSeparate` in the regression step below.
- **RF-4 → AC-4:** Render usage/activity or another read-only event. Action describes its detail opening; it never promises content editing. Covered by `nonEditableEntryIsAccurate` in the regression step below.
- **RF-5 → AC-5:** Compile every EventCard caller and inspect EN/FR/FA semantics. No caller relies on inferred default action text and labels match their destinations. Covered by `allCallersSupplyLocalizedLabels` in the regression step below.

---

## Task 1: Deliver UX-020

**Files and responsibilities:**

- Modify: `app/src/main/java/com/luma/lifeos/ui/timeline/EventCard.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/timeline/TimelineRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/search/SearchRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/review/ReviewSourcesRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/memory/MemoryReviewRoute.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/EventCardTest.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/DetailNavigationTest.kt`
- Localized resources, only when copy changes: `app/src/main/res/values/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fr/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fa/strings.xml`

Keep changes in their current screen/state/repository responsibility. New files own only the interface or component named in the specification. If a listed existing file has changed since this plan, reconcile with the live code before editing; do not revert user changes to match the audit.

**Interfaces:**

- Consumes the current route/ViewModel/repository contracts in the files listed above; no new cross-task API is required.
- Produces: EventCard gains required @StringRes openActionLabel: Int adjacent to onOpen. Use that resource for clickable(onClickLabel = ...). Migrate every EventCard call found by rg, including any additional callers beyond the listed starting files.
- Other consumers: migrate every caller affected by these interface changes; preserve callback target identity and existing readiness/busy guards.

- [ ] **Step 1: Read the spec and confirm prerequisites.** Inspect each listed file and the working-tree diff; preserve unrelated edits. No implementation prerequisite is required. Classify missing tests as additions, not evidence of an already passing regression.

- [ ] **Step 2: Add the behavior regressions and manual checks.** Use the listed test suites, keeping state/repository assertions in JVM tests where provided and navigation/Compose assertions in device tests. Use the named cases, fixtures and assertions below; extend existing fixtures instead of introducing a parallel harness. For speech, gesture comfort and visual reachability, automate the semantics/bounds that can be checked and record actual manual device evidence under the same case ID. A test cannot certify TalkBack speech merely by finding a live-region node. For copy-only changes, extend existing UI assertions.

| Case | Regression / evidence case | Fixture / action | Assertions |
|---|---|---|---|
| AC-1 | `detailCardSaysOpen` | Render editable Mood/Memory in Timeline, Search and Review sources. | Card click action says Open and opens details. |
| AC-2 | `directEditorCardSaysEdit` | Render a Today card wired to its editor. | Action says Edit and opens that editor. |
| AC-3 | `markerActionRemainsSeparate` | Card also offers a bookmark/memory action. | The marker control retains its own label and callback. |
| AC-4 | `nonEditableEntryIsAccurate` | Render usage/activity or another read-only event. | Action describes its detail opening; it never promises content editing. |
| AC-5 | `allCallersSupplyLocalizedLabels` | Compile every EventCard caller and inspect EN/FR/FA semantics. | No caller relies on inferred default action text and labels match their destinations. |

- [ ] **Step 3: Establish the regression.** Run the relevant targeted command below before implementation. New-interface tests may initially fail to compile; existing-behavior tests must fail on the specified mismatch. Record the actual failure. For a runtime-risk finding, first exercise its constrained fixture; if it already passes, record that result and retain the reachability regression without claiming a reproduced failure.

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.EventCardTest,com.luma.lifeos.DetailNavigationTest
```

Device commands require a connected, configured test device/emulator. Locale/font/window and TalkBack settings are deliberate validation inputs; the command alone does not configure or certify that matrix. Do not install dependencies or alter the execution environment as part of this documentation task.

- [ ] **Step 4: Implement the owning contract.** Add the required resource parameter and replace internal type-based action-label inference.

- [ ] **Step 5: Connect the behavior.** Inventory and migrate every caller based on its real callback destination, preserving independent marker semantics.

- [ ] **Step 6: Exercise the complete flow.** Verify detail-opening and direct-editing contexts in merged semantics and actual navigation.

- [ ] **Step 7: Verify the task.** Rerun the targeted commands above and require zero failures. Then run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` to catch all-call-site, resource and lint regressions. Exercise the relevant acceptance scenarios in EN/FR/FA, semantic light/dark, 100%/130%/200% font and narrow/landscape where layout or semantics changed. Record app/build revision, device/API, locale, font/window inputs and results; label missing checks pending. Expected outcome: AC-1 through AC-5 hold and affected compilation/lint checks pass.

- [ ] **Step 8: Review and integrate this task alone.** Inspect the diff against the spec and confirm every AC/RF has evidence. Stage only this task's hunks in existing modified files and its new files; do not stage the pre-existing dirty tree wholesale. If the execution workflow uses commits, commit with `fix: ux-020 entry card action labels`. Update the index status only from actual evidence.

## Handoff

Deliver this task's code changes, five scenario results and remaining device limitations together. No implementation has been performed by generating this plan. Do not mark the finding fixed solely because a plan, test name or historical screenshot exists.

