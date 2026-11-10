# Dual Calendar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. The user's sequencing overrides per-task test/review gates: finish every DC-I task, then perform DC-Q01 once. Do not introduce per-task QA phases.

**Goal:** Provide persistent Shamsi/Gregorian calendar selection in Settings and a page-local Calendar view switch, with system-language defaults and consistent app date presentation.

**Architecture:** Preserve ISO civil dates and instants at every persistence/repository boundary. Add a platform-free calendar arithmetic contract, an Android ICU Persian adapter, one observable preference source, and shared calendar-aware formatters/picker content. Calendar derives a page-local view policy from Settings plus its saved view override; other surfaces and memory-library grouping use Settings directly. Stored schedules and report intervals stay unchanged.

**Tech Stack:** Existing Kotlin, java.time, coroutines/StateFlow, SharedPreferences, Android ICU (API 24+, app minSdk 28), Compose Material 3 and Luma UI components. No new production dependency or SDK upgrade is planned.

**Spec:** [2026-11-10-dual-calendar-design.md](../specs/2026-11-10-dual-calendar-design.md) — read before implementation.

## Global Constraints

- Support exactly Persian Solar Hijri (Shamsi/Jalali) and Gregorian calendar types; `system` is a preference mode.
- Missing or unknown preference → primary device language `fa` means Persian; every other language means Gregorian. Luma's app language is independent.
- Preference storage: private `app_calendar`, key `selected_calendar`, stable values `system`, `persian`, `gregorian`.
- Settings alone changes the app-wide preference. The quick switch changes only Calendar route view state; it never writes app preferences or changes external editors/widgets. Switching preserves selected ISO day, mode, filters, drafts and pending actions.
- Proposed view lifecycle: retain the override with the Calendar back-stack entry, reset on a new entry or changed Settings preference/effective default, and validate the saved default signature before restoring.
- Preserve canonical ISO Calendar years 1–9999, repository bounds of at most 42 inclusive dates, all storage/route/archive formats, time zones and 12/24-hour behavior.
- Persian grid week start is Saturday; Gregorian uses the current app-locale week start. Stored schedules and report intervals do not change.
- Use warm-paper/obsidian MaterialTheme roles, bundled typography/shapes, `LumaUi`, shared Luma components and existing scaffolds.
- Support System/Light/Dark, English/French/Persian, RTL, TalkBack, at least 48 dp targets and font scale 2.0.
- Finish all implementation tasks before DC-Q01. Until then use source inspection only: no test authoring/execution, Gradle builds/lint, APK installs or emulator/device sessions.
- DC-Q01 is the only QA task; it includes automated/manual checks, defects and targeted retests.
- Keep unrelated working-tree changes intact; at planning time these include WidgetActionReceiver.kt, WidgetIntents.kt and app/production/. Reinspect status before execution.

## Review Focus

1. Device language differs from app language: automatic calendar follows the primary device language on both API paths; DQA-01/02 test this.
2. Nowruz/leap Esfand and extreme ISO years: real fixtures and signed extended-year conversion preserve the same day; DQA-03/04 test this.
3. Page switching leaks into global state or races marker loads: Settings, other routes, editors and widgets stay unchanged, and stale markers cannot replace the view; DQA-02/05 test this.
4. Picker bounds and UTC/local-midnight bridges: all hosts return the intended ISO date and cancel safely; DQA-06/08 test this.
5. ISO YearMonth headings and legacy formatter caches: memory grouping and every visible date react to the preference without changing report semantics; DQA-07/09 test this.

## File ownership map

Paths below are repository-relative. New file names/interfaces are proposed implementation decisions.

