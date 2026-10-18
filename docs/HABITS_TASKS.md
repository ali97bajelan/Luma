# Habits implementation handoff

Prepared: 17 October 2026. Status: T22–T25 complete; T26–T29 pending. The user
approved the richer counted-habits/navigation proposal and requested the
specification, plans and tasks. This handoff records the approved scope and the
current implementation evidence.

## Read first

1. [Approved product extension](personal-life-os-project-definition.txt), section 14.
2. [Feature specification](superpowers/specs/2026-10-17-habits-and-navigation-design.md).
3. [Core plan](superpowers/plans/2026-10-17-habits-core.md), T22–T25.
4. [Navigation plan](superpowers/plans/2026-10-17-habits-navigation.md), T26.
5. [Reminder and acceptance plan](superpowers/plans/2026-10-17-habit-reminders.md), T27–T29.

The spec owns product semantics. Plans own file paths, interfaces, task steps and
commands. [TASKS.md](../TASKS.md) owns overall task status. Update this index and
the master tracker together using actual execution evidence.

## Ordered tasks

| ID | Deliverable | Dependencies | Status | Start condition |
| --- | --- | --- | --- | --- |
| T22 | Pure counted-habit/domain and historical date rules | Existing baseline | Complete | Focused and full domain-only checks pass |
| T23 | Room schema 3, transactional repository, backup compatibility | T22 | Complete | JVM/build checks and 13 Room persistence/migration/backup instrumentation tests pass |
| T24 | Habit creation/logging/history, Today access | T23 | Complete | 200 app JVM tests and 21 isolated device navigation/habit/language tests pass; standard APKs build |
| T25 | Each completion in mixed Timeline and weekly Review | T24 | Complete (uncommitted) | 115 domain, 24 data and 202 app JVM tests pass; 4 Room and 25 UI instrumentation tests pass; standard APKs build |
| T26 | Today · Timeline · Habits · Review · More | T25 | Pending | Functional Habits route; no placeholder tab |
| T27 | Pure reminder planner and persistent configuration | T26 (execution order); T22/T23 (technical) | Pending | Shared date/count contracts stable |
| T28 | Android reminders, permission/status UI, notification actions | T27 | Pending | Planner and transactional action handling verified |
| T29 | Full migration, restore, device and accessibility acceptance | T22–T28 | Pending | All implementation slices available |

Each task has checkbox steps in its owning plan. Each task starts with meaningful
failing behavior tests, implements the smallest owning slice, runs focused checks
and records its evidence before being marked complete. Preserve an independently
runnable app after each slice. T24's Today link makes Habits usable before T26
promotes it into primary navigation.

## Product decisions carried into implementation

- User-set integer target 1–999, with unit label; each accepted + Log adds one.
- Actual count may exceed target; reminder cadence never determines the target.
- Repeated intentional activations have different action UUIDs; retries reuse one.
- Each log has its own Timeline item, correction and deletion; occurrence can be
  unknown, while recorded-at is always retained.
- Partial skip preserves logs/target and suppresses reminders until explicitly resumed.
- Started-habit target/weekday/status changes become effective tomorrow; future-start configuration follows the exception in spec section 5. Reminder off/edit is immediate.
- Saved Habits tracking zone owns civil dates and remains fixed in this release.
- Normal optional notifications, default off; specific times or same-day intervals.
- No automatic completion from notifications, journals, steps or app usage.
- More reuses People & Places and Settings routes; Quick Capture stays available.
- Seven additive schema-3 tables; schema-1/2 restore supported; restored reminders off.
- Existing entry/journal/memory counts remain unchanged; habits have separate counts.

## Verification and evidence rules

Run the owning plan's focused tests first; use T29 for the full suite and actual
platform acceptance. Documentation-only preparation has not run app builds or
device tests. Native TalkBack, notifications/permissions, Doze/reboot, migration,
backup replacement and reset checks need disposable installs and actual evidence.
Do not overwrite the existing user's Luma installation or data.

Create `docs/HABITS_ACCEPTANCE_2026-10-17.md` when implementation validation starts.
For each case record PASS/FAIL/BLOCKED/NOT RUN, device/API/package, locale, width,
font scale and evidence. Missing images/devices are recorded, not treated as pass.
The original release/performance work in [REMAINING_TASKS.md](../REMAINING_TASKS.md)
stays open independently of this extension.

## First execution slice

**T22 and T23 are complete.** T22 adds pure models/contracts and deterministic
count/date/history rules. T23 adds schema 3, transactional persistence and archive
compatibility; its JVM/build checks and 13 Room instrumentation tests pass. See
[the acceptance log](HABITS_ACCEPTANCE_2026-10-17.md). T24 adds functional
capture, per-log history and Today access; its 200 app JVM tests and 21 device
tests pass. T25 now adds every completion to the mixed Timeline and a separate
weekly Review section; its verification is recorded in the
[acceptance log](HABITS_ACCEPTANCE_2026-10-17.md). T26 is next.
Recommended execution is native task-by-task with a final independent review;
the two existing senior roles remain available for focused product/UX review.
