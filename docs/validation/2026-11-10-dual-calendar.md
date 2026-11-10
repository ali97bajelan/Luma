# Dual-calendar implementation and QA handoff

Package date: 2026-11-10  
Status: DC-I01–I06 source complete; DC-Q01 not started; acceptance pending.

## Source revision and patch

- Source HEAD at review: `6309dc182e8c50018183733ab15f626f13e2f094` (`6309dc1`).
- Calendar implementation history: `e0100a4..6309dc1` (DC-I01 through DC-I05). DC-I06 adds the source corrections listed below and the documentation handoff as working-tree changes on top of HEAD.
- The workspace already contained unrelated changes before this handoff: modified `app/build.gradle.kts` and untracked `app/production/`. They were not changed or included in the calendar source inventory.
- No commit was created for DC-I06. The requested commit message is supplied in the task response.

## Source review

- **Preference ownership:** `LumaApplication.calendarPreferences` is the production owner of the single `app_calendar/selected_calendar` preference. Settings persists through this owner; writes serialize and publish only after a successful disk commit. The system resolver reads the primary system locale independently from Luma's app locale (LocaleManager on API 33+, system Resources on API 28–32).
- **Calendar route state:** `CalendarRoute` passes the effective Settings policy into its back-stack-scoped `CalendarViewModel`. The page control calls only `setViewCalendarSystem`, which saves the override/signature to that entry's `SavedStateHandle`; it does not write the app preference. Settings-default changes invalidate a stale override.
- **Formatter and cache keys:** Display policies include the effective calendar and app display locale; Calendar memoization includes date, active calendar and locale. Memory grouping and entry-share formatting include the active calendar. Persian ICU formatters select the calendar explicitly; mutable ICU arithmetic instances are operation-confined.
- **Calendar math:** Shared domain `CalendarMath` backs the Gregorian `java.time` implementation and Android ICU Persian adapter. The source scan found no second Persian conversion implementation. Grid/range/month movement use shared math.
- **Resource parity and design:** Calendar Settings and switch strings are present in English, French and Persian resources. The Settings card and Calendar control use existing Luma surfaces, chips, theme roles and `LumaUi` sizing tokens.
- **Canonical-date boundary:** Calendar math and display consume ISO `LocalDate` values. The implementation file inventory contains no database/schema migration and no changes to repository storage, task/reminder rules, route arguments, report-period persistence, or machine-date payloads. UI route state remains ISO-backed.

- **Picker invalid input:** a null/invalid Material selection now clears the shared tentative selection while retaining its displayed month, so Confirm cannot submit a stale date.
- **Widget preference ownership:** WidgetRenderer now reads the application-owned selection StateFlow. The worker, configuration flow and remote collection wait for `CalendarPreferences.awaitSelection()` before rendering actual dated rows.
- **Follow system summary:** CalendarPolicy carries a system-derived default separately from the effective app choice; the Settings row now previews the result of Follow system even when an explicit override is active.

The review found these three source mismatches and corrected them in DC-I06. No known source-level ownership, wiring, resource-parity or duplicate-math conflict remains. Runtime behavior remains unverified.

## Touched-file inventory

The following files are in the dual-calendar implementation range `e0100a4..6309dc1`.

### Application calendar foundation

- `app/src/main/java/com/luma/lifeos/LumaApplication.kt`
- `app/src/main/java/com/luma/lifeos/calendar/CalendarDateFormatter.kt`
- `app/src/main/java/com/luma/lifeos/calendar/CalendarPolicy.kt`
- `app/src/main/java/com/luma/lifeos/calendar/CalendarPreferences.kt`
- `app/src/main/java/com/luma/lifeos/calendar/IcuPersianCalendarMath.kt`
- `app/src/main/java/com/luma/lifeos/calendar/SystemCalendarLocale.kt`
- `app/src/main/java/com/luma/lifeos/language/DisplayTimePolicy.kt`
- `app/src/main/java/com/luma/lifeos/language/LocalizedFormats.kt`

### Calendar screen, settings and shared picker