| Area | Files | Responsibility |
| --- | --- | --- |
| Domain math | Create `core/domain/src/main/kotlin/com/luma/lifeos/domain/calendar/CalendarSystem.kt`, `CalendarMath.kt`, `GregorianCalendarMath.kt`; modify adjacent `CalendarRules.kt` | Typed date parts/month IDs and pure calendar-aware grid/range/navigation rules; existing task eligibility stays unchanged. |
| Android policy | Create `app/src/main/java/com/luma/lifeos/calendar/IcuPersianCalendarMath.kt`, `CalendarPreferences.kt`, `SystemCalendarLocale.kt`, `CalendarDateFormatter.kt`, `CalendarPolicy.kt` | ICU boundary, durable observable choice, actual system locale, localized strings and lifecycle-aware policy. |
| Settings | Create `app/src/main/java/com/luma/lifeos/ui/settings/CalendarSettingsCard.kt`; modify `ui/LifeOsApp.kt`, `ui/settings/SettingsRoute.kt` and `res/values{,-fr,-fa}/strings.xml` | Existing Appearance & language destination, discoverability and translated selection/error copy. |
| Calendar | Modify `ui/calendar/CalendarUiState.kt`, `CalendarViewModel.kt`, `CalendarRoute.kt`, `CalendarScreen.kt`, `CalendarGrid.kt`, `CalendarAgenda.kt`; create adjacent `CalendarSystemControl.kt` | Policy input, canonical selection, request invalidation, grid and page-local fast switch. |
| Shared picker | Create `ui/common/LumaCalendarDatePicker.kt` and `LumaCalendarDatePickerState.kt`; use existing `LumaUiComponents.kt` dialog shell | Calendar-aware content with ISO inputs/outputs, bounds and tentative state; no second feature-specific picker family. |
| Picker consumers | Modify `ui/timeline/DayNavigation.kt`, `ui/todo/TodoEditorScreen.kt`, `ui/common/CaptureTimestampRow.kt`, `ui/habits/HabitEditorRoute.kt`, `HabitLogCorrectionDialog.kt`, `ui/search/SearchFilters.kt`, `ui/review/WeeklyReviewRoute.kt` | Replace direct date-picker content, preserving each host's draft/confirm/cancel contract. |
| Display migration | Modify `language/DisplayTimePolicy.kt`, `LocalizedFormats.kt` and UI callers listed in DC-I05 | Remove ISO-only human-display assumptions; time-only formatter stays intact. |
| Month grouping | Modify `ui/memory/MemoryLibraryState.kt`, `MemoryLibraryRoute.kt` | Active-calendar display month IDs and headings; bounded newest-first records unchanged. |
| Widget date text | Modify `widget/WidgetRenderer.kt`; reuse `widget/WidgetRefreshCoordinator.kt` | Same policy for due-date text and refresh; separate widget layout/action contract preserved. |
| Handoff | Create `docs/validation/2026-11-10-dual-calendar.md` at source handoff; update `docs/DUAL_CALENDAR_TASKS.md` and redesign route/checklist evidence references | Honest source-complete/NOT RUN status, exact touched files and one QA record. |

All `ui/` paths above are under `app/src/main/java/com/luma/lifeos/`. Rescan date formatting/picker usages during DC-I05 and record every additional affected file in the handoff; do not treat this seed inventory as proof of all-app coverage.

## DC-I01 — Calendar arithmetic and conversion boundary

**Depends on:** None. **Spec:** DC-04/06. **Deliverable:** Calendar-aware date parts/ranges/navigation without data-model changes.

**Interfaces:**

- `enum class CalendarSystem { PERSIAN, GREGORIAN }`
- `data class CalendarDateParts(val year: Int, val month: Int, val day: Int)` — month 1–12; year is proleptic extended year.
- `data class CalendarMonthId(val system: CalendarSystem, val year: Int, val month: Int)`
- `interface CalendarMath { val system: CalendarSystem; fun fromIso(date: LocalDate): CalendarDateParts; fun toIso(parts: CalendarDateParts): LocalDate; fun monthLength(year: Int, month: Int): Int }`
- `GregorianCalendarMath : CalendarMath`; `IcuPersianCalendarMath : CalendarMath`.
- Extend `calendarRange`, `moveCalendarSelection`, `canMoveCalendarSelection` with `math: CalendarMath = GregorianCalendarMath`; add `calendarGridDates(selected: LocalDate, mode: CalendarMode, firstDay: DayOfWeek, math: CalendarMath): List<LocalDate?>`. Keep existing callers source-compatible until migrated.

