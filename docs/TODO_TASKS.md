# Tasks feature planning handoff

Prepared: 19 October 2026. Status: TD01–TD06 complete; TD07 in progress (focused app/data acceptance, builds, native export/restore/reset, CRUD isolation and 10,000-task performance pass; the full app suite has 14 failures outside Todo, the full data suite has two Timeline failures, and native TalkBack speech/focus traversal remains unverified). The 23 October due-time extension is implemented in source; its Gradle test and emulator checks were not run at the user's request.

The user requested investigation by a senior product manager and a senior UI/UX
designer, with direct discussion to produce specifications, plans and tasks. They
subsequently selected **a dedicated Tasks list plus a small Today section**.
Detailed feature and engineering choices remain proposed for review.

## Read first

1. [Product specification](superpowers/specs/2026-10-19-todo-product-spec.md): scope alternatives, recommended MVP, lifecycle/date/data rules and `TODO-AC-01`–`13`.
2. [UI/UX specification](superpowers/specs/2026-10-19-todo-ux-spec.md): navigation, textual wireframes, list/editor interaction, feedback, RTL, accessibility and `UX-TODO-01`–`15`.
3. [Implementation plan](superpowers/plans/2026-10-19-todo-list.md): concrete files/contracts, ordered checkbox steps and verification commands.
4. [Due-time specification](superpowers/specs/2026-10-23-task-due-time-design.md) and [implementation plan](superpowers/plans/2026-10-23-task-due-time.md): optional hour/minute time, persistence, picker and archive changes.

The specs own behavior; the plan owns engineering interfaces and verification.
This file owns the proposed feature task status. `TD` identifiers are local to this
feature and do not replace or renumber existing T01–T29 or UX work.

## Recommended MVP

- One-time tasks with a required title, optional notes, optional civil due date and optional minute-precision due time (which requires a date).
- Open/Completed lists, deterministic date groups, 50-row initial loading and Load more.
- Complete, reopen, revision-safe completion Undo, editing and confirmed permanent deletion.
- More → Tasks, the shared editor through Quick Capture, and at most three due/overdue tasks on Today.
- Today tasks appear only when browsing the actual current device date. Task in existing Quick Capture starts with No date; Today Add visibly starts due today. Existing Quick Capture locations remain Timeline/Review.
- One standalone Room table with Room 6 migration, archive schema-5 compatibility, restore preview counts and reset coverage.
- Offline EN/FA/FR, Persian RTL, accessible controls and retained drafts/errors.

Projects, subtasks, priorities, recurrence, reminders, attachments, cloud sync,
search indexing, Timeline records and Review task metrics are deferred. Tasks
and counted habits remain distinct.

## Ordered delivery tasks

Priorities express proposed release scope, not implementation evidence. TD01's
focused and full domain suites pass. TD02 and TD03 are complete with schema,
persistence, migration, backup and restore evidence recorded below. TD04–TD06 are **Complete** with their owning JVM, UI and regression checks recorded below. TD07 is **In progress** (focused and manual results are recorded; integrated release acceptance remains incomplete). Every slice includes its owning tests and all three language catalogs where
UI is introduced.

| ID | Status | Priority | Deliverable | Depends on | Acceptance coverage | Done when |
| --- | --- | --- | --- | --- | --- | --- |
| TD01 | Complete | P0 | Pure Kotlin task models, validation, civil-date buckets and repository contracts | Existing baseline | TODO-AC-01–04; UX-TODO-02/06/07 | Unicode/date/ordering tests and full domain suite pass |
| TD02 | Complete | P0 | Room table, schema 4, transactional CRUD, revisions and bounded observation | TD01 | TODO-AC-03/04/07–09 | Persistence/retry/conflict tests and populated migrations 1–3→4 pass; schema exported |
| TD03 | Complete | P0 | Backup/restore, preview counts and reset contract | TD02 | TODO-AC-09/10; UX-TODO-15 | New archive round trip, old replacement, malformed/cancelled restore and existing backup regressions pass |
| TD04 | Complete | P0 | Shared create/edit screen with reliable drafts/status/delete | TD02/03 | TODO-AC-01–03/07/08/12; UX-TODO-06–11/14 | JVM draft/conflict tests and editor interaction checks pass; shared editor status/Undo flow passes on device |
| TD05 | Complete | P0 | Tasks lists, More access, Task in existing Quick Capture and completion Undo | TD04 | TODO-AC-04/05/07/08/12; UX-TODO-01–03/05/11/12/15 | >50-row live list, retry/Undo and navigation/capture regressions pass |
| TD06 | Complete | P0 | Compact Today card and date/zone lifecycle | TD05 | TODO-AC-02/05/06/07/13; UX-TODO-04/05/12 | Cap/count/eligibility, hidden historical card, midnight/travel and independent failure checks pass |
| TD07 | In progress (focused checks pass; aggregate exceptions remain) | P0 | Integrated language/accessibility/device acceptance and documentation | TD01–06 | All product/UX criteria; especially TODO-AC-11/13, UX-TODO-13/14/15 | Required automated/native checks have evidence; unresolved exceptions are explicitly accepted |

