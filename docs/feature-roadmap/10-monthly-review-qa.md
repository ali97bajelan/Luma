# F10 — Monthly reflection and review notes QA scenarios

Date: 26 October 2026 · Status: **NOT RUN** · Priority inherited from task: **P2**

[Roadmap](../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../superpowers/specs/2026-10-26-roadmap-10-monthly-review.md) · [Plan](../superpowers/plans/2026-10-26-roadmap-10-monthly-review.md) · [QA scenarios](10-monthly-review-qa.md) · [Shared workflow](WORKFLOW.md)

## Entry gate and scope

This document is **phase 2** of a two-phase task. Begin only after F10-I01–I05 are source complete with a recorded handoff. Test authoring, Gradle/sync/build/lint/schema generation, automated execution, emulator/device and manual checks occur here only. The documentation generation session did not execute these scenarios.

Read [WORKFLOW.md](WORKFLOW.md) for isolation, exact result semantics, required locale/theme/window/API matrix, automated command catalogue and performance protocol. Read the entire specification before deriving test assertions. All cases require task-specific preconditions; do not mark irrelevant branch scenarios PASS—use N/A only with a source-supported scope rationale, and do not turn a deferred investigation into shipped-feature acceptance.

## Fixture

F10: Jan31/Feb28/Feb29/Mar31 month boundaries; current Oct26 2026; 50,000-event fixture with days missing mood, explicit zero steps, partial usage, duplicate-linked people and habits with schedule changes. Notes exist for same month in two zones plus one weekly note.

Freeze clocks through injection for automation; use dedicated AVDs for manual time/zone/credential changes. Record fixture seed/hash and expected IDs/values independently from production predicates. Reset baseline between independent scenarios. Never substitute real personal records, credentials or health data.

## Coverage obligations

The mapping below identifies the scenarios responsible for each requirement; cases may cover several requirements. During QA add assertion-level mappings and authored test names without dropping any requirement or shared matrix obligation.

| Requirement | Scenario coverage set | Initial result |
| --- | --- | --- |
| F10-R01 — Period comparisons | F10-QA01, F10-QA02, F10-QA08 | NOT RUN |
| F10-R02 — Metric truthfulness | F10-QA03, F10-QA04, F10-QA08 | NOT RUN |
| F10-R03 — Review notes | F10-QA05, F10-QA08 | NOT RUN |
| F10-R04 — Freshness and persistence | F10-QA05, F10-QA06 | NOT RUN |
| F10-R05 — Performance and UI | F10-QA07, F10-QA08 | NOT RUN |

## Scenarios

### F10-QA01 — Current comparison

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Select current Oct26 2026 and inspect comparison ranges and today label.

**Expected:** Oct1–26 compares Sep1–26; today partial is visible; no invented complete-provider coverage.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F10-QA02 — Unequal months and leap

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Compare completed March and current March31 against February in 2027/2028.

**Expected:** Complete periods use actual month lengths; current matching-day comparison clamps and labels Feb28/29 accurately.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F10-QA03 — Missing and deduplication

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Load missing mood days, explicit zeros, partial usage and multiple linked rows.

**Expected:** No zero substitution or duplicate event weighting; grouped units/sources and counts match independently calculated fixtures.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F10-QA04 — Source drill-down

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Open each metric then page >50 contributing entries and return.

**Expected:** Every row contributes to selected metric/range; stable no-duplicate pages; return retains period/scroll.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F10-QA05 — Note lifecycle

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Write, rotate, edit concurrently, cancel discard, save, delete with confirmation and reopen.

**Expected:** Draft and revision rules hold; blank cannot silently delete; source records untouched; zone-specific notes remain distinct.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F10-QA06 — Freshness and archives

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Edit/delete source, import correction, restore older archive and round-trip new notes.

**Expected:** Metrics update; notes persist in new archive; older archive replacement leaves no notes; stale requests cannot overwrite restored state.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F10-QA07 — Performance and races

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Run 5 warmups/30 timed weekly/monthly samples, rapidly switch periods with late responses.

**Expected:** Report p95 including mapping and cold sample; <300 ms target or explicit open failure; only current period publishes.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F10-QA08 — Locale and future periods

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Browse future/empty month in FA RTL/light/dark with 200% font; change zone.

**Expected:** Accurate empty/partial language, reachable actions and note-zone explanation; no clinical or causal claims.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

## Automated and manual ownership

Automated command/test-file ownership is in [the plan’s phase 2](../superpowers/plans/2026-10-26-roadmap-10-monthly-review.md); no tests are asserted to exist before that phase. Add domain/state cases for pure rules, real Room cases for migration/atomicity, and UI/native cases for interaction. Fakes cannot establish native permission, biometric, provider, launcher, file-picker or shutdown behavior.

Manual/platform obligations: Representative-phone aggregation performance, long source lists, review-note drafts and multilingual month presentation.

Apply every relevant shared UI/platform matrix cell, not only the default-English emulator. For benchmarks use the shared 5-warmup/30-measurement protocol and report raw samples/p95; for task-specific failures retain the before-fix assertion and after-fix evidence.

## Execution ledger

Evidence directory during execution: `docs/feature-roadmap/evidence/<actual-run-date>/F10/`. Record source/build/environment identity once per run and link each row to its actual artifacts. Add matrix-cell rows as execution proceeds.

| Case | Result | Actual observation | Evidence | Defect/retest |
| --- | --- | --- | --- | --- |
| F10-QA01 | NOT RUN | — | — | — |
| F10-QA02 | NOT RUN | — | — | — |
| F10-QA03 | NOT RUN | — | — | — |
| F10-QA04 | NOT RUN | — | — | — |
| F10-QA05 | NOT RUN | — | — | — |
| F10-QA06 | NOT RUN | — | — | — |
| F10-QA07 | NOT RUN | — | — | — |
| F10-QA08 | NOT RUN | — | — | — |

## Exit gate

- [ ] All F10-R01–R05 assertions and scenarios mapped to actual tests/manual observations.
- [ ] Required host/build/lint/schema/instrumented gates pass for exact candidate; warnings and skips classified.
- [ ] Required native/manual/matrix/performance obligations complete with reproducible evidence.
- [ ] Defects corrected and affected results rerun; remaining blockers and invalidated old evidence explicit.
- [ ] Acceptance decision states exact completed scope. No unrun or blocked scenario is described as passing. For investigation-only outcome, feature implementation/runtime QA remains deferred explicitly.
