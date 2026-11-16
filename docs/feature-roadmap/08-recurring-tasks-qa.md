# F08 — Recurring tasks QA scenarios

Date: 26 October 2026 · TI-15 family-scope extension: 15 November 2026 · Status: **NOT RUN** · Priority inherited from task: **P2**

[Roadmap](../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../superpowers/specs/2026-10-26-roadmap-08-recurring-tasks.md) · [Plan](../superpowers/plans/2026-10-26-roadmap-08-recurring-tasks.md) · [QA scenarios](08-recurring-tasks-qa.md) · [Shared workflow](WORKFLOW.md)

## Entry gate and scope

This document is **phase 2** of a two-phase task. Begin only after F08-I01–I05 are source complete with a recorded handoff. For family scenarios, first reconcile the canonical task-family source and TI-QA01 outcome; if that baseline is not accepted, record family runtime acceptance as BLOCKED with its exact prerequisite. Test authoring, Gradle/sync/build/lint/schema generation, automated execution, emulator/device and manual checks occur here only. The TI-15 documentation update did not execute these scenarios.

Read [WORKFLOW.md](WORKFLOW.md) for isolation, exact result semantics, required locale/theme/window/API matrix, automated command catalogue and performance protocol. Read the entire specification before deriving test assertions. All cases require task-specific preconditions; do not mark irrelevant branch scenarios PASS—use N/A only with a source-supported scope rationale, and do not turn a deferred investigation into shipped-feature acceptance.

## Fixture

F08: daily series Oct 26–30, Monday/Wednesday series, monthly anchor 31 spanning Jan–Mar 2027, leap-year Feb 2028, completed/skipped/overridden occurrences, two concurrent generators and a six-month closed-app gap. Add a recurring root with two versioned child templates: one dated with an independent importance and signed date offset, and one undated; include completed, overridden, skipped and open child snapshots. Track root nominal-date keys, child-template IDs and per-occurrence child IDs separately from due dates.

Freeze clocks through injection for automation; use dedicated AVDs for manual time/zone/credential changes. Record fixture seed/hash and expected IDs/values independently from production predicates. Reset baseline between independent scenarios. Never substitute real personal records, credentials or health data.

## Coverage obligations

The mapping below identifies the scenarios responsible for each requirement; cases may cover several requirements. During QA add assertion-level mappings and authored test names without dropping any requirement or shared matrix obligation.

| Requirement | Scenario coverage set | Initial result |
| --- | --- | --- |
| F08-R01 — Rules and month end | F08-QA01, F08-QA02 | NOT RUN |
| F08-R02 — History and missed work | F08-QA01, F08-QA03, F08-QA09, F08-QA11 | NOT RUN |
| F08-R03 — Edit scopes | F08-QA04, F08-QA05, F08-QA10, F08-QA11 | NOT RUN |
| F08-R04 — Cross-surface behavior | F08-QA06, F08-QA07, F08-QA10 | NOT RUN |
| F08-R05 — Persistence and compatibility | F08-QA06, F08-QA08, F08-QA12 | NOT RUN |
| F08-R06 — One-level recurring task families | F08-QA09–F08-QA12 | NOT RUN |

## Scenarios

### F08-QA01 — Daily and weekdays

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Generate repeated overlapping ranges and inspect each expected civil date.

**Expected:** Exactly one occurrence per eligible nominal date; stable IDs; no off-weekday or before-start rows.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F08-QA02 — Month end and bounds

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Generate monthly Jan31→Feb→Mar in 2027/2028 and near supported year edges.

**Expected:** Feb clamps while March returns to 31; leap/boundary arithmetic stays valid; invalid rules/ranges reject without partial writes.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F08-QA03 — Completion and missed work

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Complete one occurrence twice, leave others overdue, restart after six months and page older dates.

**Expected:** Completion remains idempotent; missed valid dates become discoverable without unbounded work or alert burst; history is not auto-completed.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F08-QA04 — One occurrence override/skip

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Reschedule one date, skip another and edit series afterwards.

**Expected:** Nominal identity remains stable; no regenerated duplicate; override and skip survive series edits and restart.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F08-QA05 — Future edit and end

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Change weekdays from an effective date, then end series with completed future record present.

**Expected:** Only eligible future unmodified open tasks change/cancel; completed history/overrides retained; scope confirmation is accurate.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F08-QA06 — Concurrency and stale commands

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Generate same window concurrently, edit series during generation and replay old widget/reminder actions.