Dependency order: **TD01 → TD02 → TD03 → TD04 → TD05 → TD06 → TD07**.
Existing habits reminders/release work stays independently open; this proposal
does not depend on implementing reminders or declare that work complete.

## Product-manager and designer discussion

Two separate senior-role agents investigated source and sent messages directly to
each other. Their cross-review resolved these choices:

| Question | Reconciled proposal | Reason |
| --- | --- | --- |
| New primary tab or existing shell? | Keep five tabs; Tasks under More, with Today/existing Quick Capture discovery | Preserve current navigation and provide useful entry points |
| Show tasks on historical/future Today dates? | Hide the card on all non-current dates | Mutable task status is not a historical daily snapshot |
| Planned date or due date? | Optional Due date, independent of occurrence time and habit zone | Makes overdue behavior understandable without inventing event timestamps |
| Global capture inherits selected record date? | No; Task starts undated; only contextual Today Add prefills today | Avoid accidental deadlines when browsing old records |
| Status action with dirty editor fields? | Require field Save first; completed edits preserve status | Prevent silent saving or discarding of text |
| Undo on every status change? | Completion Undo reopens the exact committed revision; no reopen/delete Undo | Keep recovery useful without restoring invented completion timestamps |
| Restore stale commands? | Restore saved task data but rotate revision tokens; no automatic draft replay | Prevent old edit/status commands from overwriting replacement data |

The product spec records alternative scopes and which requirements are user
confirmed. The interaction spec records screen states and the written peer review.

## Validation and next execution boundary

TD01 verification completed with these results:

- Focused: `bash ./gradlew -PdomainOnly=true :core:domain:test --tests 'com.luma.lifeos.domain.todo.*'` — PASS.
- Full domain suite: `bash ./gradlew -PdomainOnly=true :core:domain:test` — PASS, 126 tests, 0 failures, 0 errors, 0 skipped.

TD02 and TD03 are complete. Room's exported [schema 4](../core/data/schemas/com.luma.lifeos.data.db.LumaDatabase/4.json)
matches the task entity and indexes. The data JVM suite passed 24/24, and both data
and app Android test APKs assembled. On disposable `emulator-5562` (API 36), focused
instrumentation passed 29/29 tests across task persistence, populated migrations 1–3,
schema-4 backup round-trip, schema-1–3 replacement, malformed/cancelled restore,
revision rotation and existing habit/backup regressions. App checks passed 7/7 focused
JVM tests for restore callbacks and stale editor invalidation, plus 3/3 backup settings
Compose tests for task counts and the legacy zero-task replacement warning. Full
commands and environment are recorded in [`TODO_ACCEPTANCE_2026-10-19.md`](TODO_ACCEPTANCE_2026-10-19.md).

The task table and protected drafts are covered by Android's full app-data reset;
there is no separate reset mechanism. Destructive OS reset itself remains outside
the TD03 verification and is still listed as pending in integrated acceptance.

TD04 verification now passes: the full app JVM suite includes the editor draft/conflict/retry tests,
and six editor Compose flows cover title-only save, optional date clearing, picker cancellation and
same-date confirmation, dirty status guarding, delete confirmation/cancel, and dirty close/discard.
The navigation instrumentation also completes a task in the shared editor and consumes revision-safe
Undo on return. EN/FA/FR editor resources are present; broader localized and accessibility acceptance
remains tracked under TD07.

