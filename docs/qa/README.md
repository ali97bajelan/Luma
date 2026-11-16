# Luma QA runbook

Start with this index, seed the named fixture, then follow each case step by step. Every case starts NOT RUN; no QA execution was performed while preparing these documents.

## Run order

1. Prepare a disposable productionDebug QA installation and record device/build details.
2. Read [fixtures](fixtures.md). Build the exact fixture named by each case. Reset it before each scenario unless that case explicitly chains mutations.
3. Run the [P0 smoke suite](#p0-smoke-suite).
4. Follow [coverage-matrix.md](coverage-matrix.md) through all detailed scenario files.
5. Run the bounded [cross-feature journeys](cross-feature-journeys.md).
6. Run the risk-based [UI/accessibility matrix](scenarios/ui-accessibility.md) and [lightweight performance checks](performance.md).
7. Record status/evidence in the scenario and file defects with [bug-report-template.md](bug-report-template.md).

## Scope and constraints

Covers current source routes and behavior: Today, Timeline, Calendar, Review, Tasks, Habits, Focus, Mood, Memory, legacy Journal records, People, Places, Search, sharing, settings, preferences, backup, privacy reset, Usage/Health, widgets, app lock/recovery, accessibility, cross-feature consistency, and lightweight responsiveness.

Limit every entity type to **50 records maximum**. No load, stress or endurance testing. A 50-record pack cannot test Calendar's second page or Timeline's 150-row initial window; those limits are recorded as gaps. Use only disposable QA backups, local photo fixtures and provider records; never use personal data or a personal backup.

## Baseline environment

- Build: productionDebug, application ID com.luma.lifeos.qa; minSdk 28, targetSdk 35, compileSdk 35, JDK 17.
- Disposable emulator/device with no personal Luma data.
- Reference date D: Monday 2026-11-16, 12:00 local Asia/Tehran. If unable to set emulator time, record actual date and shift all D-relative fixtures consistently.
- Default locale English, Light appearance, Gregorian calendar, Asia/Tehran, app lock off, integrations off, no open Focus session.
- Record app commit/build, device/API, local time/time zone, locale/region, theme, calendar, font scale, width dp, navigation mode, permissions and provider availability.
- UI acceptance uses the current warm-paper/obsidian Luma system. Widgets follow their separate visual contract.

## Running existing automated cases

Commands are specified but not run. Run only exact methods cited by case files. This project has a production flavor.

- App unit: ./gradlew :app:testProductionDebugUnitTest --tests 'fully.qualified.TestClass.exactMethodName'
- App device: ./gradlew :app:connectedProductionDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class='fully.qualified.TestClass#exactMethodName'
- Domain unit: ./gradlew :core:domain:test --tests 'fully.qualified.TestClass.exactMethodName'
- Data device: ./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class='fully.qualified.TestClass#exactMethodName'

Device tests need a disposable connected device. Check each selected test's fixture size first: do not run a class that seeds more than 50 records. An instrumentation class includes all source files at compile time even when a single method filter is selected. System scenarios record a stale Lock instrumentation API and an unverified test APK compile prerequisite; resolve that prerequisite before claiming instrumentation runnable. Other method-level commands remain available as cited.

## Status and evidence

Allowed statuses: NOT RUN, PASS, FAIL, BLOCKED, NOT APPLICABLE. Change status only after executing the case or documenting why it does not apply. PASS requires every assertion to match. FAIL requires actual observed behavior and evidence. BLOCKED names the missing prerequisite. Never report a proposed performance budget as a measurement.

For each execution record tester/date, case ID, build/commit, device/API, fixture IDs and counts, environment, status, actual result, screenshots/video/logs, issue link and cleanup. Reopen records or use a repository snapshot to prove persistence and identity; screenshots alone are insufficient.

## P0 smoke suite

Reset fixture before each listed case. This runbook contains 237 functional/configuration cases: 120 P0, 114 P1 and 3 P2. The index also defines 11 end-to-end journeys and 8 lightweight performance scenarios; all remain NOT RUN.

| ID | Check |
|---|---|
| NAV-001 | Fresh launch and primary navigation |
| NAV-005 | Quick Capture routes and cancel |
| TOD-002 | Today entries/counts/order |
| TL-001 | Timeline mixed feed/counts |
| CAL-001 | Exact Calendar agenda |
| CAL-004 | Complete/reopen task propagation |
| REV-001 | Partial-week calculation |
| MOOD-001 | Minimum Mood capture/repeated Save |
| TASK-001 | Task creation |
| MEM-001 | Direct Memory creation |
| SYS-001 | Settings return context |
| BAK-001 | Export disposable fixture, if prerequisites are available |
| LOCK-001 | Lock enrollment, only on disposable installation/device |

## Document index

- [Coverage matrix](coverage-matrix.md)
- [Fixtures](fixtures.md)
- [Navigation, Today, Timeline](scenarios/navigation-today-timeline.md)
- [Calendar and Review](scenarios/calendar-review.md)
- [Tasks, Habits, Focus](scenarios/productivity.md)
- [Capture, catalogs, details, Search and sharing](scenarios/capture-and-catalog.md)
- [Settings, privacy, integrations, widgets and lock](scenarios/settings-integrations-widgets.md)
- [UI and accessibility](scenarios/ui-accessibility.md)
- [Cross-feature journeys](cross-feature-journeys.md)
- [Lightweight performance](performance.md)
- [Bug report template](bug-report-template.md)