- `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`
- `app/src/main/java/com/luma/lifeos/ui/calendar/CalendarAgenda.kt`
- `app/src/main/java/com/luma/lifeos/ui/calendar/CalendarGrid.kt`
- `app/src/main/java/com/luma/lifeos/ui/calendar/CalendarRoute.kt`
- `app/src/main/java/com/luma/lifeos/ui/calendar/CalendarScreen.kt`
- `app/src/main/java/com/luma/lifeos/ui/calendar/CalendarSystemControl.kt`
- `app/src/main/java/com/luma/lifeos/ui/calendar/CalendarUiState.kt`
- `app/src/main/java/com/luma/lifeos/ui/calendar/CalendarViewModel.kt`
- `app/src/main/java/com/luma/lifeos/ui/settings/CalendarSettingsCard.kt`
- `app/src/main/java/com/luma/lifeos/ui/common/LumaCalendarDatePicker.kt`
- `app/src/main/java/com/luma/lifeos/ui/common/LumaCalendarDatePickerState.kt`

### Date-display and picker consumers

- `app/src/main/java/com/luma/lifeos/sharing/EntryShareRoute.kt`
- `app/src/main/java/com/luma/lifeos/ui/QuickCaptureSheet.kt`
- `app/src/main/java/com/luma/lifeos/ui/backup/BackupSettingsCard.kt`
- `app/src/main/java/com/luma/lifeos/ui/common/CaptureDateChoice.kt`
- `app/src/main/java/com/luma/lifeos/ui/common/CaptureTimestampRow.kt`
- `app/src/main/java/com/luma/lifeos/ui/focus/FocusGoalSettings.kt`
- `app/src/main/java/com/luma/lifeos/ui/focus/FocusHistoryRoute.kt`
- `app/src/main/java/com/luma/lifeos/ui/focus/FocusSessionDetailContent.kt`
- `app/src/main/java/com/luma/lifeos/ui/focus/WeeklyFocusCard.kt`
- `app/src/main/java/com/luma/lifeos/ui/habits/HabitDetailRoute.kt`
- `app/src/main/java/com/luma/lifeos/ui/habits/HabitEditorRoute.kt`
- `app/src/main/java/com/luma/lifeos/ui/habits/HabitHistory.kt`
- `app/src/main/java/com/luma/lifeos/ui/habits/HabitLogCorrectionDialog.kt`
- `app/src/main/java/com/luma/lifeos/ui/habits/HabitsScreen.kt`
- `app/src/main/java/com/luma/lifeos/ui/habits/PendingHabitFeedback.kt`
- `app/src/main/java/com/luma/lifeos/ui/habits/TodayHabitsCard.kt`
- `app/src/main/java/com/luma/lifeos/ui/health/HealthSettingsCard.kt`
- `app/src/main/java/com/luma/lifeos/ui/journal/JournalDetailRoute.kt`
- `app/src/main/java/com/luma/lifeos/ui/memory/MemoryLibraryRoute.kt`
- `app/src/main/java/com/luma/lifeos/ui/memory/MemoryLibraryState.kt`
- `app/src/main/java/com/luma/lifeos/ui/memory/MemoryReviewRoute.kt`
- `app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorScreen.kt`
- `app/src/main/java/com/luma/lifeos/ui/people/PeopleRoute.kt`
- `app/src/main/java/com/luma/lifeos/ui/places/PlacesRoute.kt`
- `app/src/main/java/com/luma/lifeos/ui/review/ReviewHabits.kt`
- `app/src/main/java/com/luma/lifeos/ui/review/ReviewHeader.kt`
- `app/src/main/java/com/luma/lifeos/ui/review/ReviewOverviewMemory.kt`
- `app/src/main/java/com/luma/lifeos/ui/review/ReviewSections.kt`
- `app/src/main/java/com/luma/lifeos/ui/review/ReviewSignals.kt`
- `app/src/main/java/com/luma/lifeos/ui/review/ReviewSourcesRoute.kt`
- `app/src/main/java/com/luma/lifeos/ui/review/ReviewUsageRoute.kt`
- `app/src/main/java/com/luma/lifeos/ui/review/WeeklyReviewRoute.kt`
- `app/src/main/java/com/luma/lifeos/ui/search/ActiveSearchFilters.kt`
- `app/src/main/java/com/luma/lifeos/ui/search/SearchFilters.kt`
- `app/src/main/java/com/luma/lifeos/ui/search/SearchRoute.kt`
- `app/src/main/java/com/luma/lifeos/ui/timeline/DayNavigation.kt`
- `app/src/main/java/com/luma/lifeos/ui/timeline/EventCard.kt`
- `app/src/main/java/com/luma/lifeos/ui/timeline/EventDetailRoute.kt`
- `app/src/main/java/com/luma/lifeos/ui/today/TodayDateNotice.kt`
- `app/src/main/java/com/luma/lifeos/ui/todo/TodoEditorScreen.kt`
- `app/src/main/java/com/luma/lifeos/ui/todo/TodoRow.kt`
- `app/src/main/java/com/luma/lifeos/ui/usage/UsageSettingsRoute.kt`
- `app/src/main/java/com/luma/lifeos/widget/WidgetRenderer.kt`

