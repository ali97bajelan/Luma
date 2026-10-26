# F05 — Password-protected portable backups QA scenarios

Date: 26 October 2026 · Status: **NOT RUN** · Priority inherited from task: **P1**

[Roadmap](../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../superpowers/specs/2026-10-26-roadmap-05-encrypted-backups.md) · [Plan](../superpowers/plans/2026-10-26-roadmap-05-encrypted-backups.md) · [QA scenarios](05-encrypted-backups-qa.md) · [Shared workflow](WORKFLOW.md)

## Entry gate and scope

This document is **phase 2** of a two-phase task. Begin only after F05-I01–I05 are source complete with a recorded handoff. Test authoring, Gradle/sync/build/lint/schema generation, automated execution, emulator/device and manual checks occur here only. The documentation generation session did not execute these scenarios.

Read [WORKFLOW.md](WORKFLOW.md) for isolation, exact result semantics, required locale/theme/window/API matrix, automated command catalogue and performance protocol. Read the entire specification before deriving test assertions. All cases require task-specific preconditions; do not mark irrelevant branch scenarios PASS—use N/A only with a source-supported scope rationale, and do not turn a deferred investigation into shipped-feature acceptance.

## Fixture

F05: F01 archive with Unicode private text and photos, passwords of 11/12 code points, leading/trailing spaces, mixed Persian/French and emoji, supported plaintext schemas and two independent disposable devices/installations. QA generates public synthetic encrypted vectors and records exact library/envelope versions.

Freeze clocks through injection for automation; use dedicated AVDs for manual time/zone/credential changes. Record fixture seed/hash and expected IDs/values independently from production predicates. Reset baseline between independent scenarios. Never substitute real personal records, credentials or health data.

## Coverage obligations

The mapping below identifies the scenarios responsible for each requirement; cases may cover several requirements. During QA add assertion-level mappings and authored test names without dropping any requirement or shared matrix obligation.

| Requirement | Scenario coverage set | Initial result |
| --- | --- | --- |
| F05-R01 — Portable format contract | F05-QA02, F05-QA04, F05-QA08 | NOT RUN |
| F05-R02 — Authentication boundary | F05-QA03, F05-QA04 | NOT RUN |
| F05-R03 — User interaction | F05-QA01, F05-QA05 | NOT RUN |
| F05-R04 — Secret lifetime and failures | F05-QA01, F05-QA06, F05-QA07 | NOT RUN |
| F05-R05 — Compatibility | F05-QA02, F05-QA03, F05-QA05, F05-QA08 | NOT RUN |

## Scenarios

### F05-QA01 — Password validation

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Try empty/11-code-point password, mismatch, 12-code-point Unicode, spaces and reveal toggle.

**Expected:** Invalid inputs cannot export; exact Unicode password and confirmation accepted without trimming; no password saved after rotation/process death.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F05-QA02 — Cross-device restore

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Export encrypted F01 on A, copy to B with no original app keys, enter exact password and restore.

**Expected:** All records/photo hashes match and restore does not depend on device A; output success follows stream close.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F05-QA03 — Authentication attacks

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Alter each header field/body segment; truncate at boundaries, reorder/duplicate segments, append bytes and use wrong password.

**Expected:** No record preview or live replacement before complete authentication; bounded generic failure; all live records unchanged.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F05-QA04 — Resource bounds

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Try excessive KDF cost/size fields, malicious lengths, near-limit archive and insufficient private disk.

**Expected:** Bounds reject before unbounded allocation/CPU; no OOM or silent parameter downgrade; staging cleanup is safe.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F05-QA05 — Legacy and versions

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Open every supported plaintext fixture, current encrypted fixture, unknown envelope and unknown inner schema.

**Expected:** Legacy readers retain behavior; unsupported versions fail before replacement; version dispatch cannot downgrade encrypted content silently.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F05-QA06 — Interruption and cleanup

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Cancel during encryption/decryption, kill process, fail output close and restart.

**Expected:** No false success or partial replacement; owned plaintext staging is cleaned safely; password is requested again.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F05-QA07 — Secret inspection

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Inspect app logs, saved state, preferences, crash breadcrumbs and public envelope fields using synthetic marker passwords/titles.

**Expected:** No password, derived key, private title or photo filename leak; documented transient memory limitations are accurate.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F05-QA08 — Cost and review

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Measure password derivation, streaming memory/disk and restore latency on minimum representative API 28 hardware; review against library vectors.

**Expected:** Pinned format interoperates, bounded memory works near supported sizes, and crypto review signs off; unacceptable cost remains open without weakening KDF silently.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

## Automated and manual ownership

Automated command/test-file ownership is in [the plan’s phase 2](../superpowers/plans/2026-10-26-roadmap-05-encrypted-backups.md); no tests are asserted to exist before that phase. Add domain/state cases for pure rules, real Room cases for migration/atomicity, and UI/native cases for interaction. Fakes cannot establish native permission, biometric, provider, launcher, file-picker or shutdown behavior.

Manual/platform obligations: Two-device portability, password input/rotation, near-limit file handling and focused cryptographic format review.

Apply every relevant shared UI/platform matrix cell, not only the default-English emulator. For benchmarks use the shared 5-warmup/30-measurement protocol and report raw samples/p95; for task-specific failures retain the before-fix assertion and after-fix evidence.

## Execution ledger

Evidence directory during execution: `docs/feature-roadmap/evidence/<actual-run-date>/F05/`. Record source/build/environment identity once per run and link each row to its actual artifacts. Add matrix-cell rows as execution proceeds.

| Case | Result | Actual observation | Evidence | Defect/retest |
| --- | --- | --- | --- | --- |
| F05-QA01 | NOT RUN | — | — | — |
| F05-QA02 | NOT RUN | — | — | — |
| F05-QA03 | NOT RUN | — | — | — |
| F05-QA04 | NOT RUN | — | — | — |
| F05-QA05 | NOT RUN | — | — | — |
| F05-QA06 | NOT RUN | — | — | — |
| F05-QA07 | NOT RUN | — | — | — |
| F05-QA08 | NOT RUN | — | — | — |

## Exit gate

- [ ] All F05-R01–R05 assertions and scenarios mapped to actual tests/manual observations.
- [ ] Required host/build/lint/schema/instrumented gates pass for exact candidate; warnings and skips classified.
- [ ] Required native/manual/matrix/performance obligations complete with reproducible evidence.
- [ ] Defects corrected and affected results rerun; remaining blockers and invalidated old evidence explicit.
- [ ] Acceptance decision states exact completed scope. No unrun or blocked scenario is described as passing. For investigation-only outcome, feature implementation/runtime QA remains deferred explicitly.
