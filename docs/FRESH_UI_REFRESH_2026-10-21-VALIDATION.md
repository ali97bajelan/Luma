# Fresh UI refresh implementation validation

**Date:** 21 October 2026

**Scope:** FRESH-001 through FRESH-006
**Status:** Partial. The integrated build passes, but connected tests have reproducible failures and the Persian large-text Timeline date is clipped. The acceptance checklist is not complete.

## Environment and setup

- Emulator: `Medium_Phone_API_36.1(AVD) - 16`, serial `emulator-5554`, Android API 36, debug build.
- Standard viewport: 1080 × 2400 px at 420 dpi (411 dp wide), font scale 1.0.
- Narrow large-text viewport: 840 × 2400 px at 420 dpi (320 dp wide), font scale 2.0.
- Wide viewport: 2688 × 3840 px at 320 dpi (840 dp wide).
- Languages exercised: English, French, and Persian RTL. Light and Dark themes were exercised on Today; the initial app theme was System default.
- A temporary two-habit, three-task, one-journal fixture was entered through the app UI. Its named records were deleted through their normal confirmation flows. The emulator screen size, density, font scale, app language, theme, and app-usage preference were restored. Connected-test teardown removed the debug app; `:app:installDebug` reinstalled it afterward.

## Build and instrumentation results

The integrated build, Android test APK build, and lint pass succeeded:

```text
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
BUILD SUCCESSFUL
```

The planned 19-class instrumentation selection could not run as written because these planned test classes are absent from the repository: `LumaUiComponentsTest`, `QuickCaptureSheetTest`, and `EntrySaveFeedbackTest`. The 16 existing classes ran 82 tests: 73 passed and 9 failed.

A focused rerun of the six classes containing failures ran 39 tests: 31 passed and 8 failed. The `TodayTasksFlowTest` empty-state timeout from the first run did not recur in that rerun. The separate existing `DayNavigationTest.previousAndNextArrowsPointInOppositeDirectionsInLtrAndRtl` test passed (1 test).

The failures observed were:

| Test | Result |
| --- | --- |
| `HabitCaptureFlowTest.todayPreviewShowsScheduledRowsBeforeOlderUnscheduledHabitsAndCapsAtTwo` | Fails because `assertTextContains("5")` is exact by default while the visible label is `View all (5)`. |
| `HabitCaptureFlowTest.historyPrecedesCollapsedManagementAndDeleteRemainsConfirmed` | Fails its history-before-management bounds assertion; current detail layout places management before history. |
| `TodayTasksFlowTest.contextualAddUsesTodayAndCompletionCanBeUndoneFromToday` | Initially times out waiting for `today_tasks_empty`; passes in the focused rerun. |
| `TimelineFlowTest.timelineSwitchShowsOnlyAppsOverOneMinuteAndPersistsTheChoice` | Expects `timeline_show_app_usage` while View options is collapsed. |
| `TimelineFlowTest.mixedTimelineOpensSignalsReadOnlyAndManualJournalDetailsAcrossDays` | Expects `timeline_order` while View options is collapsed. |
| `AppNavigationTest.dataWidgetCaptureUsesOneCurrentTimeChooserFromEitherProvider` | ActivityScenario teardown reports the Activity remained PAUSED instead of DESTROYED. |
| `MoodCaptureTest.capturesMultipleEmotionsRestoresDraftAndEditsAndDeletesFromTimeline` | Times out waiting for `screen_event_detail` after saving an edited mood. |
| `MemoryCaptureTest.directCaptureRestoresDraftAppearsInReviewAndCanBeEditedAndDeleted` | After tapping `memory_remove`, the expected `memory_confirm_remove` node is absent. |
| `MemoryCaptureTest.journalPromotionAndUnmarkingRetainOriginalBody` | Times out waiting for `journal_detail_body` after saving the promoted memory. |

The two Timeline tests need to expand View options before asserting its children, then explicitly test that collapsing the group preserves the filter. The other failures remain unresolved; no cause is claimed beyond the recorded assertion or lifecycle output.

## Emulator observations

