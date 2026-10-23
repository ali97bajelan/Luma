# T30–T33 validation recheck — 2026-10-22

## Decision and task status

Release acceptance is **NOT ACCEPTED**. T30, T31, T32-C and T32-D remain **blocked** by full-suite connected failures. T32 coordination and T33 are **blocked** on those child failures and the required rendered/accessibility matrix. T32-A and T32-B are **implemented (validation pending)**: their 36-test focused regression run passes, while visual, locale/window and accessibility evidence remains NOT RUN. Optional O01–O08 remain deferred.

The EventCard viewport issue and provider-absent Review routes are fixed and covered by passing focused instrumentation. Two Timeline disclosure assertions and the date-only log's hard-coded 24-hour expectation were stale; the tests now follow the expanded disclosure and the device's time-format setting. The full connected run still has 15 failures outside the passing focused T32-A/B set.

## Environment and commands

- Date: 2026-10-22, Asia/Tehran.
- Tested baseline revision: `3825167781a29cd25f7e37c761d32a787edddec8`; the tested source also included uncommitted follow-up changes in the working tree.
- Runtime: OpenJDK 17.0.20.1; Gradle 9.1.0; offline dependencies.
- Device: clean disposable `LumaFix_API36` clone of the `Medium_Phone_API_36.1` configuration, API 36, 1080 × 2400 px at 420 dpi (about 411 × 914 dp), font scale 1.0. Locale property was unset and was not recorded. The configured AVD user data was not used or cleared.
- Debug APK SHA-256: `5b67fb18e710a3f664fb717577bf2ea12f4f9a3a15af0351c380da0ae3d01ae6`.

Host validation command:

```bash
bash ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --console=plain
```

Result: **BUILD SUCCESSFUL**. Unit tests: 271, 0 failures/errors/skips. Debug assembly passed. Lint passed with 144 warnings.

The current source color/font audit was also run:

```bash
python3 docs/luma-redesign/audit-t30.py
```

Result: **PASS**, 158 opaque light/dark role pairs and bundled-font checks, 0 failures. This is source evidence, not rendered contrast or Android font-shaping evidence.

Focused affected instrumentation command:

```bash
bash ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.EventCardTest,com.luma.lifeos.TimelineFlowTest,com.luma.lifeos.TimelinePagingRouteTest,com.luma.lifeos.habits.HabitTimelineFlowTest,com.luma.lifeos.DetailNavigationTest,com.luma.lifeos.review.ReviewProviderFallbackTest,com.luma.lifeos.review.WeeklyReviewFlowTest,com.luma.lifeos.usage.ReviewUsageFlowTest,com.luma.lifeos.habits.HabitReviewFlowTest \
  --offline --console=plain
```

Result: **BUILD SUCCESSFUL**, 36 tests, 0 failures, 0 skipped. Both enlarged-text EventCard viewport assertions and RTL secondary-action targets pass. Timeline disclosure and date-only log checks pass. Both missing-provider Review destination tests show the unavailable message and return action.

Full connected command:

```bash
bash ./gradlew :app:connectedDebugAndroidTest --offline --console=plain
```

Result: **BUILD FAILED** from instrumentation failures. The XML test summary reports 224 cases, 15 failures, 0 errors and 1 skipped (208 passed).

## Full connected-run failures

The current full run reports the following failures. Causes outside the stale Timeline assertions have not been isolated, so they remain blockers rather than assumed production defects.

