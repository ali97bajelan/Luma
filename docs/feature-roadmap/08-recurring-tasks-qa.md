# F08 — Recurring tasks QA scenarios

Date: 26 October 2026 · Status: **NOT RUN** · Priority inherited from task: **P2**

[Roadmap](../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../superpowers/specs/2026-10-26-roadmap-08-recurring-tasks.md) · [Plan](../superpowers/plans/2026-10-26-roadmap-08-recurring-tasks.md) · [QA scenarios](08-recurring-tasks-qa.md) · [Shared workflow](WORKFLOW.md)

## Entry gate and scope

This document is **phase 2** of a two-phase task. Begin only after F08-I01–I05 are source complete with a recorded handoff. Test authoring, Gradle/sync/build/lint/schema generation, automated execution, emulator/device and manual checks occur here only. The documentation generation session did not execute these scenarios.

Read [WORKFLOW.md](WORKFLOW.md) for isolation, exact result semantics, required locale/theme/window/API matrix, automated command catalogue and performance protocol. Read the entire specification before deriving test assertions. All cases require task-specific preconditions; do not mark irrelevant branch scenarios PASS—use N/A only with a source-supported scope rationale, and do not turn a deferred investigation into shipped-feature acceptance.

## Fixture

F08: daily series Oct 26–30, Monday/Wednesday series, monthly anchor 31 spanning Jan–Mar 2027, leap-year Feb 2028, completed/skipped/overridden occurrences, two concurrent generators and a six-month closed-app gap. Track nominal-date keys separately from due dates.

Freeze clocks through injection for automation; use dedicated AVDs for manual time/zone/credential changes. Record fixture seed/hash and expected IDs/values independently from production predicates. Reset baseline between independent scenarios. Never substitute real personal records, credentials or health data.

## Coverage obligations

The mapping below identifies the scenarios responsible for each requirement; cases may cover several requirements. During QA add assertion-level mappings and authored test names without dropping any requirement or shared matrix obligation.

| Requirement | Scenario coverage set | Initial result |
| --- | --- | --- |
| F08-R01 — Rules and month end | F08-QA01, F08-QA02 | NOT RUN |
| F08-R02 — History and missed work | F08-QA01, F08-QA03 | NOT RUN |
| F08-R03 — Edit scopes | F08-QA04, F08-QA05 | NOT RUN |
| F08-R04 — Cross-surface behavior | F08-QA06, F08-QA07 | NOT RUN |
| F08-R05 — Persistence and compatibility | F08-QA06, F08-QA08 | NOT RUN |

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

## Automated and manual ownership

Automated command/test-file ownership is in [the plan’s phase 2](../superpowers/plans/2026-10-26-roadmap-08-recurring-tasks.md); no tests are asserted to exist before that phase. Add domain/state cases for pure rules, real Room cases for migration/atomicity, and UI/native cases for interaction. Fakes cannot establish native permission, biometric, provider, launcher, file-picker or shutdown behavior.

Manual/platform obligations: Scope dialogs, large missed-range paging, cross-surface counts, DST travel and notification reconciliation.

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

## Exit gate

- [ ] All F08-R01–R05 assertions and scenarios mapped to actual tests/manual observations.
- [ ] Required host/build/lint/schema/instrumented gates pass for exact candidate; warnings and skips classified.
- [ ] Required native/manual/matrix/performance obligations complete with reproducible evidence.
- [ ] Defects corrected and affected results rerun; remaining blockers and invalidated old evidence explicit.
- [ ] Acceptance decision states exact completed scope. No unrun or blocked scenario is described as passing. For investigation-only outcome, feature implementation/runtime QA remains deferred explicitly.
