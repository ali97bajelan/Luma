# Dual calendar task backlog

Prepared: 2026-11-10

Status: Implementation in progress; DC-I01 source complete (QA pending), DC-I02–I06 and DC-Q01 not started.

[Specification](superpowers/specs/2026-11-10-dual-calendar-design.md) · [Implementation plan](superpowers/plans/2026-11-10-dual-calendar.md)

## Outcome

Support Persian Solar Hijri (Shamsi/Jalali) and Gregorian calendars. Settings chooses the app-wide preference; a visible two-option control in Calendar switches only that page's view. Without an explicit choice, the primary device language `fa` selects Persian and every other language selects Gregorian.

Confirmed: the Calendar page switch is local to that page and never changes the app-wide preference. Proposed lifecycle: initialize from Settings, retain the view override with the Calendar back-stack entry, and reset it for a new entry or a changed Settings default. Editors and widgets continue using Settings. Other proposed decisions: dates/pickers follow Settings app-wide; Follow system remains available; Persian grids start Saturday. The app language controls translated labels, digits and RTL independently.

Canonical ISO dates, saved data, route arguments, task/reminder semantics and report periods remain unchanged. Use the existing Luma warm-paper/obsidian design system and the separate launcher widget design contract.

## Execution order

1. Complete **all DC-I01–I06 implementation tasks**, using source inspection only.
2. Run **DC-Q01 once, after all implementation**. This one QA task contains test authoring/execution, compilation/lint, device/manual validation, defect fixes and targeted retests.

Do not insert QA tasks, test runs, Gradle builds, installations or emulator sessions between implementation tasks. Source complete means ready for QA, not tested or accepted.

## Implementation tasks

| ID | Priority | Status | Deliverable | Depends on | Source completion evidence |
| --- | --- | --- | --- | --- | --- |
| DC-I01 | P0 | Source complete (QA pending) | Calendar-system/math contracts, Gregorian implementation, Android ICU Persian adapter and shared ISO-backed grid/range/month movement | Existing canonical domain rules | Boundary/conversion/range source review; no database/schema change |
| DC-I02 | P0 | Not started | Durable observable preference, true system-language resolver, lifecycle policy, Settings card and EN/FR/FA copy | I01 types | One preference owner; save/error/system-default wiring and resource parity reviewed |
| DC-I03 | P0 | Not started | Persian/Gregorian Calendar rendering, page-local fast switch and stale-marker protection | I01–I02 | Same selected ISO day, agenda/filter/actions and navigation retained |
| DC-I04 | P0 | Not started | Shared calendar-aware picker and all existing picker-host integrations | I01–I02 | ISO input/output, caller bounds, Confirm/Cancel/draft/tag contracts reviewed |
| DC-I05 | P0 | Not started | App-wide human date display, active-calendar memory grouping and widget date refresh | I01–I04 | Complete call-site inventory; machine dates, report semantics and widget actions unchanged |
| DC-I06 | P0 | Not started | Source coverage review and explicit QA handoff | I01–I05 complete | Revision/patch, files, risks and DQA-01–10 recorded as NOT RUN |

## One final QA task

| ID | Priority | Status | Deliverable | Depends on | Exit evidence |
| --- | --- | --- | --- | --- | --- |
| DC-Q01 | P0 | Not started | Author/run meaningful automated tests; host/build/lint checks; API 28 and API 33+ device checks; EN/FR/FA, both calendars, theme/RTL/large-text/TalkBack/lifecycle/time-zone coverage; fix/retest defects and record acceptance | Every DC-I task source complete, I06 handoff recorded | All DQA-01–10 in the specification pass on final source, exact commands/counts/screenshots and final decision recorded |

DC-Q01 is the sole QA task. Its internal checks and defect retests do not become separate QA phases. Required cases include defaults independent of app language, persistent Settings selection, Calendar-view isolation, leap Esfand/Nowruz, month clamping, extreme ISO years, rapid switching/stale requests, picker cancellation/bounds, memory regrouping, widgets, time-zone safety and unchanged data semantics.

## Handoff and acceptance

Create the implementation/QA record at `docs/validation/2026-11-10-dual-calendar.md` during DC-I06; the filename identifies the planning package, not an execution date. Record actual dates separately.

I06 marks source completion only. DC-Q01 marks acceptance only after every required check passes on the current source patch. Unavailable devices, unresolved failures or missing matrix checks remain BLOCKED/NOT RUN and acceptance stays pending. Existing Calendar/redesign results do not prove this feature.

DC-I01 source changes are recorded above. Tests, Gradle builds/lint, APK installs and device/emulator checks remain unrun; DC-Q01 remains pending.