| Owner | Failed test(s) | Result |
| --- | --- | --- |
| T30 | `LanguageFlowTest.journalDraftTextTagsAndDateSurviveLanguageChangeThenSaveAndDetail` | Could not scroll to the journal tag input after language change. |
| T31 | `HabitCaptureFlowTest.todayPreviewShowsScheduledRowsBeforeOlderUnscheduledHabitsAndCapsAtTwo` | Expected scheduled-habit count text was absent. |
| T32-C | `SearchStatesTest.scopeHelpRemainsVisibleInNoMatchAndErrorStates`; `HabitCaptureFlowTest.historyPrecedesCollapsedManagementAndDeleteRemainsConfirmed`; `HabitsNavigationTest.primaryHabitsDestinationCanCreateAndLogOneRepetition`; `TodoAccessibilitySemanticsTest.filterChangeKeepsRecoveryActionIdentity` | Search recovery, habit management/navigation and task recovery assertions failed; focused isolation is pending. |
| T32-D | `AppNavigationTest.dataWidgetCaptureUsesOneCurrentTimeChooserFromEitherProvider`; `AppNavigationTest.pastDayCaptureStartsExpandedAndKeepsEachDateChoice`; `JournalCaptureTest.captureRestoresDraftAndOpensDetailThenEditsAndDeletes`; `MemoryCaptureTest.directCaptureRestoresDraftAppearsInReviewAndCanBeEditedAndDeleted`; `MemoryCaptureTest.journalPromotionAndUnmarkingRetainOriginalBody`; `MoodCaptureTest.capturesMultipleEmotionsRestoresDraftAndEditsAndDeletesFromTimeline`; `PeopleCaptureTest.journalKeepsDraftAcrossNewPeopleAndHistoryReflectsExplicitRemoval`; `PlaceCaptureTest.existingPlaceIsReusableAndDeletingItKeepsLinkedMemory`; `PlaceCaptureTest.journalDraftSurvivesPlaceCreationAndSelectionCanBeRenamedAndCleared` | Nine Quick Capture/editor lifecycle and interaction checks failed; focused isolation remains pending. |

No T32-A or T32-B case failed in the full run. The focused run provides more specific passing evidence for those two families.

## Acceptance matrix

| Check | Result | Evidence / remaining work |
| --- | --- | --- |
| T30 role contrast and bundled-font source audit | PASS | Source audit: 158 pairs, 0 failures; rendered colors, alpha/disabled states and Android font shaping remain open. |
| Unit tests | PASS | 271 tests, 0 failures/errors/skips. |
| Debug assembly | PASS | Current working-tree source assembled the debug APK listed above. |
| Lint | PASS | 144 warnings; not triaged in this pass. |
| Focused Timeline/Review/navigation instrumentation | PASS | 36 tests, 0 failures/skips. |
| Full connected instrumentation | FAIL | 15 failures and 1 skipped case; see grouped failure table. |
| Provider-absent `review_sources` / `review_usage` | PASS (focused) | Localized unavailable messages, route tags and Back actions are exercised in `ReviewProviderFallbackTest`. |
| Six primary-screen captures and route-family rendered evidence | NOT RUN | No controlled fixtures or screenshots were retained. |
| English/French/Persian, System/Light/Dark, 320 dp at 2.0, 411 × 891 dp at 1.0 and 840 dp matrix | NOT RUN | Includes RTL wrapping, keyboard/IME, final-row/FAB clearance, 48 dp reachability, TalkBack/focus, rendered contrast, system bars and animation scale. |
| Optional O01–O08 | Deferred / not selected | No optional behavior was implemented. |

No screenshots or release acceptance are claimed. Reconsider T33 after remaining connected failures are isolated and resolved or shown to be stale, and the required visual/accessibility evidence is recorded.

Suggested commit message: `fix(ui): fit timeline cards and guard Review routes`

## Editor acceptance extension — 2026-10-23

T35–T38 now have source implementations recorded; their editor acceptance remains pending. T37 refines the title/note layout, responsive due-date card and Save footer while preserving the existing task data and recovery flows. T38 refines the memory editor layout while preserving the existing record and recovery flows. Per user instruction, no Gradle test or emulator check was run for T38. R38-1 through R38-5 are **NOT RUN** in the [T38 implementation record](../2026-10-23/t38/acceptance.md). These updates do not resolve the existing T32-D blockers or change the T33 release decision: **NOT ACCEPTED**.
