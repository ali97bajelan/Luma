# UX Review 08: Weekly Review Overview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure Weekly Review so people can find its main saved observations before scanning detailed comparisons and evidence.

**Architecture:** Keep the existing WeeklyReviewRepository and date model. Add a compact overview and section navigation in Compose, derive summary values only from existing report data and bounded source pages, and move secondary methodology and daily evidence behind disclosures.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Coroutines Flow, existing review repository and Android string resources.

**Spec:** [docs/superpowers/specs/2026-10-13-ux-review-08-09-15-design.md](../specs/2026-10-13-ux-review-08-09-15-design.md), Weekly Review overview section.

## Global Constraints

- Keep week selection, current/previous date ranges, timezone handling, and saved-week restoration unchanged.
- Keep unavailable distinct from recorded zero, retain partial-week and sample coverage, and make no causal claims.
- Select the overview memory by event time and activity by sample count; never compare activity values across units or sources.
- Preserve exact metric source filters, bounded paging, retry states, memory actions, and read-only usage day navigation.
- Localize all new copy in English, Persian, and French; keep text scalable and RTL usable.
- Do not change report calculation, persistence schema, dependencies, or other review findings.

## Review Focus

- Empty or partial weeks: unavailable values stay unavailable while measured zero stays zero. Test in `WeeklyReviewFlowTest.overviewKeepsMissingAndRecordedZeroDistinct`.
- A long memory list: the overview selects the newest matching memory rather than the first source page. Test in `WeeklyReviewFlowTest.overviewShowsNewestMemoryFromPagedSources`.
- Activity groups with unlike units or equal sample counts: choose by samples and stable type/unit/source tie-breaks, never by raw magnitude. Test in `ReviewOverviewTest.mostSampledActivityUsesStableTiesAcrossUnits`.
- Current and previous source availability: each menu action opens only its own date range and metric. Test in `WeeklyReviewFlowTest.sourceMenuOpensExactCurrentAndPreviousRows`.
- Large text and Persian RTL: controls remain named, wrapped, and reachable. Inspect Review at font scale 1.3 and Persian RTL.

---

### Task 1: Build the compact overview and section navigation

**Files:**
- Create: `app/src/main/java/com/luma/lifeos/ui/review/ReviewOverview.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/review/WeeklyReviewRoute.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-fa/strings.xml`
- Modify: `app/src/main/res/values-fr/strings.xml`
- Test: `app/src/test/java/com/luma/lifeos/ui/review/ReviewOverviewTest.kt`
- Test: `app/src/androidTest/java/com/luma/lifeos/review/WeeklyReviewFlowTest.kt`

**Interfaces:**
- Consumes: `WeeklyReview`, `ReviewStatistics`, `ReviewActivity`, `observeReviewSources(repository, dates, zone, metric, offset, limit)`, and the existing `onEvent`/`onUsage` callbacks.
- Produces: `internal fun mostSampledActivity(activities: List<ReviewActivity>): ReviewActivity?`, ordered by descending `total.samples`, then ascending `total.type`, `total.unit`, and `total.source.name`; and a `ReviewOverview` composable showing mood, newest memory, usage with concise coverage, most-sampled activity, and linked people/places with a clear saved-link explanation.

- [ ] **Step 1: Add the failing activity-selection unit test.** Assert that the highest sample count wins even when another activity has a larger numeric total, and that equal counts use the specified type/unit/source order.
- [ ] **Step 2: Run the focused unit test.** Run `./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.ui.review.ReviewOverviewTest'`; expect the missing helper failure.
- [ ] **Step 3: Implement `mostSampledActivity` in `ReviewOverview.kt`.** Return null for an empty list and apply the comparator from the interface above.
- [ ] **Step 4: Add the failing Compose flow tests.** Assert the overview tags for mood, memory, usage with coverage, activity, and linked catalog data; verify the no-memory state; assert each section control scrolls to its keyed section; seed more than five memories and assert the overview shows the latest one.
- [ ] **Step 5: Run the focused instrumentation test.** Run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.review.WeeklyReviewFlowTest`; expect overview tags/section actions to be absent.
- [ ] **Step 6: Implement `ReviewOverview` and integrate it into `WeeklyReviewRoute`.** Use a stable `LazyListState` and keyed section items for Mood, Memories, People/Places, Usage, and Activity. For the overview memory request the last one-row source page using `maxOf(0, report.current.memories - 1)`; keep the existing detail preview page unchanged.
- [ ] **Step 7: Add localized overview and section labels.** Use existing localized date, number, and duration formatters; label the activity summary as most-sampled and the memory as the latest saved memory.
- [ ] **Step 8: Run the focused tests and compile.** Run `./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.ui.review.ReviewOverviewTest'` and, with an attached emulator, `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.review.WeeklyReviewFlowTest`; expected result is green tests. Run `./gradlew :app:assembleDebug` to catch resource and Compose compilation errors.
- [ ] **Step 9: Commit the task.** Commit only Review overview source, resources, and tests.

### Task 2: Compact source actions and disclose secondary evidence

**Files:**
- Modify: `app/src/main/java/com/luma/lifeos/ui/review/WeeklyReviewRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/review/ReviewValues.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/review/ReviewSignals.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-fa/strings.xml`
- Modify: `app/src/main/res/values-fr/strings.xml`
- Test: `app/src/androidTest/java/com/luma/lifeos/review/WeeklyReviewFlowTest.kt`

**Interfaces:**
- Consumes: Task 1 section keys and the existing current/previous source callbacks.
- Produces: one accessible Sources menu per comparison metric, with current and previous actions when both exist and a direct action when only one exists; named disclosures for long methodology, usage coverage, and per-day evidence.

- [ ] **Step 1: Add the failing source-menu test.** Verify that selecting current and previous period menu items opens the same metric with the exact expected `ReviewDates`; assert a missing-period action is absent.
- [ ] **Step 2: Run the focused instrumentation test.** Run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.review.WeeklyReviewFlowTest#sourceMenuOpensExactCurrentAndPreviousRows`; expect the menu interaction test to fail before implementation.
- [ ] **Step 3: Replace paired source buttons in `ReviewValue` with a compact menu.** Preserve existing test semantics with distinct current/previous menu item tags and retain direct click behavior when only one callback exists.
- [ ] **Step 4: Add the failing disclosure test.** Verify methodology, coverage, and per-day usage rows are hidden initially, then visible after opening the corresponding disclosure; ensure partial/missing status remains visible in the summary.
- [ ] **Step 5: Run the focused instrumentation test.** Run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.review.WeeklyReviewFlowTest#secondaryEvidenceStartsCollapsed`; expect failure until the details are grouped.
- [ ] **Step 6: Update `WeeklyReviewRoute` and `ReviewSignals` to place long methodology, usage coverage, and daily evidence behind named `DetailsDisclosure` sections.** Leave partial-week and unavailable/zero context visible before expansion.
- [ ] **Step 7: Localize the menu and disclosure labels in all three resource files.** Keep actions distinguishable in Persian RTL and use existing source labels.
- [ ] **Step 8: Run Review state/unit and instrumentation flows.** Run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.review.WeeklyReviewFlowTest` when a device is attached; expected result is green tests. Inspect English, French, Persian RTL, and font scale 1.3.
- [ ] **Step 9: Commit the task.** Commit only Review source, resources, and tests.
