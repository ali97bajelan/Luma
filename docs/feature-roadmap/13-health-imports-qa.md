# F13 — Optional sleep and workout imports QA scenarios

Date: 26 October 2026 · Status: **NOT RUN** · Priority inherited from task: **P3**

[Roadmap](../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../superpowers/specs/2026-10-26-roadmap-13-health-imports.md) · [Plan](../superpowers/plans/2026-10-26-roadmap-13-health-imports.md) · [QA scenarios](13-health-imports-qa.md) · [Shared workflow](WORKFLOW.md)

## Entry gate and scope

This document is **phase 2** of a two-phase task. Begin only after F13-I01–I05 are source complete with a recorded handoff. Test authoring, Gradle/sync/build/lint/schema generation, automated execution, emulator/device and manual checks occur here only. The documentation generation session did not execute these scenarios.

Read [WORKFLOW.md](WORKFLOW.md) for isolation, exact result semantics, required locale/theme/window/API matrix, automated command catalogue and performance protocol. Read the entire specification before deriving test assertions. All cases require task-specific preconditions; do not mark irrelevant branch scenarios PASS—use N/A only with a source-supported scope rationale, and do not turn a deferred investigation into shipped-feature acceptance.

## Fixture

F13: actual test-provider steps plus sleep crossing midnight, overlapping sleep from two origins, workout crossing midnight, correction/deletion, empty successful window, provider failure and permission loss mid-query. Fixed import zone Europe/Paris around DST; no personal health records.

Freeze clocks through injection for automation; use dedicated AVDs for manual time/zone/credential changes. Record fixture seed/hash and expected IDs/values independently from production predicates. Reset baseline between independent scenarios. Never substitute real personal records, credentials or health data.

## Coverage obligations

The mapping below identifies the scenarios responsible for each requirement; cases may cover several requirements. During QA add assertion-level mappings and authored test names without dropping any requirement or shared matrix obligation.

| Requirement | Scenario coverage set | Initial result |
| --- | --- | --- |
| F13-R01 — Capability gate | F13-QA01, F13-QA08 | NOT RUN |
| F13-R02 — Granular opt-in | F13-QA02, F13-QA04, F13-QA08 | NOT RUN |
| F13-R03 — Identity and corrections | F13-QA03, F13-QA04 | NOT RUN |
| F13-R04 — Time and overlap | F13-QA05, F13-QA06 | NOT RUN |
| F13-R05 — Presentation and restore | F13-QA07, F13-QA08 | NOT RUN |

## Scenarios

### F13-QA01 — Capability decision

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Review API/version/permission source evidence and workflow rationale against installed provider.

**Expected:** Supported branch has reproducible capability evidence; deferred branch is clearly disabled and never labelled feature-complete or runtime-passed.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F13-QA02 — Independent permissions

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Enable sleep only, deny workouts, revoke steps and return foreground.

**Expected:** Each kind reports actual status and reads only allowed enabled kinds; core capture and other eligible kinds continue.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F13-QA03 — Identity and retry

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Import same provider sessions twice, then correct one ID and delete another in complete window.

**Expected:** No duplicates; correction updates same identity; known deletion reconciles only successful complete coverage.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F13-QA04 — Failure versus empty

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Return provider error/permission loss and then a valid empty response; race disable during query.

**Expected:** Failure preserves saved records/checkpoint; stale query cannot publish; only valid complete empty response reconciles deletions.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F13-QA05 — Midnight and DST

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Read sleep/workout spanning midnight and Paris gap/overlap with saved import zone.

**Expected:** Original instants retained; sleep end-date/workout start-date attribution correct; duration uses actual instants and coverage labels.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F13-QA06 — Source overlaps

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Load same overlapping episode from two origins and select each source for aggregate view.

**Expected:** Sessions labelled separately; single-source interval union avoids double counting; cross-source sum not presented as measured total.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F13-QA07 — Archive and integration regression

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Round-trip sessions, restore legacy archive and re-enable integrations; run existing steps flows.

**Expected:** Summaries/zone preserved where supported; imports off after restore; step behavior unchanged; no Calendar health rows.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F13-QA08 — Native provider and presentation

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Exercise actual consent/update-required/unavailable states and Review details in FR/FA/large text.

**Expected:** Provider-backed behavior recorded separately from fakes; units/source/coverage truthful, controls accessible, no diagnosis or invented metrics.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

## Automated and manual ownership

Automated command/test-file ownership is in [the plan’s phase 2](../superpowers/plans/2026-10-26-roadmap-13-health-imports.md); no tests are asserted to exist before that phase. Add domain/state cases for pure rules, real Room cases for migration/atomicity, and UI/native cases for interaction. Fakes cannot establish native permission, biometric, provider, launcher, file-picker or shutdown behavior.

Manual/platform obligations: Native Health Connect provider, granular consent/revocation and real synthetic source corrections; mocks alone cannot certify.

Apply every relevant shared UI/platform matrix cell, not only the default-English emulator. For benchmarks use the shared 5-warmup/30-measurement protocol and report raw samples/p95; for task-specific failures retain the before-fix assertion and after-fix evidence.

## Execution ledger

Evidence directory during execution: `docs/feature-roadmap/evidence/<actual-run-date>/F13/`. Record source/build/environment identity once per run and link each row to its actual artifacts. Add matrix-cell rows as execution proceeds.

| Case | Result | Actual observation | Evidence | Defect/retest |
| --- | --- | --- | --- | --- |
| F13-QA01 | NOT RUN | — | — | — |
| F13-QA02 | NOT RUN | — | — | — |
| F13-QA03 | NOT RUN | — | — | — |
| F13-QA04 | NOT RUN | — | — | — |
| F13-QA05 | NOT RUN | — | — | — |
| F13-QA06 | NOT RUN | — | — | — |
| F13-QA07 | NOT RUN | — | — | — |
| F13-QA08 | NOT RUN | — | — | — |

## Exit gate

- [ ] All F13-R01–R05 assertions and scenarios mapped to actual tests/manual observations.
- [ ] Required host/build/lint/schema/instrumented gates pass for exact candidate; warnings and skips classified.
- [ ] Required native/manual/matrix/performance obligations complete with reproducible evidence.
- [ ] Defects corrected and affected results rerun; remaining blockers and invalidated old evidence explicit.
- [ ] Acceptance decision states exact completed scope. No unrun or blocked scenario is described as passing. For investigation-only outcome, feature implementation/runtime QA remains deferred explicitly.