### Resources, domain contracts and planning records

- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-fr/strings.xml`
- `app/src/main/res/values-fa/strings.xml`
- `core/domain/src/main/kotlin/com/luma/lifeos/domain/calendar/CalendarMath.kt`
- `core/domain/src/main/kotlin/com/luma/lifeos/domain/calendar/CalendarRules.kt`
- `core/domain/src/main/kotlin/com/luma/lifeos/domain/calendar/CalendarSystem.kt`
- `core/domain/src/main/kotlin/com/luma/lifeos/domain/calendar/GregorianCalendarMath.kt`
- `docs/DUAL_CALENDAR_TASKS.md`
- `docs/superpowers/plans/2026-11-10-dual-calendar.md`
- `docs/superpowers/specs/2026-11-10-dual-calendar-design.md`

## DC-I06 corrective source patch

These uncommitted source changes are on top of `6309dc1` and are part of the current QA handoff:

- `app/src/main/java/com/luma/lifeos/ui/common/LumaCalendarDatePicker.kt`
- `app/src/main/java/com/luma/lifeos/ui/common/LumaCalendarDatePickerState.kt`
- `app/src/main/java/com/luma/lifeos/calendar/CalendarPreferences.kt`
- `app/src/main/java/com/luma/lifeos/calendar/CalendarPolicy.kt`
- `app/src/main/java/com/luma/lifeos/ui/settings/CalendarSettingsCard.kt`
- `app/src/main/java/com/luma/lifeos/widget/WidgetRenderer.kt`
- `app/src/main/java/com/luma/lifeos/widget/WidgetRefreshWorker.kt`
- `app/src/main/java/com/luma/lifeos/widget/WidgetCollectionFactory.kt`
- `app/src/main/java/com/luma/lifeos/widget/WidgetConfigurationActivity.kt`

## Risks and QA handoff

- Android ICU calendar behavior and formatting can vary by API/ICU version. Verify independent fixtures and ISO years 1–9999 on API 28 and API 33+; source inspection cannot close this risk.
- Persisted selection, failed/rapid writes, Calendar override restoration/reset, marker invalidation, picker drafts/bounds, widget refresh, time-zone/DST handling and all UI accessibility/layout combinations remain unverified.
- The existing build-working-tree edits noted above may affect the requested build attempt; they were preserved.
- DC-Q01 owns every test, lint, install, emulator/device and manual matrix check. All acceptance evidence remains pending.

## DQA-01–10

| ID | Scenario | DC-I06 status |
| --- | --- | --- |
| DQA-01 | System-language defaults and app-language independence | NOT RUN |
| DQA-02 | Preference persistence and Calendar-view isolation/lifecycle | NOT RUN |
| DQA-03 | Persian/Gregorian conversion, leap and supported-boundary fixtures | NOT RUN |
| DQA-04 | Grid alignment, month movement/clamping and ISO boundary slots | NOT RUN |
| DQA-05 | Same-day safety, stale markers and preserved task/memory actions | NOT RUN |
| DQA-06 | All shared date-picker hosts, bounds, cancel/confirm and drafts | NOT RUN |
| DQA-07 | App-wide date surfaces, memory grouping, widgets and machine data | NOT RUN |
| DQA-08 | API/lifecycle/time-zone/DST and 12/24-hour behavior | NOT RUN |
| DQA-09 | Locale, calendar, theme, RTL, large-text, TalkBack and inset matrix | NOT RUN |
| DQA-10 | Final-source build/lint/regression evidence | NOT RUN |

### Commands and device results

- Unit/instrumentation test authoring and execution: NOT RUN.
- Lint and Android test APK assembly: NOT RUN.
- API 28 and API 33+ device/emulator runs and screenshots: NOT RUN.
- Light/Dark/System, EN/FR/FA, RTL, font scale 2.0, TalkBack and widget matrix: NOT RUN.
- The direct `./gradlew :app:assembleProductionDebug` invocation could not start because the workspace wrapper is not executable (`Permission denied`).
- Android Studio Gradle task `:app:assembleProductionDebug` was run twice: once after the initial handoff draft and once after the DC-I06 source corrections. Both failed at `:app:processProductionDebugResources` because AAPT2 Daemon #0 could not start (`Failed to exec spawn helper`, exit value 1). The final-patch attempt did not complete the build and is not DQA-10 or DC-Q01 evidence.
