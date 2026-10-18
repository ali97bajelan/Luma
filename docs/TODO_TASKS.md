# Tasks feature planning handoff

Prepared: 19 October 2026. Status: TD01 complete; TD02 in progress (schema export and verification pending); TD03–TD07 not started.

The user requested investigation by a senior product manager and a senior UI/UX
designer, with direct discussion to produce specifications, plans and tasks. They
subsequently selected **a dedicated Tasks list plus a small Today section**.
Detailed feature and engineering choices remain proposed for review.

## Read first

1. [Product specification](superpowers/specs/2026-10-19-todo-product-spec.md): scope alternatives, recommended MVP, lifecycle/date/data rules and `TODO-AC-01`–`13`.
2. [UI/UX specification](superpowers/specs/2026-10-19-todo-ux-spec.md): navigation, textual wireframes, list/editor interaction, feedback, RTL, accessibility and `UX-TODO-01`–`15`.
3. [Implementation plan](superpowers/plans/2026-10-19-todo-list.md): concrete files/contracts, ordered checkbox steps and verification commands.

The specs own behavior; the plan owns engineering interfaces and verification.
This file owns the proposed feature task status. `TD` identifiers are local to this
feature and do not replace or renumber existing T01–T29 or UX work.

## Recommended MVP

- One-time tasks with a required title, optional notes and optional civil due date.
- Open/Completed lists, deterministic date groups, 50-row initial loading and Load more.
- Complete, reopen, revision-safe completion Undo, editing and confirmed permanent deletion.
- More → Tasks, the shared editor through Quick Capture, and at most three due/overdue tasks on Today.
- Today tasks appear only when browsing the actual current device date. Task in existing Quick Capture starts with No date; Today Add visibly starts due today. Existing Quick Capture locations remain Timeline/Review.
- One standalone Room table with schema-4 migration, archive compatibility, restore preview counts and reset coverage.
- Offline EN/FA/FR, Persian RTL, accessible controls and retained drafts/errors.

Projects, subtasks, priorities, recurrence, reminders, attachments, cloud sync,
search indexing, Timeline records and Review task metrics are deferred. Tasks
and counted habits remain distinct.

## Ordered delivery tasks

Priorities express proposed release scope, not implementation evidence. TD01's
focused and full domain suites pass. TD02 is in progress; TD03–TD07 remain **Not
started**. Every slice includes its owning tests and all three language catalogs where
UI is introduced.

| ID | Status | Priority | Deliverable | Depends on | Acceptance coverage | Done when |
| --- | --- | --- | --- | --- | --- | --- |
| TD01 | Complete | P0 | Pure Kotlin task models, validation, civil-date buckets and repository contracts | Existing baseline | TODO-AC-01–04; UX-TODO-02/06/07 | Unicode/date/ordering tests and full domain suite pass |
| TD02 | In progress (schema export and verification pending) | P0 | Room table, schema 4, transactional CRUD, revisions and bounded observation | TD01 | TODO-AC-03/04/07–09 | Persistence/retry/conflict tests and populated migrations 1–3→4 pass; schema exported |
| TD03 | Not started | P0 | Backup/restore, preview counts and reset contract | TD02 | TODO-AC-09/10; UX-TODO-15 | New archive round trip, old replacement, malformed/cancelled restore and existing backup regressions pass |
| TD04 | Not started | P0 | Shared create/edit screen with reliable drafts/status/delete | TD02/03 | TODO-AC-01–03/07/08/12; UX-TODO-06–11/14 | JVM draft/conflict tests and editor interaction checks pass in localized UI |
| TD05 | Not started | P0 | Tasks lists, More access, Task in existing Quick Capture and completion Undo | TD04 | TODO-AC-04/05/07/08/12; UX-TODO-01–03/05/11/12/15 | >50-row live list, retry/Undo and navigation/capture regressions pass |
| TD06 | Not started | P0 | Compact Today card and date/zone lifecycle | TD05 | TODO-AC-02/05/06/07/13; UX-TODO-04/05/12 | Cap/count/eligibility, hidden historical card, midnight/travel and independent failure checks pass |
| TD07 | Not started | P0 | Integrated language/accessibility/device acceptance and documentation | TD01–06 | All product/UX criteria; especially TODO-AC-11/13, UX-TODO-13/14/15 | Required automated/native checks have evidence; unresolved exceptions are explicit |

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

TD02 persistence code, migration and test sources are in progress. Backup archives
remain at schema 3 until TD03 adds task export and replacement restore. Per request, no
Gradle or emulator tests were run. A non-test compile was attempted to export schema 4,
but could not start because this environment has no Java runtime (`JAVA_HOME` is unset
and `java` is unavailable). Keep TD02 in progress until Room exports schema 4 and the
owning checks have evidence.

No app build, emulator test, usability session, performance benchmark or native
TalkBack check has run as part of feature implementation.

Proceed to TD02 in the dependency order. Create
`docs/TODO_ACCEPTANCE_2026-10-19.md` at TD07 (or earlier to record actual evidence),
using PASS/FAIL/BLOCKED/NOT RUN plus command, device/API/package, locale, width,
font scale and evidence. Use disposable installs for migration/restore/reset;
preserve the user's working tree and actual saved Luma data. Update this index
only after its owning checks pass. Do not infer native acceptance from existing
Habits or app validation reports.
