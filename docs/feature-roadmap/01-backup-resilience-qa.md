# F01 — Backup and restore resilience QA scenarios

Date: 26 October 2026 · Status: **NOT RUN** · Priority inherited from task: **P0**

[Roadmap](../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../superpowers/specs/2026-10-26-roadmap-01-backup-resilience.md) · [Plan](../superpowers/plans/2026-10-26-roadmap-01-backup-resilience.md) · [QA scenarios](01-backup-resilience-qa.md) · [Shared workflow](WORKFLOW.md)

## Entry gate and scope

This document is **phase 2** of a two-phase task. Begin only after F01-I01–I05 are source complete with a recorded handoff. Test authoring, Gradle/sync/build/lint/schema generation, automated execution, emulator/device and manual checks occur here only. The documentation generation session did not execute these scenarios.

Read [WORKFLOW.md](WORKFLOW.md) for isolation, exact result semantics, required locale/theme/window/API matrix, automated command catalogue and performance protocol. Read the entire specification before deriving test assertions. All cases require task-specific preconditions; do not mark irrelevant branch scenarios PASS—use N/A only with a source-supported scope rationale, and do not turn a deferred investigation into shipped-feature acceptance.

## Fixture

F01: dataset A has 3 journals, 2 memories, 1 mood, 2 tasks (one timed), a habit with 3 logs, linked person/place/tag, and 3 distinct photos with recorded SHA-256. Dataset B has disjoint IDs and 2 photos. Include a protected unsaved photo draft. Export supported legacy fixtures separately; never use real records.

Freeze clocks through injection for automation; use dedicated AVDs for manual time/zone/credential changes. Record fixture seed/hash and expected IDs/values independently from production predicates. Reset baseline between independent scenarios. Never substitute real personal records, credentials or health data.

## Coverage obligations

The mapping below identifies the scenarios responsible for each requirement; cases may cover several requirements. During QA add assertion-level mappings and authored test names without dropping any requirement or shared matrix obligation.

| Requirement | Scenario coverage set | Initial result |
| --- | --- | --- |
| F01-R01 — Atomic replacement | F01-QA01, F01-QA02, F01-QA04, F01-QA05 | NOT RUN |
| F01-R02 — Export truthfulness | F01-QA03, F01-QA08 | NOT RUN |
| F01-R03 — Validation and bounds | F01-QA01, F01-QA06 | NOT RUN |
| F01-R04 — Concurrency and recovery | F01-QA04, F01-QA05, F01-QA07 | NOT RUN |
| F01-R05 — Usable recovery | F01-QA02, F01-QA03, F01-QA04, F01-QA05, F01-QA08 | NOT RUN |

## Scenarios

### F01-QA01 — Round trip

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Export A, prepare it while B is live, inspect preview, confirm replacement, close and reopen storage.

**Expected:** IDs, values, task minutes, links and photo hashes match A; derived caches invalidate and restored reminders/imports remain off.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F01-QA02 — Cancel preview

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Prepare A over B, cancel, then reopen B and inspect all referenced files.

**Expected:** B and its photos are unchanged; preparation files are released; no replacement or success notification occurs.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F01-QA03 — Output and low-storage faults

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Fail output on first write, middle write, archive finalization and stream close; separately fail private staging allocation.

**Expected:** No false export success; live data intact; partial destination warning and retry are available; owned temporary resources release.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F01-QA04 — Pre-commit interruptions

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Terminate the disposable app at each stage through before-commit; restart and inspect B, photos and draft lease.

**Expected:** B is complete and usable; no A-only reference is published; protected draft remains accessible; later cleanup touches only orphans.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F01-QA05 — Post-commit interruption

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Terminate after DB commit but before cleanup, then restart and read every A photo.

**Expected:** A is complete; B-only files may await safe cleanup but are never referenced; no automatic second restore occurs.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F01-QA06 — Corruption and limits

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Try missing photo, changed hash, duplicate/traversing path, unsupported version, truncated manifest and over-limit archive.

**Expected:** Preparation fails before replacement; B remains exact; error contains no private contents.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F01-QA07 — Concurrency

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Pause snapshot/restore at seams while an editor imports/removes a photo and cleanup attempts to run; then release operations.

**Expected:** Snapshot is internally consistent; transaction rollback and photo ownership preserve committed content and protected drafts; no deadlock.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F01-QA08 — Provider and power behavior

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Use real create/open-document pickers; interrupt provider access and simulate abrupt isolated-device shutdown around publication.

**Expected:** Picker cancellation is harmless; restart state meets atomicity contract. Process-kill evidence alone is not labelled power-loss validation.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

## Automated and manual ownership

Automated command/test-file ownership is in [the plan’s phase 2](../superpowers/plans/2026-10-26-roadmap-01-backup-resilience.md); no tests are asserted to exist before that phase. Add domain/state cases for pure rules, real Room cases for migration/atomicity, and UI/native cases for interaction. Fakes cannot establish native permission, biometric, provider, launcher, file-picker or shutdown behavior.

Manual/platform obligations: Real document provider, disk exhaustion, lifecycle interruption and abrupt-shutdown recovery on disposable storage.

Apply every relevant shared UI/platform matrix cell, not only the default-English emulator. For benchmarks use the shared 5-warmup/30-measurement protocol and report raw samples/p95; for task-specific failures retain the before-fix assertion and after-fix evidence.

## Execution ledger

Evidence directory during execution: `docs/feature-roadmap/evidence/<actual-run-date>/F01/`. Record source/build/environment identity once per run and link each row to its actual artifacts. Add matrix-cell rows as execution proceeds.

| Case | Result | Actual observation | Evidence | Defect/retest |
| --- | --- | --- | --- | --- |
| F01-QA01 | NOT RUN | — | — | — |
| F01-QA02 | NOT RUN | — | — | — |
| F01-QA03 | NOT RUN | — | — | — |
| F01-QA04 | NOT RUN | — | — | — |
| F01-QA05 | NOT RUN | — | — | — |
| F01-QA06 | NOT RUN | — | — | — |
| F01-QA07 | NOT RUN | — | — | — |
| F01-QA08 | NOT RUN | — | — | — |

## Exit gate

- [ ] All F01-R01–R05 assertions and scenarios mapped to actual tests/manual observations.
- [ ] Required host/build/lint/schema/instrumented gates pass for exact candidate; warnings and skips classified.
- [ ] Required native/manual/matrix/performance obligations complete with reproducible evidence.
- [ ] Defects corrected and affected results rerun; remaining blockers and invalidated old evidence explicit.
- [ ] Acceptance decision states exact completed scope. No unrun or blocked scenario is described as passing. For investigation-only outcome, feature implementation/runtime QA remains deferred explicitly.
