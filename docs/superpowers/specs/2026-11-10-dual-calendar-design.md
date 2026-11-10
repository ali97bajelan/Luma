# Dual calendar specification: Shamsi and Gregorian

Prepared: 2026-11-10

Status: Proposed; documentation only. Implementation and QA have not started.

[Task backlog](../../DUAL_CALENDAR_TASKS.md) · [Implementation plan](../plans/2026-11-10-dual-calendar.md)

## Intent and requested behavior

Luma supports two calendar systems: Persian Solar Hijri (Shamsi/Jalali) and Gregorian. Users choose the app-wide calendar in Settings and switch only the Calendar page's view using its quick control. With no explicit choice, the device's primary system language determines the calendar: `fa` → Persian; every other language → Gregorian.

The user requested task, specification and plan documents, with **one QA task after all implementation is complete**. This package does not authorize or report app implementation.

## Proposed product decisions

Confirmed user requirement: the Calendar page switch must not change the app-wide calendar type.

Other decisions below are planning assumptions, not recorded user approvals:

- Apply the Settings preference to app-owned date labels, date pickers and display-only month grouping throughout Luma. Calendar may display a different type through its page-local override.
- Initialize Calendar from Settings. Retain a local view override only with the Calendar back-stack entry; a new entry or a changed Settings default resets it. Editors opened from Calendar continue to use Settings.
- Settings includes Follow system as a preference mode, alongside the two calendar types. While following system, a system-language change updates the effective calendar on resume.
- Persian calendar grids start on Saturday. Gregorian grids keep the existing app-locale week-start policy. Changing presentation does not alter stored habit schedules, review/report intervals or reminder rules.
- Launcher widget date text follows the preference through the existing refresh mechanism; its separate visual contract remains authoritative.

## Existing code and constraints

- `language/DisplayTimePolicy.kt` and `LocalizedFormats.kt` explicitly use ISO chronology. Translating month names alone cannot provide Persian dates.
- `core/domain/.../calendar/CalendarRules.kt` and `ui/calendar/CalendarGrid.kt` independently calculate Gregorian month bounds. Both must share calendar-aware month arithmetic.
- `CalendarViewModel` saves ISO selected dates and guards asynchronous repository results using request keys. Calendar type changes must participate in marker invalidation without changing the selected day.
- `CalendarRoute` currently derives week start from the app locale, which can differ from the device language.
- `AppLanguages.wrap` calls `Locale.setDefault` for Luma. That process default and `LocalConfiguration` must not determine the system-language calendar default.
- `settings_language` in `LifeOsApp.kt` hosts theme and language cards; extend this existing destination.
- Material date-picker consumers include shared day navigation, task due dates, capture timestamps, habit start/correction, Search and Weekly Review.
- `MemoryLibraryState.kt` groups with ISO `YearMonth`; Persian month headings require regrouping records by Persian month, not relabeling Gregorian groups.
- The app currently has minSdk 28, compileSdk 35, targetSdk 35 and the `production` flavor. No SDK or build-system upgrade is needed.

## Requirements

### DC-01 — Preference and system default

Persist stable values `system`, `persian`, `gregorian` under `selected_calendar` in private `app_calendar` preferences. Missing or unknown values resolve as `system`; do not eagerly persist the resolved default.

Resolve the primary device locale, ignoring Luma's language override: API 33+ uses `LocaleManager.systemLocales`; API 28–32 uses `Resources.getSystem().configuration.locales`, following the existing language infrastructure. Compare normalized language code to `fa`, independent of region or script. An empty/unavailable list resolves to Gregorian. A Persian locale later in the list does not override a non-Persian primary locale.

| Saved preference | Primary system language | Effective calendar |
| --- | --- | --- |
| Missing, invalid or system | fa, including fa-IR/fa-AF | Persian |
| Missing, invalid or system | en, fr, ar, unsupported language or unavailable | Gregorian |
| persian | Any | Persian |
| gregorian | Any | Gregorian |

Changing Luma's app language changes translated labels, digits and layout direction; it does not change this system-language decision. An explicit calendar survives app-language changes, system-language changes, recreation and process death. Selecting Follow system removes the override logically by saving `system`.