- [x] Inspect current Calendar domain bounds/eligibility and the duplicated grid helper; preserve task/overdue rules.
- [x] Add typed parts/month IDs and Gregorian math using java.time, with explicit argument validation and invalid-date rejection.
- [x] Implement Persian math using public `android.icu.util.Calendar.getInstance(UTC, ULocale(...calendar=persian...))`, `EXTENDED_YEAR`, explicit type checking and non-lenient validation. Keep Android types out of `:core:domain`.
- [x] Bridge date-only values via ISO epoch days and UTC milliseconds with correct negative-day division; use independent confined ICU instances, never shared mutable globals.
- [x] Make shared grid and repository range derive from the same active-calendar month boundaries. Use null slots outside ISO years 1–9999; handle intermediate dates outside that range without passing them to guarded repository models.
- [x] Implement month movement by active calendar fields and day clamping; weeks move seven ISO days. Clip canonical bounds and disable outward arrows appropriately.
- [x] Source-inspect leap Esfand, Nowruz, Gregorian February and partial edge months. Record intended fixtures for DC-Q01; do not author/run tests yet.

**DC-I01 source review (2026-11-10):** Calendar task eligibility/overdue rules and repository date bounds remain unchanged. The Persian adapter delegates leap Esfand and Nowruz conversion to strict, explicitly selected ICU Persian calendar math. DC-Q01 fixtures queued from the specification: `2024-03-20 ↔ 1403-01-01`, `2025-03-20 ↔ 1403-12-30`, `2025-03-21 ↔ 1404-01-01`; reject `1404-12-30`. These fixtures have not been executed. No persistence/schema or stored-date changes were made.

## DC-I02 — Durable preference, lifecycle policy and Settings

**Depends on:** I01 types. **Spec:** DC-01/02/07. **Deliverable:** One persisted, observable choice available to every consumer.

**Interfaces:**

- `enum class CalendarPreference(val storageValue: String) { SYSTEM("system"), PERSIAN("persian"), GREGORIAN("gregorian") }`
- `fun resolveCalendarSystem(preference: CalendarPreference, primarySystemLocale: Locale?): CalendarSystem`.
- `CalendarPreferences.selection: StateFlow<CalendarPreference>`; `suspend fun select(value: CalendarPreference): Result<Unit>` serializes durable writes on an I/O dispatcher and publishes only successful writes.
- `CalendarPolicy(preference: CalendarPreference, system: CalendarSystem, displayLocale: Locale, firstDay: DayOfWeek)`.
- `rememberCalendarPolicy()` observes the shared store, app configuration and lifecycle resume; `calendarMathFor(system: CalendarSystem): CalendarMath` supplies adapters.

- [x] Read the required shared theme/tokens/components/scaffold and redesign references before UI edits; preserve the current palette.
- [x] Add system-locale lookup for API 33+ and 28–32. Treat missing, unknown or malformed stored values as SYSTEM without persisting the derived calendar.
- [x] Add an application-context preference store with lifetime-safe observation, serialized writes and accessible failure state. No main-thread `commit()`.
- [x] Resolve effective type on start/configuration/resume and propagate only effective changes; app language controls display locale separately.
- [x] Add CalendarSettingsCard after LanguageSettingsCard in `settings_language`; update localized hub title/description as needed without renaming the existing route or tags.
- [x] Add Follow system/effective-type supporting text, Persian (Shamsi), Gregorian and retry copy in EN/FR/FA with identical format placeholders.
- [x] Trigger existing widget refresh only after accepted effective changes, including system-default changes on resume. No database/archive changes.
- [x] Source-inspect settings → preference → observer wiring and save-failure/rapid-write handling. Mark DQA-01/02 pending.

