# Luma Fresh UI Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the fresh review's calm daily companion design across the existing core screens, with compact daily actions and clear confirmed feedback.

**Architecture:** Add a small set of internal Compose presentation components, then migrate each reviewed surface through its own task. Keep existing repositories, route contracts, and state owners; hoist only the new Timeline disclosure state and consume existing editor results for feedback. Reuse the current semantic theme instead of adding a parallel palette or component library.

**Tech Stack:** Kotlin, Jetpack Compose/Material 3, Navigation Compose, existing Room-backed fixtures, Android string/plural resources, existing Gradle wrapper and SDK configuration.

**Spec:** [Fresh UI refresh design](../specs/2026-10-21-fresh-ui-refresh-design.md)
**Tasks:** [Task catalog](../../APP_FLOW_UX_REVIEW_2026-10-21-FRESH-TASKS.md)
**Source review:** [Fresh UI/UX review](../../APP_FLOW_UX_REVIEW_2026-10-21-FRESH.md)
**Status:** FRESH-001, FRESH-002, and FRESH-003 implementations are in place; their planned test and visual verification steps remain pending by user request. FRESH-004–006 have not started.

## Global Constraints

- Keep the five primary destinations in this order: Today, Timeline, Habits, Review, More; preserve existing route IDs and back behavior.
- Keep the current dependency versions and SDK configuration; add no dependencies, repository interfaces, database/schema changes, permissions, or network services.
- Preserve null-to-follow-today versus explicit selected dates, habit tracking-zone behavior, and current-day-only task previews.
- Today shows at most two scheduled habit rows and at most three task rows; retained recovery controls may add content.
- Support English, French, and Persian using string/plural resources, existing localized formatters, and start/end layout directions.
- Every interactive target is at least 48 × 48 dp; important labels, values, errors, and recovery controls wrap or scroll instead of being clipped.
- Preserve System, Light, and Dark theme choices; use semantic color roles with at least 4.5:1 normal-text contrast and 3:1 meaningful non-text contrast.
- Use 20 dp page gutters, a centered maximum body width of 600 dp including gutters, 16 dp section padding, and spacing from 4, 8, 12, 16, and 24 dp.
- Use the existing system font and Material roles: titleLarge for page titles/major values, titleMedium for sections/row titles, bodyLarge for body text, and bodyMedium for metadata; new styling uses only Normal and Medium weights.
- Keep inset ownership in the existing app/page scaffolds and preserve measured capture-FAB bottom clearance; do not add duplicate system-bar or IME padding.
- Preserve loading, empty, error, disabled, pending, retry, undo, and retained-action behavior; show success only for confirmed outcomes.

## Review Focus

These five cases cross component boundaries. The owning tasks include explicit assertions rather than relying on a visual happy path.

1. Long French/Persian text and 2.0 font scale must retain independent, reachable actions — FRESH-001/002/005.
2. Historical selection and a habit tracking zone differing from the device zone must not expose stale actions or today's task preview — FRESH-002.
3. Collapsing options or changing dates must not silently change the persisted usage filter — FRESH-003.
4. Recorded zero, missing data, partial coverage, and fractional activity must keep distinct values and source routes — FRESH-004.
5. Recreation, failed writes, and an already active undo/retry message must not duplicate or falsely announce success — FRESH-006.

## Execution and file ownership

Use one implementation owner for the shared components, strings, and app-level result handling. Recommended execution is native in one session because these six tasks share presentation components and resource files. This document does not start implementation or select an agent execution method.

All Kotlin paths below are rooted in the current app module. New files are explicitly labeled **Create**; other files already exist. All new copy goes into the three existing resource files:

- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-fr/strings.xml`
- `app/src/main/res/values-fa/strings.xml`

Implement FRESH-001 first; then FRESH-002, FRESH-003, FRESH-004, FRESH-005; finish with FRESH-006. Use the order to avoid reworking shared surfaces. Keep intentional per-task checkpoints separate from pre-existing untracked APKs, baseline profiles, hooks, and audit artifacts.

### Verification workflow

For behavior changes, add the specified focused regression, demonstrate its failure against the old interaction, then implement and rerun it. Do not add tests that merely repeat spacing constants or theme values. Existing fixtures should be extended rather than replaced with large new datasets.

During a task, run only its changed regression methods using the existing instrumentation runner:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.TimelineFlowTest#viewOptionsSurviveDateNavigationWithoutChangingUsagePreference
```