Use one observable app-wide preference source for Settings, other app surfaces and widget rendering, and as Calendar's initial default. Calendar's view override is separate route state and never writes this source. Serialize writes; perform durable preference I/O off the main thread. Publish the accepted choice after successful persistence. On failure, retain the previous choice and show localized accessible retry feedback. Prevent stale or rapid requests from overwriting a newer successful selection.

The preference is local presentation state. Do not change Luma archive/schema formats to add it. Restoring a Luma data archive does not reinterpret dates or overwrite the destination's calendar choice. Clearing app data returns to the system default.

### DC-02 — Settings

Add a Calendar card to the existing Appearance & language page, after the language card. Update the hub description so calendar selection is discoverable without adding a separate settings route.

Use localized selectable rows: Follow system, Persian (Shamsi), Gregorian. Follow system also shows the effective type. The selected radio row reflects the preference mode, even if Follow system and an explicit choice currently resolve to the same type.

Use `SettingsSectionPage`, `LumaSectionSurface`, existing choice-row semantics, theme roles and `LumaUi` tokens. Add stable tags `calendar_settings`, `calendar_preference_system`, `calendar_preference_persian`, `calendar_preference_gregorian`, `calendar_preference_error`.

### DC-03 — Fast Calendar switch

Place a visible two-option calendar control near the existing Month/Week controls in the Calendar grid card. Show localized Persian (Shamsi) and Gregorian labels, with that page's effective view calendar selected. This fulfills the requested switch button; use two labeled choices rather than an ambiguous unlabeled on/off control.

Selecting the other calendar updates only Calendar's local view state. It never writes `app_calendar`, changes Settings, refreshes widgets or changes another destination's dates/pickers. Re-selecting the displayed type is a no-op. Selecting the current app-wide default clears the local override.

Proposed lifecycle: store a nullable `calendar_view_system` plus the Settings preference/effective-system signature in the Calendar back-stack entry's SavedStateHandle. Preserve it during rotation, process recreation and editor round trips while the same entry exists. A new entry starts from Settings. When the app-wide preference mode or its effective calendar changes, clear the override and display the new default; discard a restored override if its saved default signature no longer matches. Changing app language alone preserves the override while translating labels and updating applicable week alignment.

Do not conflate calendar type with the existing Month/Week view mode. Keep Today, previous/next, filters, agenda, marker legend and Schedule accessible. Stack/wrap controls at narrow widths or large font sizes. Expose selected state, accessible names and at least 48 dp touch targets. Use tags `calendar_system_switch`, `calendar_system_persian`, `calendar_system_gregorian`; retain existing Calendar tags.

### DC-04 — Calendar rendering and navigation

Calendar type affects year/month heading, day numbers, month membership/overflow styling, month length, navigation, spoken dates and date ribbon. Persian uses Farvardin–Esfand, including correct leap Esfand; Gregorian retains existing behavior.

Use calendar year + month to compare month membership, not ISO `date.month`. Month arrows move one month in the active system, preserving its day number where possible and clamping to the destination month's last valid day. Week arrows move exactly seven ISO civil days. Today selects the actual local civil day in the existing environment zone.

Switching calendar preserves the canonical selected `LocalDate`, Month/Week mode, filters, agenda paging, scroll position where feasible, task action/undo/retry state and drafts. The newly displayed month/week contains that same day. Requery markers for the new calendar-aligned ISO range, cancel old work and reject stale responses. Agenda queries still target the identical ISO date; do not reset them solely for a calendar presentation change.

Grid rows and marker queries share one range calculation. Weeks have seven slots; months have whole-week rows and at most 42 slots. At existing supported ISO boundaries, use null/disabled slots outside the valid range and clip repository queries. Never emit an invalid `CalendarRange`.

### DC-05 — App-wide date display and picking

App-owned human-readable date labels use the effective Settings calendar, including Today/Timeline, task labels, capture/editor timestamps, habit history, Review, Focus history, Search, memory library, people/places, integration/backup metadata and human-readable entry-share output. Within Calendar, headings, grid, date ribbon, agenda date badges and accessibility dates use the page's effective view calendar. Keep this formatting policy local to Calendar; external editors, Quick Capture, share destinations and other routes use Settings even when opened from Calendar. Times retain the user's 12/24-hour preference and their existing time zone.

App language controls translation, numeral style and RTL independently of calendar type. Persian UI with Gregorian dates and English/French UI with Persian dates must both work offline.