| Task | Observed result |
| --- | --- |
| FRESH-001 | Shared surfaces and typography rendered on Today, Timeline, Review, and More in Light and Dark screenshots. At 840 dp, the Timeline reading pane measured 600 dp wide and centered with 120 dp side margins. Localized More and Quick Capture choices wrapped and remained reachable at 320 dp and 2× text. The planned component-level heading/action and TalkBack tests are absent. |
| FRESH-002 | With the controlled fixture, Today showed two scheduled habit previews, three current-day tasks, and one recent entry. The compact habit action controls were separate from the open action. Light and Dark Today screenshots were captured. |
| FRESH-003 | The first view showed direct search, date navigation, counts, and a collapsed View options group. Expanded options exposed usage, time zone, and ordering controls; the active usage context stayed visible when collapsed. Historical date navigation worked. In Persian RTL at 320 dp and 2× text, the selected date is squeezed to approximately one pixel and visibly ellipsized beside the Today control. This is a confirmed layout defect. |
| FRESH-004 | Empty Review showed distinct no-data labels and a collapsed Data setup section with one Integration settings action when expanded. With the fixture, the overview showed one entry and two scheduled habit-days; the review sections remained scrollable. |
| FRESH-005 | More opened Tasks and Back returned to More. Quick Capture from both Timeline and Review showed life-record and task groups, descriptive choices, and Close. On a historical Timeline day, the sheet showed both “Use current time” and “Add to Oct 20, 2026”; Task remained described as starting without a due date. Callback/date-policy and recreation checks remain unverified because `QuickCaptureSheetTest` is absent. |
| FRESH-006 | A confirmed journal save produced one “Entry saved” snackbar. Existing habit flow coverage passed the stored count and undo checks, but the 180 ms visual transition, pending/failure behavior, and snackbar-host priority were not independently verified; `EntrySaveFeedbackTest` is absent. |

The large-text date defect is in shared `DayNavigation`: the date button and Today button compete in one row. A layout adjustment and an LTR/RTL large-text regression test are proposed, but no source change has been made pending approval.

## Evidence screenshots

Screenshots are stored in [`docs/superpowers/validation/2026-10-21-fresh-ui-refresh/`](superpowers/validation/2026-10-21-fresh-ui-refresh/).

- [Today, Light, populated](superpowers/validation/2026-10-21-fresh-ui-refresh/today-light-populated.png) and [Today, Dark, populated](superpowers/validation/2026-10-21-fresh-ui-refresh/today-dark-populated.png)
- [Timeline, collapsed](superpowers/validation/2026-10-21-fresh-ui-refresh/timeline-collapsed.png), [expanded options](superpowers/validation/2026-10-21-fresh-ui-refresh/timeline-options.png), and [840 dp wide](superpowers/validation/2026-10-21-fresh-ui-refresh/timeline-840dp.png)
- [Timeline, French at 320 dp/2×](superpowers/validation/2026-10-21-fresh-ui-refresh/timeline-fr-320dp-2x.png) and [Timeline, Persian RTL at 320 dp/2× with clipped date](superpowers/validation/2026-10-21-fresh-ui-refresh/timeline-fa-320dp-2x-date-clipped.png)
- [Review, empty](superpowers/validation/2026-10-21-fresh-ui-refresh/review-empty.png) and [Review, populated](superpowers/validation/2026-10-21-fresh-ui-refresh/review-populated.png)
- [More, English](superpowers/validation/2026-10-21-fresh-ui-refresh/more-english.png), [French at 320 dp/2×](superpowers/validation/2026-10-21-fresh-ui-refresh/more-fr-320dp-2x.png), and [Persian RTL at 320 dp/2×](superpowers/validation/2026-10-21-fresh-ui-refresh/more-fa-320dp-2x.png)
- [Quick Capture on a historical day](superpowers/validation/2026-10-21-fresh-ui-refresh/quick-capture-historical.png), [from Review](superpowers/validation/2026-10-21-fresh-ui-refresh/quick-capture-from-review.png), and [French at 320 dp/2×](superpowers/validation/2026-10-21-fresh-ui-refresh/quick-capture-fr-320dp-2x.png)
- [Habits, empty](superpowers/validation/2026-10-21-fresh-ui-refresh/habits-empty.png)

## Remaining verification

- Resolve the Persian 320 dp/2× selected-date clipping and add its regression test if approved.
- Add or restore the three absent planned test classes, then verify Quick Capture callback/date policy, shared component semantics, and confirmed-save feedback policy.
- Triage the eight reproducible instrumentation failures listed above; update tests that assert pre-refresh Timeline behavior.
- Verify TalkBack output, contrast ratios, 48 dp targets on every changed control, the 180 ms confirmed progress animation, failed/pending save behavior, and the full light/dark/localized acceptance matrix.
- Run the integrated 19-class regression selection after resolving failures, capture final clean-state screenshots, and rerun `git diff --check`.