Substitute the exact class/method named in the task. Expected result: the selected tests pass. A changed test's initial failure must match the intended assertion, not an unrelated build or environment failure. FRESH-001 may first fail to compile because its explicitly proposed components do not exist yet. Use `git diff --check` at each checkpoint. Record checks and an appropriate commit message when the selected execution workflow includes commits; no commits are performed by this planning package.

<a id="fresh-001"></a>
## Task FRESH-001: Build the shared visual foundation

**Files**

- **Create:** `app/src/main/java/com/luma/lifeos/ui/common/LumaUiTokens.kt`
- **Create:** `app/src/main/java/com/luma/lifeos/ui/common/LumaUiComponents.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/common/EmptyStateCard.kt`
- **Create test:** `app/src/androidTest/java/com/luma/lifeos/ui/LumaUiComponentsTest.kt`

**Interfaces**

- Consumes: existing `MaterialTheme.colorScheme`, `MaterialTheme.typography`, and the current `EmptyStateCard` arguments.
- Produces: `LumaUi`, `LumaSurfaceTone`, `LumaReadingPane`, `LumaSectionSurface`, `LumaSectionHeader`, and `LumaChoiceRow` with the complete signatures and values in [FRESH-001](../specs/2026-10-21-fresh-ui-refresh-design.md#fresh-001). Later tasks must use those signatures without local duplicate token sets.

- [ ] **Step 1:** Add `LumaUiComponentsTest.sectionHeaderKeepsActionIndependentFromHeading` using separate callback and heading nodes. Assert the title has `SemanticsProperties.Heading`, the action has a click action, clicking it increments its callback counter exactly once, and the title has no click action.
- [ ] **Step 2:** Add `choiceRowWrapsLocalizedCopyAndHasOneAction` using a long French/Persian label, 320 dp content width, and font scale 2.0. Assert one clickable node for the row, full title/supporting copy exists, the action is displayed and at least 48 dp in both dimensions, and a click invokes its callback once. Run these new methods to capture the missing-component failure.
- [ ] **Step 3:** Implement the token object and the four composables from the specification, including width ownership, semantic color pairs, header stacking below 280 dp or at font scale 1.5, and decorative icons. Preserve `AppSectionHeading`'s default and the existing theme palettes.
- [ ] **Step 4:** Align `EmptyStateCard` to the neutral surface and 8 dp content gap while retaining its existing signature, tag, and optional CTA. Run the two new component tests; inspect neutral/reflective pairs in both themes for the specified contrast thresholds.
- [ ] **Step 5:** Record acceptance results and checkpoint the foundation. Suggested commit: `feat(ui): add shared calm interface components`.

<a id="fresh-002"></a>
## Task FRESH-002: Compact Today and habit presentation

**Files**

- Modify: `app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/today/TodayCaptureActions.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/TodayHabitsCard.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitsScreen.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitProgress.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/todo/TodayTasksCard.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/todo/TodoRow.kt`
- Tests: `app/src/androidTest/java/com/luma/lifeos/TodayScreenTest.kt`
- Tests: `app/src/androidTest/java/com/luma/lifeos/habits/HabitCaptureFlowTest.kt`
- Reuse: `app/src/androidTest/java/com/luma/lifeos/habits/HabitDateSelectionFlowTest.kt`
- Reuse: `app/src/androidTest/java/com/luma/lifeos/todo/TodayTasksSemanticsTest.kt` and `TodayTasksFlowTest.kt` in the same directory.

**Interfaces**

- Consumes: FRESH-001 components and existing Today/habit/task states and callbacks. `TODAY_HABIT_PREVIEW_LIMIT` remains 2; `TODO_DEFAULT_TODAY_LIMIT` remains 3.
- Produces: source-compatible `TodayScreen`, `TodayHabitsCard`, `HabitsScreen`, `HabitRow`, `HabitProgressContent`, and `TodayTasksCard`; `TodoRow` gains only the trailing optional `compact: Boolean = false` parameter.

- [ ] **Step 1:** Extend `TodayScreenTest` with `populatedDailyPreviewKeepsCaptureAndTasksHeadingInFirstView`: use an explicitly sized 411 × 891 dp/1.0 fixture with two short-name habits, three due/overdue tasks, and one entry. Assert all three capture nodes, both habit-log nodes, and `today_tasks_heading` are displayed before scrolling. Capture the old layout's failure; do not make this expectation apply to arbitrary user text.
- [ ] **Step 2:** Extend `HabitCaptureFlowTest.compactPreviewKeepsSeparateFortyEightDpActionsAtLargeText` to use long localized names/units at 320 dp/2.0. Assert `habit_open_<id>` and `habit_log_<id>` are separate actions, each at least 48 dp, and activating one never calls the other. Add `compactNoRecordsKeepsAccessibleStatusAndPendingRecovery`: assert the ordinary status is absent from visible Text but included in the open target's content description; then supply pending/disabled/skipped fixtures and assert their status/retry nodes exist.
- [x] **Step 3:** Apply shared sections/headers to Today previews and reading panes to Today/Habits. Flatten compact habit wrappers, use 4 dp between compact rows, 8 dp vertical row padding, and the 240 dp/1.5 compact layout rule. Preserve the standalone 336 dp rule, two-row cap, pending schedules, retained actions, tracking-zone differences, and all callback/tag wiring. Change `HabitProgressContent(day: HabitDay)` to wrap count/unit text without changing numeric formatting or progress semantics.
- [ ] **Step 4:** Append `compact` to `TodoRow` and use its transparent variant only from `TodayTasksCard`; preserve the checkbox/open/error focus behavior. Keep three-row task limits and one ordinary empty message. Run the new regressions, existing `todayPreviewShowsScheduledRowsBeforeOlderUnscheduledHabitsAndCapsAtTwo`, both stale-date rejection methods in `HabitDateSelectionFlowTest`, and the existing Today task visibility/undo and conflict/missing-row cases.
- [ ] **Step 5:** Inspect normal and large-text previews, including historical dates, and record acceptance results. Suggested commit: `feat(ui): compact daily habit and task previews`.

<a id="fresh-003"></a>
## Task FRESH-003: Simplify Timeline's first view

**Files**

- Modify: `app/src/main/java/com/luma/lifeos/ui/timeline/TimelineRoute.kt`
- **Create:** `app/src/main/java/com/luma/lifeos/ui/timeline/TimelineViewOptions.kt`
- Modify: the three string resource files listed above.
- Tests: `app/src/androidTest/java/com/luma/lifeos/TimelineFlowTest.kt`
- Reuse: `app/src/androidTest/java/com/luma/lifeos/TimelinePagingRouteTest.kt`

**Interfaces**

- Consumes: `TimelineViewModel.setShowAppUsage(Boolean)`, `TimelineUsagePreference.setShowAppUsage(Boolean)`, the observed pager state, and existing `DayNavigation(date, onSelectDate, onToday)`.
- Produces: `TimelineViewOptions` with the complete signature in [FRESH-003](../specs/2026-10-21-fresh-ui-refresh-design.md#fresh-003). `TimelineRoute`'s callable contract remains unchanged. New UI state is only the hoisted saveable disclosure Boolean.

- [ ] **Step 1:** Update `timelineSwitchShowsOnlyAppsOverOneMinuteAndPersistsTheChoice` to open `timeline_view_options` before accessing the existing switch. Add `viewOptionsSurviveDateNavigationWithoutChangingUsagePreference`: assert `timeline_view_options_panel` starts absent, expand it, turn usage on, change day, and assert it stays expanded with the switch on. Close it and assert the active indicator exists while the switch is absent. Verify the existing SharedPreferences value remains true. Run the focused new test against the old UI.
- [x] **Step 2:** Implement `TimelineViewOptions` and add the three specified `timeline_*` strings in all locales. Use a named 48 dp toggle with expanded/collapsed semantics; pass the current filter flag and the existing persistence/pager callback, with no local copy of that flag.
- [x] **Step 3:** Move disclosure state above `key(date, zone)` in `TimelineRoute`; render title/Search, `DayNavigation`, loaded counts/options, active-filter indicator, then the feed. Apply the reading pane and shared roles. Keep the current stable feed keys, paging effects, grouping, error/empty content, and capture clearance.
- [ ] **Step 4:** Extend the new disclosure test with `StateRestorationTester` around the existing LifeOsApp fixture; assert the panel/filter/date restore together. Run the changed filtering test and existing mixed-entry/date-only/paging checks. Confirm opening Search remains one click and closing options does not change the saved preference.
- [ ] **Step 5:** Inspect an empty and populated day in English/French/Persian and record acceptance results. Suggested commit: `feat(ui): group timeline view options`.

<a id="fresh-004"></a>
## Task FRESH-004: Restructure Weekly Review's overview

**Files**

- Modify: `app/src/main/java/com/luma/lifeos/ui/review/ReviewOverview.kt`
- **Create:** `app/src/main/java/com/luma/lifeos/ui/review/ReviewOverviewSignals.kt`
- **Create:** `app/src/main/java/com/luma/lifeos/ui/review/ReviewOverviewMemory.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/review/ReviewHeader.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/review/ReviewSection.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/review/WeeklyReviewRoute.kt` for reading width/gutters only.
- Modify: the three string resource files listed above.
- Tests: `app/src/androidTest/java/com/luma/lifeos/review/WeeklyReviewFlowTest.kt`
- Reuse: `app/src/androidTest/java/com/luma/lifeos/habits/HabitReviewFlowTest.kt`

**Interfaces**

- Consumes: existing `WeeklyReview`, `ReviewHabitsState?`, `IntegrationAvailabilityState`, callbacks, `mostSampledActivity`, and `reviewUsageCoverageSummary`.
- Produces: `ReviewOverviewSignals(report, availability, onSources, onIntegrations)` and `ReviewOverviewMemory(report, repository, onEvent)` with the exact typed signatures in [FRESH-004](../specs/2026-10-21-fresh-ui-refresh-design.md#fresh-004). `ReviewOverview` and existing section/metric keys remain source-compatible.

- [ ] **Step 1:** Add `overviewModulesKeepPersonalContentBeforeSavedSignals`. Assert `review_overview_personal`, `review_overview_memory`, and `review_overview_signals` occur in that vertical order, with personal counts and mood absence/value still present. Add `overviewSetupStartsCollapsedAndKeepsWeekWhenOpened`: assert `review_overview_integrations` is absent until `review_overview_data_setup` is expanded, then occurs exactly once and invokes its callback once without changing the selected week. Run these tests against the old overview.
- [ ] **Step 2:** Extract latest memory and saved signals into the two specified files. Preserve the memory observation's paging offset, stored-content route, error/retry behavior, and time formatter; keep `mostSampledActivity` and `reviewUsageCoverageSummary` callable from their current package. Do not add repository requests for the split signal cards.
- [ ] **Step 3:** Recompose `ReviewOverview` into the specified three modules. Apply Reflective tone only to an available saved memory; use ordinary body-size absence messages in signals and group setup explanations behind the named disclosure. Consolidate the overview integration callbacks under `review_overview_integrations`, add the localized setup key, and update assertions using the two retired tags.
- [ ] **Step 4:** Align `ReviewHeader` title roles and `ReviewSection` surfaces; apply reading width in `WeeklyReviewRoute` without inserting/removing top-level lazy keys. Run the new tests plus `overviewKeepsMissingAndRecordedZeroDistinct`, `overviewShowsSavedSamplesActivitySourceAndLinkedCatalogCounts`, `overviewShowsNewestMemoryFromPagedSources`, `sectionControlsScrollToTheirHeadings`, `sectionSelectionRestoresWithoutChangingTheSelectedWeek`, and `finalReviewActionScrollsAboveCaptureFab`. Add a fractional-activity assertion to the existing numeric fixture if it does not already cover one.
- [ ] **Step 5:** Inspect empty/populated/partial weeks, a failed memory load, and Persian large text; record acceptance results. Suggested commit: `feat(ui): split weekly overview into readable modules`.

<a id="fresh-005"></a>
## Task FRESH-005: Align More and Quick Capture

**Files**

- Modify: `app/src/main/java/com/luma/lifeos/ui/more/MoreRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/QuickCaptureSheet.kt`
- Tests: `app/src/androidTest/java/com/luma/lifeos/AppNavigationTest.kt`
- **Create test:** `app/src/androidTest/java/com/luma/lifeos/QuickCaptureSheetTest.kt`

**Interfaces**

- Consumes: FRESH-001 `LumaChoiceRow`, existing icons, and current More/QuickCaptureSheet callbacks.
- Produces: unchanged `MoreRoute` and `QuickCaptureSheet(browsedDate: LocalDate? = null, onDismiss: () -> Unit, onMood: (LocalDate?) -> Unit, onJournal: (LocalDate?) -> Unit, onMemory: (LocalDate?) -> Unit, onTask: (() -> Unit)? = null)` contracts. No new route, chooser, or domain state is introduced.

- [ ] **Step 1:** Add `QuickCaptureSheetTest.dateChoiceAppliesOnlyToLifeRecords`: use callback collectors and a historical `browsedDate`, click each record choice with Now/selected date, and assert exactly one matching callback with null/the selected date respectively. Task increments only its undated callback; dismiss increments only dismissal. Add `largeRtlChoicesAndCloseRemainReachable` with long localized labels and verify scrolling can reach and click the final choice/Close exactly once.
- [ ] **Step 2:** Replace `MoreEntry`'s outlined cards with neutral shared choice rows. Preserve task availability, title/description/tag resources, ordering, and callbacks. Keep AppPageScaffold and apply the shared reading width without duplicate padding.
- [ ] **Step 3:** Replace Quick Capture buttons with the specified four icon choice rows and spacing. Keep date chips, grouping text, expanded sheet state, scroll/back/close behavior, optional Task, and every existing `capture_*` tag. Add explicit 48 dp minimum targets to date-choice chips and Close.
- [ ] **Step 4:** Run the new sheet tests and `AppNavigationTest.moreOpensPeoplePlacesAndSettingsWithBackToMore`, `pastDayCaptureStartsExpandedAndKeepsEachDateChoice`, and `dataWidgetCaptureUsesOneCurrentTimeChooserFromEitherProvider`. Extend the direct sheet fixture with state restoration: select the browsed date, restore composition, and assert the next record callback still receives that date.
- [ ] **Step 5:** Inspect the sheet from Timeline and Review, including a historical day and optional Task omitted; record acceptance results. Suggested commit: `feat(ui): unify destination and capture choice rows`.

<a id="fresh-006"></a>
## Task FRESH-006: Acknowledge confirmed saves and animate confirmed progress

**Files**

- Modify: `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitProgress.kt`
- **Create:** `app/src/main/java/com/luma/lifeos/ui/common/EntrySaveAcknowledgement.kt`
- Modify: the three string resource files listed above.
- **Create test:** `app/src/androidTest/java/com/luma/lifeos/EntrySaveFeedbackTest.kt`
- Extend/reuse: `app/src/androidTest/java/com/luma/lifeos/JournalCaptureTest.kt`, `MoodCaptureTest.kt`, and `MemoryCaptureTest.kt` in the same directory.
- Reuse: `app/src/androidTest/java/com/luma/lifeos/FeedbackAccessibilityTest.kt` and `app/src/androidTest/java/com/luma/lifeos/habits/HabitCaptureFlowTest.kt`.

**Interfaces**

- Consumes: existing `EntryEditorResult.Saved/Deleted/Unmarked`, `consumeEntryEditorResult()`, app-level coroutine scope/SnackbarHostState, and confirmed `HabitDay` counts.
- Produces: one short acknowledgement at the existing result-consumption boundary, with no new domain result types or persistence; `internal suspend fun acknowledgeSavedEntry(result: EntryEditorResult, snackbarHostState: SnackbarHostState, message: String)` implements the host-priority policy. `HabitProgressContent(day: HabitDay)` remains source-compatible.

- [ ] **Step 1:** In `EntrySaveFeedbackTest`, reuse the in-memory Room/`LifeOsApp` fixture pattern from `TimelineFlowTest`. Add `confirmedSaveShowsOneAcknowledgement`: confirm a journal write and assert stored content and exactly one localized success node. Add a separate test, `failedSaveKeepsDraftWithoutAcknowledgement`, whose fixture wraps `RoomJournalRepository(db, clock)` through `JournalRepository` delegation and overrides `suspend fun create(id: EventId, input: JournalInput): EventId` to throw; assert the editor/error/draft remains and no success appears. Run to capture the missing acknowledgement failure in the successful-write test.
- [ ] **Step 2:** Implement `acknowledgeSavedEntry` with the result/host policy from the spec. Resolve `entry_saved_feedback` in composition; after normal consumed-result navigation, launch this helper through the existing app-level scope. Do not await the optional record lookup or alter its selected-date behavior. Add the specified copy in all three locales.
- [ ] **Step 3:** Extend current capture-flow fixtures to assert acknowledgement after confirmed mood/memory saves and its absence after delete/unmark/cancel. Add `JournalCaptureTest.savedAcknowledgementDoesNotReplayAfterRecreation`: consume Saved, use `compose.waitUntil(timeoutMillis = 10_000)` to observe its short message disappear, recreate through `activityRule.scenario.recreate()`, and assert no replay. In `EntrySaveFeedbackTest`, add `existingActionFeedbackIsNotReplacedBySaveAcknowledgement`: display an indefinite Undo message through a real SnackbarHostState, call `acknowledgeSavedEntry` with Saved, and assert its currentSnackbarData identity and action label are unchanged; dismiss it and assert no generic message was queued. Also call the helper with Deleted/Unmarked on an idle host and assert it stays idle.
- [ ] **Step 4:** In `HabitProgressContent`, remember the visual animation per habit ID/date and animate only later confirmed fraction changes for 180 ms. First observation/date change starts at the actual target. Expose the confirmed fraction in progress semantics and clear duplicate animated-child progress semantics; numeric labels use the actual count. Verify the existing repeated-log/undo regression still changes each stored count once and pending/failure leaves it unchanged.
- [ ] **Step 5:** Run the new save regressions and affected capture/feedback checks. In the emulator compare enabled versus disabled system animation duration, confirmed increment/undo, pending/failure, and TalkBack acknowledgement. Restore animation settings and record acceptance results. Suggested commit: `feat(ui): acknowledge saved entries and animate confirmed progress`.

## Integrated completion checks

- [ ] Run the integrated build/resource/lint pass once after the tasks; repeat only for new edits or failures:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Expected: `BUILD SUCCESSFUL`, no new lint errors, and both application/test APKs produced.

- [ ] Run one combined instrumentation pass over the changed/new test classes plus the relevant navigation/inset regressions:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.ui.LumaUiComponentsTest,com.luma.lifeos.TodayScreenTest,com.luma.lifeos.habits.HabitCaptureFlowTest,com.luma.lifeos.habits.HabitDateSelectionFlowTest,com.luma.lifeos.todo.TodayTasksSemanticsTest,com.luma.lifeos.todo.TodayTasksFlowTest,com.luma.lifeos.TimelineFlowTest,com.luma.lifeos.TimelinePagingRouteTest,com.luma.lifeos.review.WeeklyReviewFlowTest,com.luma.lifeos.habits.HabitReviewFlowTest,com.luma.lifeos.AppNavigationTest,com.luma.lifeos.QuickCaptureSheetTest,com.luma.lifeos.EntrySaveFeedbackTest,com.luma.lifeos.JournalCaptureTest,com.luma.lifeos.MoodCaptureTest,com.luma.lifeos.MemoryCaptureTest,com.luma.lifeos.FeedbackAccessibilityTest,com.luma.lifeos.ui.PageInsetsContractTest,com.luma.lifeos.ui.PrimaryNavigationAccessibilityTest
```

Expected: all selected regressions pass. Do not expand to unrelated integration/provider/widget suites unless a changed shared component creates a concrete unresolved concern.

- [ ] Use the dedicated two-habit/three-task/one-entry fixture from the spec for one ordinary visit per affected screen, not a separate new dataset per task. Capture Today, Timeline, Habits, Weekly Review, More, and Quick Capture at 411 × 891 dp/1.0 English in Light and Dark. Exercise empty/error/recovery states where changed, using controlled fixtures without clearing personal data.
- [ ] Repeat the affected controls at 320 dp/2.0 in French and Persian RTL, inspect a centered 840 dp-wide window, and check TalkBack names/state/action separation, contrast, and final-row FAB clearance. Restore the original emulator settings.
- [ ] Create `docs/FRESH_UI_REFRESH_2026-10-21-VALIDATION.md` **during implementation** with actual commands/results, device/build identity, screenshots, acceptance outcomes, and pending matrix cases. Do not create it now with invented pass results.
- [ ] Run `git diff --check`, inspect the final diff for unintentional behavioral changes, and update the task catalog with real completion/verification status. Every declared completion must be backed by the recorded checks.

## Planning self-review

- All six findings have owners; the catalog links each task to its spec and plan section.
- Shared interfaces are declared once in the spec and consumed by the matching task; changed existing signatures are explicit and default-compatible.
- Every task has five acceptance scenarios in the spec, concrete implementation files, state/error constraints, and appropriate verification steps.
- No new dependencies, business rules, navigation destinations, or unrelated editor/widget redesign is included.
- This plan schedules implementation verification; generating these documents has not run prospective app builds, tests, or emulator checks.