Provide a shared calendar-aware date-picker content component, hosted in the existing Luma dialog or timestamp dialog. Use it for DayNavigation (Today/Timeline/Habits), task due dates, capture timestamps (including Mood/Journal/Memory consumers), habit start/correction, Search bounds and Weekly Review. Gregorian may delegate to the existing Material picker; Persian needs real Persian month/day selection. Changing the locale of an ISO picker alone is insufficient.

The picker consumes and returns ISO `LocalDate?`. Keep tentative selection separate from the committed draft; Confirm commits once, Cancel/Back commits nothing. Preserve caller-specific date bounds, selectable-date rules, nullable due dates, test tags, scrolling, keyboard behavior and save/validation flows. Translate an ISO allowed interval into valid Persian dates without treating Persian year numbers as ISO years. Retain efficient month/year navigation, Gregorian input-mode behavior and a usable Persian year/month chooser. Raw numeric Persian date entry is outside this feature.

Memory-library visual headings group by active calendar year/month while preserving event order, event IDs, limits and load-older behavior. Weekly Review, habit schedules, Focus metrics and saved report periods retain their current semantic intervals; only labels/picker presentation changes. Do not regenerate stored report snapshots.

Launcher widget task due-date text uses the same preference and existing localized formatting rules. Request existing widget refresh after successful preference changes and system-default changes; do not change widget layouts, action payloads or configuration.

### DC-06 — Canonical date safety and supported bounds

Storage, repositories, SQL predicates, task due dates, saved selections, route arguments, drafts, import/export machine fields and backup timestamps stay ISO `LocalDate`, epoch days or existing instants. There is no database migration and no calendar-type column on tasks or memories.

Convert date-only values using epoch-day arithmetic with a fixed UTC bridge, never device-midnight milliseconds. Convert instants into each screen's existing display zone before extracting a local date. Keep reminder scheduling, habit tracking zones, event timestamps, DST handling and daily boundaries unchanged.

Preserve the current canonical Calendar date range, ISO years 1–9999. Persian conversion must round-trip the corresponding proleptic extended years, including values at/before Persian year 1; never silently reinterpret these as Gregorian or shrink the supported range. Month movement clamps at the ISO bounds. Picker limits remain each caller's ISO limits.

### DC-07 — Design and regressions

Use the current warm-paper/obsidian theme, MaterialTheme color roles, typography/shapes, bundled fonts, `LumaUi` tokens and shared Luma components. Extend a shared control only if multiple consumers need it. Preserve specialized Calendar markers, charts and Focus visuals, navigation, callbacks, drafts, validation, retry/undo, existing tags, keyboard/inset handling and FAB clearance.

Support System/Light/Dark, English/French/Persian, RTL, TalkBack, at least 48 dp targets and font scale 2.0. The required starting references are `ui/theme/`, `LumaUiTokens.kt`, `LumaUiComponents.kt`, `AppPageScaffold.kt`, `docs/luma-redesign/README.md`, `route-coverage.md` and `ui-consistency-checklist.md`. Current code and this specification take precedence over older palette documents.

## Proposed architecture and alternatives

**Recommended:** Keep canonical ISO domain data. Add a platform-free calendar arithmetic contract with the current Gregorian implementation, and an Android ICU Persian adapter in `:app`. Use one observable app-wide preference, separate Calendar route view state and one shared formatting/picker layer with explicitly scoped policy inputs. Android's public Calendar factory accepts an explicit calendar keyword; instantiate with `persian`, check the returned type, use UTC and `EXTENDED_YEAR`, disable lenient invalid-date rollover, and avoid hidden `PersianCalendar` APIs. Use per-operation instances or confined instances because ICU calendars/formatters are mutable.

Gregorian math remains `java.time.LocalDate` (proleptic ISO); do not accidentally introduce the ICU Gregorian 1582 cutover. Persian formatting uses the same conversion authority as Persian arithmetic, with explicit calendar selection independent of display locale.

Alternative: bundle a vetted pure Kotlin/JVM Persian engine. This makes arithmetic host-testable and independent of device ICU versions, but adds a dependency and license/version maintenance. Reconsider only if final QA proves platform ICU cannot meet conversion fixtures consistently on supported devices.

Rejected alternative: make the Calendar switch write Settings. The user explicitly requires page-only switching. A different calendar in an editor is intentional when a Calendar view override is active; editors honor Settings. Share conversion/formatting code while keeping preference state and view state separate.