**DC-I02 source review (2026-11-10):** One application-owned `CalendarPreferences` instance stores `selected_calendar` in `app_calendar`; initial reads and serialized durable writes run on `Dispatchers.IO`, and its `StateFlow` updates only after `commit()` succeeds. The API-specific resolver reads only the primary device locale, treats unknown/empty values as SYSTEM/Gregorian as specified, and recomputes on configuration and resume while using the app configuration for display locale. Settings uses the existing Language destination, shared Luma section surfaces/tokens, stable row/error tags, retry feedback, and matching EN/FR/FA placeholders. The app root requests the existing widget refresh only when the accepted effective calendar changes. DQA-01/02 remain pending; no tests, Gradle tasks, installs or device checks were run.

**Ruling:** The shared policy includes an initialization-ready flag and Settings disables its choices until the asynchronous preference read completes. This keeps preference reads off the main thread and prevents an initial placeholder SYSTEM value from appearing selected or triggering a widget refresh; the cost if wrong is a brief delay before the Settings rows can be changed.

## DC-I03 — Calendar screen and quick switch

**Depends on:** I01–I02. **Spec:** DC-03/04. **Deliverable:** Both real calendar grids and an isolated page-local fast switch.

**Interfaces:**

- Add `calendarSystem: CalendarSystem` to `CalendarUiState` for the resolved page view, plus a nullable `viewCalendarOverride: CalendarSystem?`. Resolve override ?: Settings default; save the override as `calendar_view_system` and its default signature in this route's SavedStateHandle, never app preferences.
- Inject `calendarMathFor: (CalendarSystem) -> CalendarMath` into CalendarViewModel; update production factory. Existing test construction is adapted during DC-Q01.
- Extend `setEnvironment(today, zone, gregorianFirstDay, restoreGeneration, appCalendarPreference, appCalendarSystem)`; resolve the page view and its week start atomically. A changed preference/effective default clears the override; invalid restored signatures are discarded. Add `setViewCalendarSystem(system: CalendarSystem)` for the local switch, clearing the override when the app default is selected.
- `CalendarSystemControl(system: CalendarSystem, onSelect: (CalendarSystem) -> Unit, enabled: Boolean)` uses existing Luma choices and stable tags.

- [x] Feed the lifecycle-aware Settings policy into CalendarRoute/ViewModel as the default. Keep saved selected date/mode/filter keys canonical and backward-compatible. Derive a page-only formatting policy from the resolved view; never override a global CompositionLocal/store.
- [x] Use shared math for ViewModel range keys, arrows, grid slots, day numbers, selected-month comparison and headings. Delete the private Gregorian-only grid implementation after migration.
- [x] On effective system/week alignment change, invalidate marker state and restart bounded marker requests. Include active calendar identity in stale-response guards even where ISO bounds happen to match.
- [x] Preserve selected day, task/memory agenda pages, filters and pending task actions on a calendar-only change; preserve current zone/restore invalidation rules.
- [x] Add the two labeled choices near Month/Week. Stack at narrow/large-text sizes, retain Today/period navigation and use accessible selected semantics.
- [x] Wire the control only to `setViewCalendarSystem`; selecting the displayed type does nothing. Save local view/default-signature state in this back-stack entry for recreation/editor return, reset for a new entry or changed Settings default, and clear the override when the app default is selected. Never call CalendarPreferences.select or widget refresh from the quick switch.
- [x] Migrate agenda/ribbon/overdue/badge/spoken date text to shared formatting with the page-local view policy. Retain ISO-derived day test tags and exact task/memory callbacks. Open external editors/Quick Capture with canonical dates and the unchanged Settings policy.
- [x] Source-inspect request-key transitions; track DQA-03/04/05 obligations for the DC-I06 handoff; do not run UI/tests.

