# Calendar & Agenda task backlog

Prepared: 2026-10-25  
Status: CAL-I01–I06 source complete; QA has not started.

[Specification](superpowers/specs/2026-10-25-calendar-agenda-design.md) · [Engineering plan](superpowers/plans/2026-10-25-calendar-agenda.md) · [QA scenarios](CALENDAR_QA.md)

## Scope and working rules

Add a native light/dark Calendar & Agenda screen for dated tasks, with existing scheduled-habit summaries and journal reflection links. Per the user’s navigation decision, Calendar replaces Habits in the main navigation bar: **Today → Timeline → Calendar → Review → More**. The full Habits section moves to **More → Habits**, retaining its existing routes and functionality. Today remains the home screen. Scheduled tasks reuse existing optional due dates/times; they do not introduce events, recurrence, reminders or task tags.

The user requires two ordered phases:

1. **Implementation:** finish all CAL-I tasks using source work and static review only. No Gradle commands, test authoring/execution, emulator use or app installation. Source complete means ready for QA, not tested.
2. **QA:** after the complete implementation handoff, author and run tests, compile/lint, exercise emulator/device flows and compare both visual references. Defect corrections and retesting remain tracked in QA; do not claim acceptance until evidence passes.

IDs are local to Calendar and do not renumber T01–T38, TD or optional-feature work. Existing task due-time support is the source baseline; unrelated dirty files and existing release blockers are outside this package.

## Phase 1 — Implementation (all rows before QA)

| ID | Priority | Status | Deliverable | Depends on | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| CAL-I01 | P0 | Source complete; QA pending | Pure civil-date/period rules, marker/read models and CalendarRepository contract | Existing task/habit models | Source and interface review against CAL-02–04 |
| CAL-I02 | P0 | Source complete; QA pending | Bounded Room task/habit/journal projections, live counts and provider wiring | I01 | Room query/repository/provider source review; Room version 6/schema/archive unchanged; no build run |
| CAL-I03 | P0 | Source complete; QA pending | Saved date/mode/filter, paging, per-source state and stale-response/restore protection | I01, I02 contracts | State transition/source review; no tests run |
| CAL-I04 | P0 | Source complete; QA pending | Month/week UI, agenda/filter/counts, completion recovery, habits/reflection and light/dark/localized resources | I03, existing mutation/feedback code | Reference-to-source and callback review |
| CAL-I05 | P0 | Source complete; QA pending | Calendar primary tab replacing Habits, More → Habits with Back/legacy-route compatibility, existing editor integration and explicit dated Quick Capture | I02, I04 | Call-site/default compatibility review |
| CAL-I06 | P0 | Source complete; QA pending | Source coverage review and complete QA handoff with all validation marked NOT RUN | I01–I05 source complete | Static source review, resource parity, revision/patch recorded, implementation gate signed |

## Phase 2 — QA (starts only after CAL-I06)

| ID | Priority | Status | Deliverable | Depends on | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| CAL-Q01 | P0 | Not started | Author domain/ViewModel tests; run host tests, build, test APK assembly and lint | All I tasks source complete | Commands, environment, test counts and logs |
| CAL-Q02 | P0 | Not started | Author/run Room and Compose scenarios; execute existing regression suites | Q01 passes | Current-build instrumentation results; no unexplained skips |
| CAL-Q03 | P0 | Not started | Execute manual light/dark, EN/FR/FA, RTL, accessibility, lifecycle/window matrix; fix/retest defects and record acceptance | Q01/Q02 and available QA build | CQA-01–32 evidence, screenshots, defects and final gate |

## Handoff and acceptance

Implementation exit: I01–I06 source complete; concrete file list and build/behavior risks recorded; no Gradle or emulator use. QA entry: that handoff is complete and uses an isolated test environment. QA exit: all required scenarios/matrix checks pass on the current source revision/patch; unresolved or unavailable checks remain BLOCKED/NOT RUN and acceptance stays pending.

Create the actual-run record at `docs/validation/2026-10-25-calendar-agenda.md` during handoff/QA. The date in that path identifies this planning package, not a claim QA ran on that date. Record actual execution timestamps separately. No test result is claimed by these documents.