No conversion code is selected by locale alone. No hand-written approximate leap-year algorithm, network calendar service or SDK upgrade belongs to this plan.

## Single final QA and acceptance

All implementation tasks finish before **DC-Q01**, the only QA task. Until then use source inspection only: no test authoring/execution, Gradle builds/lint, APK installs or emulator/device sessions. DC-Q01 owns automated test authoring, execution, device/manual checks, defect fixes and targeted retests inside that same QA task.

| Scenario | Acceptance evidence required in DC-Q01 |
| --- | --- |
| DQA-01 Defaults | Fresh/missing/invalid preference: fa-IR/fa-AF → Persian; en/fr/ar/empty → Gregorian; primary en + secondary fa stays Gregorian; explicit app language never substitutes for system language. |
| DQA-02 Persistence | Settings selection survives process death and language changes; Follow system reacts on resume; failed/rapid Settings writes retain the latest successful choice. Calendar switches change no preference bytes, other routes, editors, memory grouping or widgets. Verify local override rotation/process restoration/editor return, new-entry reset, selecting the app default clears it, and changed Settings preference/effective default resets it. |
| DQA-03 Conversion | Fixed independent fixtures include 2024-03-20 ↔ 1403-01-01, 2025-03-20 ↔ 1403-12-30 and 2025-03-21 ↔ 1404-01-01; 1404-12-30 is rejected. Exercise 31/30/29-day boundaries, Gregorian February and ISO boundary dates with round trips. |
| DQA-04 Grid/navigation | Correct Persian and Gregorian month range, weekday alignment, overflow styling, month clamping, year rollover, week +/−7 days, max 42 cells and clipped boundary queries. |
| DQA-05 Same-day safety | Switch during marker loading, pagination and task mutations; preserve selected ISO day/agenda/filter, reject late markers, and preserve completion/undo/retry. Compare actual task/memory IDs before/after. |
| DQA-06 Pickers | Each picker host selects the expected ISO date in both calendars; Confirm/Cancel/Back, null due date, year/month navigation, caller bounds, disabled days, invalid dates and draft recreation work. |
| DQA-07 All surfaces | Audit all human-readable date calls, memory month grouping across Nowruz, Focus/Review unchanged periods, entry-share date text and widget refresh; machine payloads/backups remain identical. |
| DQA-08 Time and lifecycle | UTC, Asia/Tehran and America/New_York near local midnight/DST; system calendar/default changes on resume, local-view override restoration/reset, rotation and process recreation; 12/24-hour time unaffected. |
| DQA-09 Visual/accessibility | EN/FR/FA × Persian/Gregorian × Light/Dark plus System theme transitions; Persian RTL, 320 dp and wide windows, font scale 2.0, offline fonts, TalkBack, 48 dp targets, IME/insets/FAB clearance. |
| DQA-10 Regression | Existing Calendar/domain/ViewModel/Room/Compose, Settings, task/habit/capture, Search, Review, memory, language and widget date/action regressions; build/lint and current-source results recorded. |

Run on API 28 and a current API 33+ device/emulator to cover both system-locale paths and ICU versions. Independent fixtures must assert known dates, not only round-trip against the same implementation. If a fixture differs across ICU versions, acceptance is blocked until the adapter or a vetted replacement satisfies the same contract.

QA exit requires all required checks passing on the final source revision/patch. Record actual commands, test counts, environment, screenshots, defects and targeted retests in `docs/validation/2026-11-10-dual-calendar.md` during DC-Q01. Missing devices or unavailable cases remain BLOCKED/NOT RUN; never mark this feature accepted based on source review or historical Calendar results.

## Non-goals

Holidays, Islamic lunar calendars, events, recurrence, calendar sync, new reminder behavior, user-configurable week starts, calendar-aware rescheduling, changing stored report periods, a backup-format change and a new visual design are outside this package.

## Technical references

- [Android ICU Calendar](https://developer.android.com/reference/android/icu/util/Calendar): public factory, calendar keyword and extended-year API; availability predates the app's minSdk.
- [Android LocaleManager](https://developer.android.com/reference/android/app/LocaleManager#getSystemLocales()): system locales independent of app overrides.
- [Material DatePickerState](https://developer.android.com/reference/kotlin/androidx/compose/material3/DatePickerState): selected date/visible month UTC representation and caller constraints. Do not infer Persian arithmetic from localized labels.
