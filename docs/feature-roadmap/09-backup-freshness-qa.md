# F09 — Backup freshness and in-app nudges QA scenarios

Date: 26 October 2026 · Status: **NOT RUN** · Priority inherited from task: **P2**

[Roadmap](../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../superpowers/specs/2026-10-26-roadmap-09-backup-freshness.md) · [Plan](../superpowers/plans/2026-10-26-roadmap-09-backup-freshness.md) · [QA scenarios](09-backup-freshness-qa.md) · [Shared workflow](WORKFLOW.md)

## Entry gate and scope

This document is **phase 2** of a two-phase task. Begin only after F09-I01–I05 are source complete with a recorded handoff. Test authoring, Gradle/sync/build/lint/schema generation, automated execution, emulator/device and manual checks occur here only. The documentation generation session did not execute these scenarios.

Read [WORKFLOW.md](WORKFLOW.md) for isolation, exact result semantics, required locale/theme/window/API matrix, automated command catalogue and performance protocol. Read the entire specification before deriving test assertions. All cases require task-specific preconditions; do not mark irrelevant branch scenarios PASS—use N/A only with a source-supported scope rationale, and do not turn a deferred investigation into shipped-feature acceptance.

## Fixture

F09: frozen install time Oct26 09:00 UTC; never-exported, success plain/encrypted, failed, cancelled, future-dated and preference-write-failure states. Evaluate day7−1ms, day7, day14 and snooze boundary.

Freeze clocks through injection for automation; use dedicated AVDs for manual time/zone/credential changes. Record fixture seed/hash and expected IDs/values independently from production predicates. Reset baseline between independent scenarios. Never substitute real personal records, credentials or health data.

## Coverage obligations

The mapping below identifies the scenarios responsible for each requirement; cases may cover several requirements. During QA add assertion-level mappings and authored test names without dropping any requirement or shared matrix obligation.

| Requirement | Scenario coverage set | Initial result |
| --- | --- | --- |
| F09-R01 — Truthful status | F09-QA01, F09-QA02, F09-QA03, F09-QA08 | NOT RUN |
| F09-R02 — Nudge schedule | F09-QA01, F09-QA04 | NOT RUN |
| F09-R03 — Time corrections | F09-QA05 | NOT RUN |
| F09-R04 — Privacy and destination | F09-QA06, F09-QA07, F09-QA08 | NOT RUN |
| F09-R05 — Restore/reset and failure | F09-QA03, F09-QA06, F09-QA08 | NOT RUN |

## Scenarios

### F09-QA01 — Fresh install grace

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Open at install, day7−1ms and day7 with no export.

**Expected:** Never exported label throughout; nudge absent before and present exactly at 7 days when enabled.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F09-QA02 — Closed-success boundary

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Succeed archive writing but fail stream close; then perform full success.

**Expected:** First attempt does not update time; second records one success after close with accurate encryption flag.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F09-QA03 — Cancel and restore

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Cancel picker/export, fail export, prepare/confirm restore and reopen.

**Expected:** None advances export time; existing device-local metadata persists across content replacement.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F09-QA04 — Dismiss and disable

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Dismiss at eligibility, revisit just before/at +7 days, disable/re-enable.

**Expected:** Snooze persists and expires inclusively; off suppresses nudge; no notifications/jobs are created.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F09-QA05 — Clock correction

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Set now before stored install/success/snooze time, then restore valid chronology.

**Expected:** Time uncertainty appears; no misleading fresh claim or reminder storm; normal policy resumes when valid.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F09-QA06 — Preference failure

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Make recordSuccess persistence fail after a valid external file is closed.

**Expected:** Archive success retained with status warning; no false export-failed result or automatic repeat export.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F09-QA07 — Privacy and accessibility

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Inspect nudge/status under lock, RTL, 200% fonts and screen reader.

**Expected:** No private record data; accurate readable labels and reachable dismiss/backup/settings controls.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F09-QA08 — Reset and provider loss

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Delete external file separately, reopen status, then confirm full app reset.

**Expected:** UI never promises external file existence; reset clears status/settings; no attempt to delete provider originals.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

## Automated and manual ownership

Automated command/test-file ownership is in [the plan’s phase 2](../superpowers/plans/2026-10-26-roadmap-09-backup-freshness.md); no tests are asserted to exist before that phase. Add domain/state cases for pure rules, real Room cases for migration/atomicity, and UI/native cases for interaction. Fakes cannot establish native permission, biometric, provider, launcher, file-picker or shutdown behavior.

Manual/platform obligations: Actual export picker/close behavior, device-local reset, RTL/large-text nudge.

Apply every relevant shared UI/platform matrix cell, not only the default-English emulator. For benchmarks use the shared 5-warmup/30-measurement protocol and report raw samples/p95; for task-specific failures retain the before-fix assertion and after-fix evidence.

## Execution ledger

Evidence directory during execution: `docs/feature-roadmap/evidence/<actual-run-date>/F09/`. Record source/build/environment identity once per run and link each row to its actual artifacts. Add matrix-cell rows as execution proceeds.

| Case | Result | Actual observation | Evidence | Defect/retest |
| --- | --- | --- | --- | --- |
| F09-QA01 | NOT RUN | — | — | — |
| F09-QA02 | NOT RUN | — | — | — |
| F09-QA03 | NOT RUN | — | — | — |
| F09-QA04 | NOT RUN | — | — | — |
| F09-QA05 | NOT RUN | — | — | — |
| F09-QA06 | NOT RUN | — | — | — |
| F09-QA07 | NOT RUN | — | — | — |
| F09-QA08 | NOT RUN | — | — | — |

## Exit gate

- [ ] All F09-R01–R05 assertions and scenarios mapped to actual tests/manual observations.
- [ ] Required host/build/lint/schema/instrumented gates pass for exact candidate; warnings and skips classified.
- [ ] Required native/manual/matrix/performance obligations complete with reproducible evidence.
- [ ] Defects corrected and affected results rerun; remaining blockers and invalidated old evidence explicit.
- [ ] Acceptance decision states exact completed scope. No unrun or blocked scenario is described as passing. For investigation-only outcome, feature implementation/runtime QA remains deferred explicitly.
