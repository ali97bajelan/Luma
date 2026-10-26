# F03 — Fast search across entry content QA scenarios

Date: 26 October 2026 · Status: **NOT RUN** · Priority inherited from task: **P1**

[Roadmap](../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../superpowers/specs/2026-10-26-roadmap-03-search.md) · [Plan](../superpowers/plans/2026-10-26-roadmap-03-search.md) · [QA scenarios](03-search-qa.md) · [Shared workflow](WORKFLOW.md)

## Entry gate and scope

This document is **phase 2** of a two-phase task. Begin only after F03-I01–I05 are source complete with a recorded handoff. Test authoring, Gradle/sync/build/lint/schema generation, automated execution, emulator/device and manual checks occur here only. The documentation generation session did not execute these scenarios.

Read [WORKFLOW.md](WORKFLOW.md) for isolation, exact result semantics, required locale/theme/window/API matrix, automated command catalogue and performance protocol. Read the entire specification before deriving test assertions. All cases require task-specific preconditions; do not mark irrelevant branch scenarios PASS—use N/A only with a source-supported scope rationale, and do not turn a deferred investigation into shipped-feature acceptance.

## Fixture

F03: 50,000 stable-ID records across 5 years, including 10,000 journals with 1,300-character bodies, 5,000 tasks (dated/undated/completed), marked/unmarked journals, shared catalog names, exact and no-match tokens, one/two-character terms, %/_/backslash, emoji and EN/FR/FA text. Store generator seed and expected ID sets independently from query code.

Freeze clocks through injection for automation; use dedicated AVDs for manual time/zone/credential changes. Record fixture seed/hash and expected IDs/values independently from production predicates. Reset baseline between independent scenarios. Never substitute real personal records, credentials or health data.

## Coverage obligations

The mapping below identifies the scenarios responsible for each requirement; cases may cover several requirements. During QA add assertion-level mappings and authored test names without dropping any requirement or shared matrix obligation.

| Requirement | Scenario coverage set | Initial result |
| --- | --- | --- |
| F03-R01 — Search semantics | F03-QA01, F03-QA02, F03-QA04 | NOT RUN |
| F03-R02 — Filter membership | F03-QA03, F03-QA04 | NOT RUN |
| F03-R03 — Deduplication and ordering | F03-QA02, F03-QA03, F03-QA06 | NOT RUN |
| F03-R04 — Freshness and privacy | F03-QA05, F03-QA08 | NOT RUN |
| F03-R05 — Responsiveness | F03-QA06, F03-QA07, F03-QA08 | NOT RUN |

## Scenarios

### F03-QA01 — Legacy matching

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Search title/catalog fragments, literal %/_/backslash and one/two-character terms with no new type filter.

**Expected:** All pre-existing event matches remain discoverable with escaped literals; duplicates never appear.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F03-QA02 — New content and routing

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Search a journal-body-only phrase, memory-note-only phrase and task-note-only phrase; open each hit.

**Expected:** Each returns the correct typed original ID, bounded snippet and destination; no copied source record is created.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F03-QA03 — Combined filters

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Combine date, selected tags/people/place and kinds; include an undated task and a memory-marked journal.

**Expected:** Membership follows all specified predicates; unlinked tasks are excluded by catalog filters; marked source appears once.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F03-QA04 — Multilingual semantics

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Search NFC/decomposed French, accents, Persian variants, ZWNJ, mixed RTL/LTR, emoji and locale switches.

**Expected:** Canonical equivalents and case policy are consistent; distinct accents/Persian variants are not silently conflated; snippet truncation preserves code points.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F03-QA05 — Freshness and restore

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Edit body, rename linked person, delete a source, then restore a different dataset while search is open.

**Expected:** Old hits disappear; new hits appear; derived index rebuild/invalidation is complete; stale navigation shows unavailable safely.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F03-QA06 — Race and paging

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Issue query A then B, return A last, load more tied timestamps and rapidly change dates.

**Expected:** Only current query state publishes; deterministic no-duplicate pages and full totals survive cancellation/retry.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F03-QA07 — Repository benchmark

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** On named hardware run 5 warmups and 30 measured repetitions per title/body/task/short/no-match/broad query; capture cold first-open separately.

**Expected:** Report raw samples and p95 including count/mapping; each required warm query meets <300 ms or task acceptance remains open.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F03-QA08 — Migration and release

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Upgrade a populated prior DB, rebuild index, restore legacy archives, and run minified search with 200% fonts/RTL.

**Expected:** No data loss or extra archive inventory; all content remains searchable and final result/actions stay reachable.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

## Automated and manual ownership

Automated command/test-file ownership is in [the plan’s phase 2](../superpowers/plans/2026-10-26-roadmap-03-search.md); no tests are asserted to exist before that phase. Add domain/state cases for pure rules, real Room cases for migration/atomicity, and UI/native cases for interaction. Fakes cannot establish native permission, biometric, provider, launcher, file-picker or shutdown behavior.

Manual/platform obligations: Representative phone performance, 50k fixture, RTL/large text and minified navigation.

Apply every relevant shared UI/platform matrix cell, not only the default-English emulator. For benchmarks use the shared 5-warmup/30-measurement protocol and report raw samples/p95; for task-specific failures retain the before-fix assertion and after-fix evidence.

## Execution ledger

Evidence directory during execution: `docs/feature-roadmap/evidence/<actual-run-date>/F03/`. Record source/build/environment identity once per run and link each row to its actual artifacts. Add matrix-cell rows as execution proceeds.

| Case | Result | Actual observation | Evidence | Defect/retest |
| --- | --- | --- | --- | --- |
| F03-QA01 | NOT RUN | — | — | — |
| F03-QA02 | NOT RUN | — | — | — |
| F03-QA03 | NOT RUN | — | — | — |
| F03-QA04 | NOT RUN | — | — | — |
| F03-QA05 | NOT RUN | — | — | — |
| F03-QA06 | NOT RUN | — | — | — |
| F03-QA07 | NOT RUN | — | — | — |
| F03-QA08 | NOT RUN | — | — | — |

## Exit gate

- [ ] All F03-R01–R05 assertions and scenarios mapped to actual tests/manual observations.
- [ ] Required host/build/lint/schema/instrumented gates pass for exact candidate; warnings and skips classified.
- [ ] Required native/manual/matrix/performance obligations complete with reproducible evidence.
- [ ] Defects corrected and affected results rerun; remaining blockers and invalidated old evidence explicit.
- [ ] Acceptance decision states exact completed scope. No unrun or blocked scenario is described as passing. For investigation-only outcome, feature implementation/runtime QA remains deferred explicitly.