TD05 verification now passes: app JVM coverage exercises 50→100 observed prefixes, live insertion,
deletion and reordering, retry and row-action behavior, revision-checked Undo, and retained list mode
and prefix. Device flows cover More → Tasks, Quick Capture's Task action with its occurrence-date
toggle selected while the task remains undated, origin-preserving return, and editor completion/Undo.
Existing app navigation, habits navigation and Today/day-navigation regressions also pass.

TD06 verification now passes: state tests cover eligibility, three-row preview/full count, completion
backfill, live updates, retry/reconciliation, midnight and timezone regrouping without changing the
stored due date. Device flows verify current-day visibility, hiding on a historical date, restoration
on Today, and independent task errors that leave other Today content visible.

Follow-up results: `:app:testDebugUnitTest` passes 244/244 tests; `:app:lintDebug` and
`:app:assembleDebugAndroidTest` pass. The focused TD04–TD06 device flows pass 11/11 on disposable
`emulator-5562` (API 36); adjacent navigation/Today regressions pass 22/22, and task accessibility/
language regression cases pass 4/4. Full commands and remaining aggregate acceptance gaps are in
[`TODO_ACCEPTANCE_2026-10-19.md`](TODO_ACCEPTANCE_2026-10-19.md).

At the TD06 handoff, no app build, emulator test, usability session, performance
benchmark or native TalkBack check had run for Tasks. TD07 added focused
accessibility/language tests and recorded native results in
[`TODO_ACCEPTANCE_2026-10-19.md`](TODO_ACCEPTANCE_2026-10-19.md). The full app
suite now reports 14 failures outside Todo, and the full data suite reports two
Timeline ordering/page failures. Native TalkBack showed an initial focus ring and
the active-service tests passed, but spoken output and focus traversal remain
unverified. Native SAF export/restore, app-data reset, CRUD isolation and the
10,000-task benchmark passed. TD07 stays in progress until the suite failures and
TalkBack gap are fixed or explicitly accepted. Only disposable `emulator-5562`
was used; the user's existing emulator and saved Luma data were not touched.

## Due-time extension (23 October 2026)

Status: implementation and regression-test source are in the working tree. Room
schema generation, production compilation, debug APK assembly, Android test-source
compilation and lint passed. Gradle tests and emulator/device acceptance were not run
for this extension at the user's request; runtime acceptance remains pending. Some
planned localization/display assertions remain unauthored. See the [validation record](validation/2026-10-23-task-due-time.md).

Tasks can keep a date without a time or a date with a local hour and minute. The
time is stored as minutes since midnight, so midnight is distinct from no time.
Changing the device time zone keeps the civil date and minute unchanged. Sorting
places timed tasks before date-only tasks for the same date; Today and Overdue
remain calendar-day based. Due times do not schedule reminders or alarms.

Room schema 6 migrates old tasks with null due time. Portable backup schema 5 adds
`due_time_minutes`; schema 4 retains its exact old task header and imports with null
time. The editor stores confirmed time in saved drafts and pending writes. Its
Material time input starts at 09:00 when no time is selected and only updates the
draft on confirmation.

## TD07 follow-up checks

The final full APK runs have **16 unresolved failures**: 14 app UI cases and two
Timeline persistence ordering/page cases. The acceptance record lists each test,
its observed assertion/timeout and the next diagnostic check. No root cause has
been established and no failure is accepted as an exception.

- Reproduce the app failures by class and the Timeline failures individually;
  capture semantics/state and compare isolated runs with full-suite runs.
- Verify the underlying product/test contract before changing code or assertions,
  then rerun affected classes and both full APK suites.
- Decide whether Gradle's connected-test path must be restored or the direct ADB
  APK workflow is the documented supported path; the UTP host-output artifact is
  currently unavailable in the local cache.
- Complete native TalkBack spoken feedback and focus traversal, plus the
  remaining PARTIAL/NOT RUN acceptance checks listed in the acceptance record.

TD07 remains **In progress** until these checks are resolved or explicitly
accepted and the remaining acceptance evidence is recorded.