**DC-I03 source review (2026-11-10):** CalendarRoute passes the lifecycle-aware Settings policy into a CalendarViewModel scoped to the Calendar back-stack entry. The VM retains canonical selected dates, mode, filters and agenda state; local view state is saved separately as `calendar_view_system` with the preference/effective-calendar signature. Active-calendar math drives range keys, arrows, grids, overflow styling, day numbers and headings. Calendar-only changes reload marker ranges, and marker keys include calendar identity plus a generation so stale responses are rejected even after rapid A→B→A switching. Calendar dates, ribbon, task badges and accessibility descriptions use an explicit page-local ICU/ISO formatter with a UTC date bridge. The switch uses localized Luma filter chips and stacks its choices and period arrows at constrained widths/large text. The independent source review found no Critical issues; its paging, layout and visibility findings were addressed. Android Studio file analysis reports no code errors. Tests and device checks remain NOT RUN for DC-Q01.

**Build result:** The requested `:app:assembleProductionDebug` build did not complete. It failed during `:app:processProductionDebugResources` because AAPT2 Daemon #0 could not start (`Failed to exec spawn helper`). No test task was run.

**Ruling:** The direct task instruction requested one Gradle build after DC-I03 implementation, so `:app:assembleProductionDebug` ran before DC-I04–I06 despite the plan's all-implementation-before-build sequence. This is not DC-Q01 and does not establish acceptance; the cost if this sequencing exception is wrong is a build attempt before the planned final QA boundary.

## DC-I04 — Shared calendar-aware date picking

**Depends on:** I01–I02. **Spec:** DC-05/06/07. **Deliverable:** Existing picker hosts support both calendar types with the same ISO contracts.

**Interfaces:**

- `rememberLumaCalendarDatePickerState(initialDate: LocalDate?, displayedDate: LocalDate, minDate: LocalDate, maxDate: LocalDate)` owns tentative selected ISO date and displayed month anchor as saveable ISO values.
- `LumaCalendarDatePicker(state: LumaCalendarDatePickerState, policy: CalendarPolicy, isSelectable: (LocalDate) -> Boolean, modifier: Modifier = Modifier)` renders content only, so timestamp dialogs can retain their time input.
- State exposes `selectedDate: LocalDate?`; the caller retains ownership of Confirm/Cancel/Back and draft mutation.
- Bounds remain ISO dates and the selected type comes from policy. Gregorian delegates to Material with UTC bridge values; Persian uses shared math and Luma controls.

- [ ] Inventory each host's existing limits, disabled-day constraints, null handling, open/restore state and test tags before migration.
- [ ] Build shared picker state/content with real Persian month/year/day selection, efficient month/year chooser, selected semantics and at least 48 dp targets. Reuse LumaDatePickerDialog or the existing timestamp dialog shell.
- [ ] Keep tentative selection when policy changes/recreation occurs; recompute the visible active month from the same ISO anchor. Do not commit as a side effect of recomposition.
- [ ] Translate each host's existing ISO year/date bounds to allowed days, including partial Persian years. Prevent invalid selections and leave Confirm disabled for null/invalid values.
- [ ] Migrate DayNavigation, TodoEditorScreen, CaptureTimestampRow, HabitEditorRoute, HabitLogCorrectionDialog, SearchFilters and WeeklyReviewRoute.
- [ ] Preserve Gregorian input mode and timestamp TimeInput/zone/DST behavior; provide a usable Persian year/month chooser without introducing raw numeric Persian input in this feature.
- [ ] Source-inspect Confirm-once/Cancel-never behavior, draft preservation, nullable task dates, scrolling/IME/insets and tags across every consumer. DQA-06/08 remain pending.

## DC-I05 — App-wide display, month grouping and widget date consistency

**Depends on:** I01–I04. **Spec:** DC-05/06/07. **Deliverable:** Settings-driven date presentation throughout the app, with an intentional isolated Calendar page override.