**Expected:** Unique transactional occurrences; stale actions rejected; no lost completion or phantom task.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F08-QA07 — Travel and surfaces

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Change device zone/DST and compare Today/Calendar/tasks/widgets/reminders.

**Expected:** Civil due values stable; same occurrence IDs/counts; reminder instant follows task04; habits remain absent from Calendar.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F08-QA08 — Migration and archives

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Upgrade populated one-time tasks, restore old archive and round-trip series with overrides/skips.

**Expected:** Existing tasks unchanged; old archives produce no series; new series/history round-trip and commands rotate safely.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F08-QA09 — Family snapshot and reset

**Precondition:** an isolated daily root series has two child templates with distinct IDs, explicit sibling order, one dated offset child with High importance and one undated child with None importance.

**Actions:** Materialize the first root occurrence only; complete that root and both children; materialize the previously absent next occurrence; then generate an overlapping range again.

**Expected:** Each root occurrence has exactly one child per effective template, with a distinct child task UUID and unique `(parentOccurrenceKey, childTemplateId)` key. Children in the newly generated occurrence start open with null completion time and copy the effective title/note/order/importance and independent offset date/time, even though the previous family is complete. No child ID, completion state or timestamp carries across occurrences; replay adds no duplicates.

**Evidence:** exact source/build/fixture identity, independently checked root/child keys and persisted fields, generator command log. Initial result: **NOT RUN**.

### F08-QA10 — Independent fields, date windows and edit scopes

**Precondition:** a recurring family has multiple materialized open occurrences for edit checks, a completed child sibling, and one child snapshot with an explicit due-date override. The effective daily template has a child with signed offset `-2`; the root occurrence for nominal date today + 2 days and its child snapshot are not materialized, and the generation cursor has not passed that date.

**Actions:** Query a one-day due-date window for today; verify the query-driven materialization of the root at today + 2 days and its offset child due today. Repeat the query and verify no duplicates. Separately query the concrete due date of the persisted child override; change one child under `This occurrence`; then change a template child under `This and future`, including title, signed offset, due time, importance and sibling order.

**Expected:** The first query inverse-maps the child offset, creates the previously absent root occurrence and child snapshot atomically, and returns the child with parent context although the root nominal date is outside today's window. Repeating the query creates no duplicate keys or task IDs. The separate override query finds the existing child by its concrete due date. The single-occurrence edit changes only that snapshot. Future content/date/importance edits affect eligible active open unmodified snapshots from the effective nominal date and later generations, not completed or explicitly overridden fields. A materialized family's sibling order changes only when the root and every child are active, open and unmodified. If any family member is completed, skipped, cancelled or overridden, preserve all existing sibling positions without partial renumbering; future materialization and wholly eligible families use the new order. Positions remain unique. Child dates derive from the root nominal date rather than an overridden root due date; root due date/importance edits never cascade to child fields.

**Evidence:** query windows and affected IDs, exact scope, before/after fields and sibling positions, whether the root/child were absent before the query and atomically created afterward, date-window results and transaction result. Initial result: **NOT RUN**.

### F08-QA11 — Family lifecycle, skip/end and completion

**Precondition:** a future family has an ACTIVE open root, an ACTIVE open child, a completed child and an overridden open child; a later unmodified family is also materialized.

**Actions:** Skip an open child; attempt direct-ID and bulk completion against it; confirm parent completion with any remaining ACTIVE open children and verify the skipped child is absent from the affected IDs; skip a whole root occurrence in another family; end the series; then replay overlapping materialization and the same commands.

**Expected:** A skipped child is retained with the same ID, separate SKIPPED lifecycle and no completion timestamp; it disappears from actionable date/search/Focus/widget/reminder projections, is read-only on direct-ID actions, and does not count as completed. It is excluded from bulk completion and no longer blocks parent completion; progress keeps completed/total counts and shows skipped counts separately. Root skip is rejected if the root is completed; otherwise it marks the root SKIPPED and marks remaining ACTIVE open children SKIPPED, including one-off overrides, while preserving completed children and preventing regeneration. Ending stops future families; a family with a root or child override remains actionable as a coherent family, while unmodified future open roots and ACTIVE open children become CANCELLED. Completed and previously inactive rows remain unchanged. Repeated commands stay idempotent and stale commands fail atomically.

