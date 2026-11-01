# API36 app C4 after lock-gate and semantics fixes

- Device: `emulator-5554`, Medium Phone API 36.1 / Android 16.
- Gradle `:app:connectedAppLockQaDebugAndroidTest` ran 279 tests; the console says it finished 280. Exported JUnit contains 279 cases: 256 passed, 22 failed, 1 skipped. The one-case console/JUnit discrepancy remains unresolved.
- `AppNavigationTest.wholeAppGateKeepsUnlockDialogAndSettingsNavigationAvailable` passed. It verifies that a locked gate hides the covered Today screen from Compose semantics, Unlock opens the password prompt, cancellation returns to the gate, and the settings shortcut reaches the authenticated settings screen.
- App Lock credential integration cases, Q01 validation visibility, Quick Capture, journal readiness/multilingual journal, and SearchFlow focused coverage remain green. The broad `JournalCaptureTest.captureRestoresDraftAndOpensDetailThenEditsAndDeletes` failure from the prior C4 run did not reproduce in this full run.
- The 22 failures are in MemoryCapture (2), MoodCapture (1), PeopleCapture (1), PlaceCapture (1), SearchStates (1), calendar semantics (1), habits capture/navigation (5), language navigation (1), Todo accessibility/editor/language (5), primary navigation accessibility (1), and widgets (3). The skipped test remains `NativeUsageSessionImportTest.nativeForegroundEventsPersistAndRetryWithoutDuplicatingVisits`.
- Failure surfaces from the exported JUnit are recorded below. These are observed assertion/interaction failures; root causes have not yet been independently confirmed.

| Test | Observed failure surface |
| --- | --- |
| `MemoryCaptureTest.directCaptureRestoresDraftAppearsInReviewAndCanBeEditedAndDeleted` | `performScrollTo()` could not find `memory_importance_5`. |
| `MemoryCaptureTest.journalPromotionAndUnmarkingRetainOriginalBody` | Timed out waiting for a tagged UI node. |
| `MoodCaptureTest.capturesMultipleEmotionsRestoresDraftAndEditsAndDeletesFromTimeline` | Timed out waiting for the expected capture UI text/tag. |
| `PeopleCaptureTest.journalKeepsDraftAcrossNewPeopleAndHistoryReflectsExplicitRemoval` | Could not find `person_confirm_delete` for the confirmation tap. |
| `PlaceCaptureTest.journalDraftSurvivesPlaceCreationAndSelectionCanBeRenamedAndCleared` | Could not find `journal_confirm_delete` for the confirmation tap. |
| `SearchStatesTest.scopeHelpRemainsVisibleInNoMatchAndErrorStates` | Could not find editable text `Retry` in the error-state path. |
| `CalendarSemanticsTest.todayCellAnnouncesItsFullDateAndStateWithAReachableTarget` | Reachable-target/full-date assertion failed at line 31. |
| `HabitCaptureFlowTest.todayPreviewShowsScheduledRowsBeforeOlderUnscheduledHabitsAndCapsAtTwo` | Text assertion failed on the `habits_view_all` node, whose visible text is `View all (5)`. |
| `HabitCaptureFlowTest.historyPrecedesCollapsedManagementAndDeleteRemainsConfirmed` | Assertion failed at line 298. |
| `HabitCaptureFlowTest.editorValidationUsesLocalizedVisibleAndAccessibilityCopy` | `isDisplayed` found two nodes with the habit-length validation copy. |
| `HabitsNavigationTest.quickCaptureHabitShortcutUsesTrackingTodayAndPreservesHistoricalDateWithoutLogging` | `day_selected` was present only in the unmerged semantics tree. |
| `HabitsNavigationTest.moreHabitsDestinationCanCreateAndLogOneRepetition` | `habit_manage_edit` was not displayed. |
| `LanguageFlowTest.settingsSwitchesAllLanguagesAndPreservesSelectedDateThroughRecreation` | Could not find `nav_habits` for the navigation tap. |
| `TodoAccessibilitySemanticsTest.filterChangeKeepsRecoveryActionIdentity` | Could not find row semantics tag `todo_row_error_…` after the filter change. |
| `TodoEditorFlowTest.dueTimePickerConfirmsMinuteAndBackOutsideDismissalKeepCommittedValue` | IME action finder matched two nodes. |
| `TodoEditorFlowTest.invalidPickerMinuteIsRejectedBeforeTimeCommit` | IME action finder matched two nodes. |
| `TodoEditorFlowTest.dueTimePickerCancelKeepsDraftAndConfirmationUsesDefaultNine` | IME action finder matched two nodes. |
| `TodoLanguageFlowTest.dirtyDraftAndPoliteValidationSurviveEnglishPersianFrenchRecreation` | Could not find `todo_editor_select_date` for the tap. |
| `PrimaryNavigationAccessibilityTest.labelsFitAtNarrowWidthAndKeepFullLocalizedTabNames` | French calendar label clipped vertically at 320dp / font scale 2.0. |
| `WidgetCollectionFactoryTest.adapterIdentityIncludesProviderAndWidgetInstance` | Expected 2 adapter identities but observed 4. |
| `WidgetRendererAcceptanceTest.wd10KeepsHeaderTargetsSeparateAndShowsTotalCountBelowTitle` | Test cast `GradientDrawable` to `ColorDrawable`. |
| `WidgetRendererAcceptanceTest.habitProgressUsesInlineLayoutWhenItFitsAndStacksLongNames` | Expected 0 but observed 1. |

- The full app C4 gate still fails. Current-source C2/C3/C5 pass after the final gate semantics change; see [their summary](c2-c3-c5-gate-fix-summary.md).

Evidence: [Gradle log](c4-app-gate-fix.log), [JUnit XML](c4-app-gate-fix.xml), [gate regression GREEN](c4-green-app-lock-gate-dialog.xml), [gate failure RED](c4-red-app-lock-gate-dialog.log), [semantics leak RED](c4-red-app-lock-gate-semantics.log).