**Interfaces:**

- `CalendarDateFormatter.formatDate(date: LocalDate, style: FormatStyle, system: CalendarSystem, locale: Locale): String` and `formatMonth(date: LocalDate, system: CalendarSystem, locale: Locale): String`.
- Extend DisplayTimePolicy with effective calendar and `formatDate(date: LocalDate, style: FormatStyle): String`; `formatDateTime(value: ZonedDateTime, style: FormatStyle): String` uses that value's local date and existing time formatter.
- Add `formatDateTime(value: LocalDateTime, style: FormatStyle): String` where callers already own a local timestamp. Instant callers explicitly supply their existing display zone before formatting.
- Replace ISO-only `date()`/`dateTime()` DateTimeFormatter usage for human-readable app dates; do not invent a java.time Persian chronology. Keep time-only functions unchanged.
- `memoryLibraryItems(records, zone, math)` returns month headings keyed by `CalendarMonthId`; records retain event-ID keys/order.

- [ ] Create explicit calendar-aware formatting with locale-selected names/digits; Persian uses the same ICU authority as arithmetic, Gregorian preserves proleptic ISO formatting.
- [ ] Migrate DisplayTimePolicy/LocalizedFormats consumers and caches to include effective calendar. Replace implicit Locale.getDefault-based date formatting in UI with observable Settings policy input; pass Calendar's view policy explicitly only inside that page.
- [ ] Cover Calendar; QuickCaptureSheet/CaptureDateChoice; TodayDateNotice/DayNavigation; task list/editor badges; habit list/detail/history/correction/feedback; capture/editor/detail timestamps; Timeline EventCard/EventDetail; Review header/sections/signals/habits/sources/usage; Focus goal/history/session/weekly cards; Search filters/results; Memory library/review; people/places; health/usage/backup metadata; EntryShareRoute.
- [ ] Change MemoryLibraryState/Route display grouping to active-calendar year/month. Preserve bounded query/paging state, newest-first order and event scroll anchors; do not relabel ISO YearMonth groups.
- [ ] Format widget due-date text using the effective Settings choice and request existing refresh only for app-wide changes; preserve WidgetRenderer visual roles, action/configuration protocols and unrelated widget changes.
- [ ] Search production sources for `localizedDate`, `ofLocalizedDate`, `ofLocalizedDateTime`, date-bearing `ofPattern`, `YearMonth`, `DatePicker`, `DisplayTimePolicy` and user-visible date `toString` interpolation. Classify every remaining occurrence as canonical/machine, fixed semantic interval, or migrated display.
- [ ] Record the complete final file/call-site inventory in the handoff. Keep SQL/archive/route dates, reminders, habit rules, report snapshots and Focus intervals unchanged.

## DC-I06 — Source completion and final QA handoff

**Depends on:** I01–I05 complete. **Spec:** All. **Deliverable:** Reviewable implementation ready for the single QA task.

- [ ] Perform source-only consistency review of preference ownership, constructor/callback wiring, formatter cache keys, duplicated date math, resource parity and unchanged canonical boundaries.
- [ ] Update task statuses to Source complete; QA pending, never Passed/Accepted.
- [ ] Create `docs/validation/2026-11-10-dual-calendar.md` with actual source revision/patch, touched-file inventory, implementation decisions and known risks. Mark every DQA scenario and every command/device result NOT RUN.
- [ ] Update redesign route/checklist ownership/evidence references for Settings, Calendar, shared date pickers and affected app surfaces without changing historical acceptance results.
- [ ] Confirm all I tasks are complete and tests/builds/device work have not started. Hand all DQA-01–10 obligations to DC-Q01.
- [ ] Commit only feature files when requested/appropriate; do not stage unrelated working-tree changes.

## DC-Q01 — One consolidated final QA, defect fixing and acceptance

**Depends on:** Every I task complete and I06 handoff recorded. **Spec:** DQA-01–10. **Deliverable:** Current-source evidence and an explicit acceptance decision.