**Evidence:** family membership, lifecycle/task completion state and progress before/after; persisted skip/end keys; direct-ID, bulk and parent-completion outcomes; repeated-command results and visible task IDs. Initial result: **NOT RUN**.

### F08-QA12 — Completed-history immutability and archive compatibility

**Precondition:** seed completed root/child occurrences with non-default dates, importance, notes, sibling order and completion timestamps; record full row values and stable IDs. Prepare supported archive fixtures for schema 14 with a non-recurring hierarchy and sibling order/importance, v12 with tasks but no hierarchy, plus a new recurrence archive.

**Actions:** Edit child templates for the future, generate overlapping ranges, skip/end later families, export/restore the recurrence archive, restore schema 14 and v12 fixtures, and run two concurrent generators for the same window.

**Expected:** Future edits, generation, skip and end leave every pre-existing completed root and child field unchanged, including task ID, content, due date/time, importance, task status, lifecycle, completion timestamp, revision, parent link and sibling order. Recurrence archive round-trip preserves template versions, child identities/order, independent fields, overrides, lifecycle and completion history; restore may rotate revisions to invalidate stale commands but does not alter completed content/status/timestamps. Schema 14 restores existing non-recurring families, order and importance without inventing a series. The v12 task-only fixture restores each task as a root without changing its supported legacy fields. Concurrent generation creates no duplicate parent or child keys; malformed family data rejects atomically.

**Evidence:** independently captured before/after row snapshots and hashes, archive fixture/version, concurrent generator outcomes and both legacy restore results. Initial result: **NOT RUN**.

## Automated and manual ownership

Automated command/test-file ownership is in [the plan’s phase 2](../superpowers/plans/2026-10-26-roadmap-08-recurring-tasks.md); no tests are asserted to exist before that phase. Add domain/state cases for pure rules and occurrence keys, real Room cases for family materialization/migration/atomicity/history immutability, and UI/native cases for edit-scope and family confirmation behavior. Fakes cannot establish native permission, biometric, provider, launcher, file-picker or shutdown behavior.

Manual/platform obligations: parent/child recurrence scope dialogs, large missed-range paging, child due-date lookup when root nominal dates fall outside a surface window, inactive-row exclusion and direct-ID feedback, independent child dates/importance across surfaces, whole-family versus child-only skip, parent completion after child skip, end-series recovery, protected-family ordering, completed-history immutability and notification/widget reconciliation.

Apply every relevant shared UI/platform matrix cell, not only the default-English emulator. For benchmarks use the shared 5-warmup/30-measurement protocol and report raw samples/p95; for task-specific failures retain the before-fix assertion and after-fix evidence.

## Execution ledger

Evidence directory during execution: `docs/feature-roadmap/evidence/<actual-run-date>/F08/`. Record source/build/environment identity once per run and link each row to its actual artifacts. Add matrix-cell rows as execution proceeds.

| Case | Result | Actual observation | Evidence | Defect/retest |
| --- | --- | --- | --- | --- |
| F08-QA01 | NOT RUN | — | — | — |
| F08-QA02 | NOT RUN | — | — | — |
| F08-QA03 | NOT RUN | — | — | — |
| F08-QA04 | NOT RUN | — | — | — |
| F08-QA05 | NOT RUN | — | — | — |
| F08-QA06 | NOT RUN | — | — | — |
| F08-QA07 | NOT RUN | — | — | — |
| F08-QA08 | NOT RUN | — | — | — |
| F08-QA09 | NOT RUN | — | — | — |
| F08-QA10 | NOT RUN | — | — | — |
| F08-QA11 | NOT RUN | — | — | — |
| F08-QA12 | NOT RUN | — | — | — |

## Exit gate

- [ ] All F08-R01–R06 assertions and F08-QA01–F08-QA12 scenarios mapped to actual tests/manual observations, including a before/after proof that completed root and child history is unchanged.
- [ ] Required host/build/lint/schema/instrumented gates pass for exact candidate; warnings and skips classified.
- [ ] Required native/manual/matrix/performance obligations complete with reproducible evidence.
- [ ] Defects corrected and affected results rerun; remaining blockers and invalidated old evidence explicit.
- [ ] Acceptance decision states exact completed scope. No unrun or blocked scenario is described as passing. For investigation-only outcome, feature implementation/runtime QA remains deferred explicitly.
