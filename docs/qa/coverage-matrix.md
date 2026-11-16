# Coverage matrix

The row ranges link current source-derived cases. All detailed cases begin NOT RUN. Verify each exact title/status in its group file before execution. Shared configuration cases are in UI-001..UI-010; they supplement feature cases and do not replace functional assertions.

| Feature/flow | Scenarios | Source of truth / validation anchor | Coverage note |
|---|---|---|---|
| Launch and main navigation | NAV-001..NAV-007 | AppDestinations, LifeOsApp, AppNavigationTest | Primary tabs, More links, date context, Back, cancellation, recreation |
| Today and summary | TOD-001..TOD-006 | TodayRoute, TodayScreen, TodayState, TodayScreenTest, TodayPersistenceTest, DailySummaryFlowTest | Empty/error distinction, exact counts/order, historical/live split, edits and retry |
| Timeline feed | TL-001..TL-006 | TimelineRoute, TimelineViewModel, TimelineFlowTest, TimelinePagingRouteTest | Mixed entry/log identity, date race, options, bounded 50, delete invalidation and recovery |
| Calendar | CAL-001..CAL-014, DATE-001..DATE-002 | CalendarRules, CalendarViewModel, RoomCalendarRepository, CalendarFlowTest, CalendarProjectionTest | Exact/carry-in/global overdue, task/memory actions, period/date navigation, calendar systems, zone boundaries, bounded page |
| Weekly Review | REV-001..REV-011 | WeeklyReview, WeeklyReviewRoute/State, ReviewSourcesRoute, WeeklyReviewFlowTest, WeeklyReviewTest | Partial/current comparison, metrics/samples, catalog counts, source navigation, sections, missing/unavailable and bounded sources |
| Tasks, hierarchy, reminders | TASK-001..TASK-022; WID-* system file | TodoList/Editor ViewModels, TodoRepository, TodoEditorFlowTest, TodoHierarchyFlowTest, reminder tests | Includes bounded list, editor, parent/child, notifications and recovery |
| Habits, logs, correction and schedule | HAB-001..HAB-018; WID-* system file | HabitsViewModel, HabitEditorViewModel, HabitCaptureFlowTest, HabitDateSelectionFlowTest, EntryHabitCountsFlowTest | Daily schedule, selected date/tracking zone, correction, undo, count and widget actions |
| Focus setup/session/recovery/history/goals | FOC-001..FOC-027; LOCK-* when protected | FocusViewModel, FocusRuntimeRecoveryTest, FocusLifecycleTest, FocusScreenTest, FocusReportsTest | Timer/recovery operations use controlled time; no endurance test |
| Mood capture | MOOD-001..MOOD-010 | MoodEditorViewModel, MoodEditorTest, MoodCaptureTest | Score, scales, contexts, limits, draft, failure, imported/read-only, delete and timestamp |
| Journal route and records | JRN-001..JRN-008 | JournalEditorViewModel/legacy tests, LifeOsApp | Current route resolves to Memory; legacy record/editor checks identify explicit harness/path prerequisites |
| Direct/promoted Memory and media | MEM-001..MEM-023 | MemoryEditorViewModel, MemoryCaptureTest, PhotoCaptureTest, MemoryRepository | Content vs marker, importance/tags/context, draft, photo crop/viewer/retry, library, share; media/harness blockers in feature file |
| People/Places | CAT-001..CAT-010 | Person/Place editor ViewModels, PlaceCatalogFlowTest, PeopleCaptureTest, PlaceCaptureTest | Duplicates, validation, picker return, linked histories, rename/delete/error |
| Search | SRCH-001..SRCH-011 | SearchViewModel, SearchFlowTest, SearchStatesTest, ActiveSearchFiltersTest | Search scope/date/filter semantics, debounce, lifecycle, retry, maximum 50 records |
| Theme/language/calendar/week start | SYS-001..SYS-008, UI-001/UI-003 | ThemeSettingsCard, LanguageSettingsCard, CalendarSettingsCard, WeekStartSettingsCard and tests | Persistent Light/Dark/System behavior, English/French/Persian, calendar policy and return state |
| Backup/restore/privacy reset | BAK-001..BAK-011 | BackupArchive/Service/ViewModel tests; PrivacySettingsFlowTest | Replace semantics, cancel/reject/corruption, transaction/identity, imports off, reset; disposable archive only |
| Usage/Health integrations | INT-001..INT-011, REV-008/REV-010 | Usage/Health settings/syncers, ForegroundRefreshFlowTest | Grant/revoke, exact controlled import, missing vs zero, retry and unsupported providers |
| Launcher widgets | WID-001..WID-012, UI-001..UI-006 on configuration | WidgetConfigurationActivity, WidgetRenderer, WidgetCommandExecutor and acceptance tests | Separate design contract; IDs, stale action, resize, refresh, accessibility and lock redaction |
| App lock and restricted capture | LOCK-001..LOCK-010, NAV-005, SYS-008 | Lock policy/gate/credential routes, LifeOsApp | Current typed credential rules, relock, screenshots, recovery/reset, restricted Mood/Memory/Task; test APK compatibility prerequisite |
| UI layout/accessibility/localization | UI-001..UI-010 | LumaUi tokens/components/scaffold, UI consistency contract | Matrix cases record result per case + matrix ID + surface |
| Lightweight performance | PERF-001..PERF-008 | performance.md | Fixed 3 cold/5 warm repetitions; proposed triage budgets labeled, never preaccepted |
| Cross-feature journeys | JOURNEY-001..JOURNEY-011 | cross-feature-journeys.md | End-to-end record/date/identity checks across source screens |

## Explicit unverified or bounded gaps

- Scenarios are source-derived and NOT RUN. No rendered screen, runtime, performance, or acceptance evidence is implied.
- Instrumentation test APK compile status is unverified. The system scenario file calls out stale Lock test APIs; method filtering will not skip compilation of that source.
- Some transient database/provider failures require a fake repository or controlled response seam. A manual case that cannot trigger the failure must remain BLOCKED until the named harness exists.
- Current app navigation maps Journal capture/detail to Memory. Legacy Journal tests do not establish an accessible current Journal workflow.
- Search indexing is narrower than every text field; see SRCH-002. Do not search bodies/notes unless documented.
- 50 rows cannot verify a second Calendar page or Timeline's >150 rows/window eviction. Historical tests with larger fixtures are excluded.
- Actual health/usage providers, launcher pin support, lock hardware/OS surfaces, Persian locale and 320/840 dp layouts depend on device availability.
- Calendar DST repeated-hour identity needs injected exact instants; changing device time alone cannot specify both offsets.