This is the only QA task. The subsections below are activities within it, not separate QA tasks or per-implementation gates.

- [ ] Record the actual runtime/test environment and pre-existing failures. Use a dedicated QA install (`com.luma.lifeos.qa`), API 28 and API 33+ devices/emulators; preserve the user's data.
- [ ] Author domain tests in `core/domain/src/test/kotlin/com/luma/lifeos/domain/calendar/CalendarRulesTest.kt` and new `CalendarMathTest.kt`: Gregorian regression, injected alternate math, grid/range agreement, month-end clamping, max 42 days, null edge slots, years 1–9999 and week movement.
- [ ] Author `app/src/test/java/com/luma/lifeos/calendar/CalendarPreferenceResolverTest.kt` for primary system locale, app-locale independence, invalid preference and explicit override cases. Adapt existing CalendarViewModel tests for new injection; add `CalendarViewModelTest` cases for switching, same-day preservation and stale results.
- [ ] Author on-device `app/src/androidTest/java/com/luma/lifeos/calendar/PersianCalendarMathTest.kt` and `CalendarDateFormattingTest.kt`: independent Nowruz/leap fixtures from DQA-03, rejection of invalid Esfand, extreme ISO round trips, pre-1582 Gregorian behavior, FA/EN/FR cross-calendar formatting, midnight/DST and time-format invariance. Do not run platform ICU APIs through mock android.jar host stubs.
- [ ] Author `CalendarPreferenceFlowTest.kt` for persistent Settings selection, failure/rapid-write behavior, Follow system, process recreation and app/system language independence. Add assertions that Calendar switching leaves preference storage, other routes, editors, memory grouping and widgets unchanged; verify local restoration, editor return, new-entry reset and default-change reset. Extend existing `CalendarFlowTest.kt` and `CalendarSemanticsTest.kt` for selected state and stable canonical day tags.
- [ ] Author `CalendarDatePickerFlowTest.kt` exercising every picker host, both calendars, caller bounds, tentative state, Confirm/Cancel/Back and draft restoration; cover memory grouping, human entry-share text and widget refresh/date regressions with meaningful fixtures.
- [ ] Run host checks sequentially: `./gradlew :core:domain:test :core:data:testDebugUnitTest :app:testProductionDebugUnitTest`. Expected: all pass, new counts and any baseline failures documented.
- [ ] Run build/static checks sequentially: `./gradlew :app:assembleProductionDebug :app:assembleProductionDebugAndroidTest :app:lintProductionDebug` and `git diff --check`. Expected: compilation, resource merge, lint and whitespace checks pass.
- [ ] Run `./gradlew :core:data:connectedDebugAndroidTest :app:connectedProductionDebugAndroidTest` against isolated QA devices, serially for API 28 and API 33+. Expected: required Room/Compose/integration scenarios pass with no unexplained skips. Record serials/API/ICU and final app build.
- [ ] Execute the complete DQA-01–10 matrix, including EN/FR/FA × both calendars × Light/Dark, System theme transitions, RTL, 320 dp/wide windows, font scale 2.0, offline fonts, TalkBack, system-language changes, restore, selected-day data identity and widget behavior.
- [ ] Fix discovered defects within DC-Q01 and rerun affected tests/checks on the final source patch; broaden only when the defect affects shared behavior. Keep one defect/evidence log.
- [ ] Record commands, results/counts, screenshots, fixture sources, outstanding failures and final revision/patch in the validation record. Mark acceptance PASS only when all required checks pass; missing coverage remains BLOCKED/NOT RUN. Historical results never substitute for this feature's QA.

## Handoff

This package is proposed documentation. Implementation and QA are NOT RUN. The page-local switch is a confirmed user requirement. App-wide Settings scope, the local-view lifecycle and Follow system mode are explicitly proposed decisions for review; no approval or runtime result is implied.
