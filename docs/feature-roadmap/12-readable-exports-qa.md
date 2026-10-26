# F12 — Selective readable exports QA scenarios

Date: 26 October 2026 · Status: **NOT RUN** · Priority inherited from task: **P2**

[Roadmap](../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../superpowers/specs/2026-10-26-roadmap-12-readable-exports.md) · [Plan](../superpowers/plans/2026-10-26-roadmap-12-readable-exports.md) · [QA scenarios](12-readable-exports-qa.md) · [Shared workflow](WORKFLOW.md)

## Entry gate and scope

This document is **phase 2** of a two-phase task. Begin only after F12-I01–I05 are source complete with a recorded handoff. Test authoring, Gradle/sync/build/lint/schema generation, automated execution, emulator/device and manual checks occur here only. The documentation generation session did not execute these scenarios.

Read [WORKFLOW.md](WORKFLOW.md) for isolation, exact result semantics, required locale/theme/window/API matrix, automated command catalogue and performance protocol. Read the entire specification before deriving test assertions. All cases require task-specific preconditions; do not mark irrelevant branch scenarios PASS—use N/A only with a source-supported scope rationale, and do not turn a deferred investigation into shipped-feature acceptance.

## Fixture

F12: journals/memories before/at/after DST day boundaries, marked journal, excluded mood/task, photo-only context, structured linked names plus free-text name, multiline Persian/French/emoji, Markdown punctuation titles and 5,000-record selected range. Record expected exported IDs separately.

Freeze clocks through injection for automation; use dedicated AVDs for manual time/zone/credential changes. Record fixture seed/hash and expected IDs/values independently from production predicates. Reset baseline between independent scenarios. Never substitute real personal records, credentials or health data.

## Coverage obligations

The mapping below identifies the scenarios responsible for each requirement; cases may cover several requirements. During QA add assertion-level mappings and authored test names without dropping any requirement or shared matrix obligation.

| Requirement | Scenario coverage set | Initial result |
| --- | --- | --- |
| F12-R01 — Selection | F12-QA01, F12-QA04 | NOT RUN |
| F12-R02 — Privacy defaults | F12-QA02, F12-QA06 | NOT RUN |
| F12-R03 — Output format | F12-QA03 | NOT RUN |
| F12-R04 — Safe file lifecycle | F12-QA04, F12-QA05, F12-QA06, F12-QA07 | NOT RUN |
| F12-R05 — Scope and reachability | F12-QA06, F12-QA07, F12-QA08 | NOT RUN |

## Scenarios

### F12-QA01 — Individual and range selection

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Export one journal then inclusive DST date range with known boundary records.

**Expected:** Exactly expected IDs included once; date endpoints correct across 23/25-hour days; moods/tasks absent.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F12-QA02 — Default privacy and explicit names

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Preview/export with default names off then enable names; inspect photo-linked record.

**Expected:** No structured linked names by default; explicit option includes only selected linked metadata; photos omitted and free-text non-redaction explained.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F12-QA03 — Unicode and format

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Export multiline RTL/emoji and metadata containing #, brackets, backticks and newlines as both formats.

**Expected:** UTF-8 body preserved, generated metadata escaped, clear record boundaries and unambiguous offset/zone timestamps.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F12-QA04 — Snapshot consistency

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Prepare preview, concurrently edit/delete source, export; change selection before confirmation.

**Expected:** Prepared snapshot matches preview; selection change forces new preview; no accidental extra record or stale count.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F12-QA05 — Failure and cancellation

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Cancel picker, fail middle write/close, exhaust staging space and retry.

**Expected:** Saved records untouched; no false success; owned temporary files cleaned and partial-provider warning accurate.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F12-QA06 — Share and lock

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Lock between preview and share, unlock then use/cancel explicit chooser.

**Expected:** No private preview or share before auth; one explicit export/share; no automatic recipient or upload.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F12-QA07 — Large range lifecycle

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Export 5,000 records, rotate/kill/restart while preparing, inspect memory and cleanup.

**Expected:** Bounded streaming, actionable progress/error, no oversized saved state or unowned cache deletion.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F12-QA08 — Presentation and backup distinction

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Use FR/FA 200% fonts/TalkBack and attempt to select readable output for backup restore.

**Expected:** Controls reachable; UI distinguishes readable exports; backup importer safely rejects non-archive output.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

## Automated and manual ownership

Automated command/test-file ownership is in [the plan’s phase 2](../superpowers/plans/2026-10-26-roadmap-12-readable-exports.md); no tests are asserted to exist before that phase. Add domain/state cases for pure rules, real Room cases for migration/atomicity, and UI/native cases for interaction. Fakes cannot establish native permission, biometric, provider, launcher, file-picker or shutdown behavior.

Manual/platform obligations: System create-document/share chooser, real text readers, range privacy inspection and large output lifecycle.

Apply every relevant shared UI/platform matrix cell, not only the default-English emulator. For benchmarks use the shared 5-warmup/30-measurement protocol and report raw samples/p95; for task-specific failures retain the before-fix assertion and after-fix evidence.

## Execution ledger

Evidence directory during execution: `docs/feature-roadmap/evidence/<actual-run-date>/F12/`. Record source/build/environment identity once per run and link each row to its actual artifacts. Add matrix-cell rows as execution proceeds.

| Case | Result | Actual observation | Evidence | Defect/retest |
| --- | --- | --- | --- | --- |
| F12-QA01 | NOT RUN | — | — | — |
| F12-QA02 | NOT RUN | — | — | — |
| F12-QA03 | NOT RUN | — | — | — |
| F12-QA04 | NOT RUN | — | — | — |
| F12-QA05 | NOT RUN | — | — | — |
| F12-QA06 | NOT RUN | — | — | — |
| F12-QA07 | NOT RUN | — | — | — |
| F12-QA08 | NOT RUN | — | — | — |

## Exit gate

- [ ] All F12-R01–R05 assertions and scenarios mapped to actual tests/manual observations.
- [ ] Required host/build/lint/schema/instrumented gates pass for exact candidate; warnings and skips classified.
- [ ] Required native/manual/matrix/performance obligations complete with reproducible evidence.
- [ ] Defects corrected and affected results rerun; remaining blockers and invalidated old evidence explicit.
- [ ] Acceptance decision states exact completed scope. No unrun or blocked scenario is described as passing. For investigation-only outcome, feature implementation/runtime QA remains deferred explicitly.
