# F14 — Build and dependency modernization assessment QA scenarios

Date: 26 October 2026 · Status: **NOT RUN** · Priority inherited from task: **P3**

[Roadmap](../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../superpowers/specs/2026-10-26-roadmap-14-build-modernization.md) · [Plan](../superpowers/plans/2026-10-26-roadmap-14-build-modernization.md) · [QA scenarios](14-build-modernization-qa.md) · [Shared workflow](WORKFLOW.md)

## Entry gate and scope

This document is **phase 2** of a two-phase task. Begin only after F14-I01–I05 are source complete with a recorded handoff. Test authoring, Gradle/sync/build/lint/schema generation, automated execution, emulator/device and manual checks occur here only. The documentation generation session did not execute these scenarios.

Read [WORKFLOW.md](WORKFLOW.md) for isolation, exact result semantics, required locale/theme/window/API matrix, automated command catalogue and performance protocol. Read the entire specification before deriving test assertions. All cases require task-specific preconditions; do not mark irrelevant branch scenarios PASS—use N/A only with a source-supported scope rationale, and do not turn a deferred investigation into shipped-feature acceptance.

## Fixture

F14: one source baseline and one candidate with identical synthetic DB/photos, supported old archives, widgets and native import fixtures. Named API28/API36 devices, matching release signing type, no network permission change. Capture exact dependency/SDK/JDK versions and artifact sizes.

Freeze clocks through injection for automation; use dedicated AVDs for manual time/zone/credential changes. Record fixture seed/hash and expected IDs/values independently from production predicates. Reset baseline between independent scenarios. Never substitute real personal records, credentials or health data.

## Coverage obligations

The mapping below identifies the scenarios responsible for each requirement; cases may cover several requirements. During QA add assertion-level mappings and authored test names without dropping any requirement or shared matrix obligation.

| Requirement | Scenario coverage set | Initial result |
| --- | --- | --- |
| F14-R01 — Justified scope | F14-QA01, F14-QA08 | NOT RUN |
| F14-R02 — Compatibility matrix | F14-QA01, F14-QA02, F14-QA06 | NOT RUN |
| F14-R03 — Staged changes | F14-QA02, F14-QA03, F14-QA08 | NOT RUN |
| F14-R04 — Reproducibility and artifacts | F14-QA02, F14-QA08 | NOT RUN |
| F14-R05 — Release evidence | F14-QA03, F14-QA04, F14-QA05, F14-QA06, F14-QA07 | NOT RUN |

## Scenarios

### F14-QA01 — Assessment traceability

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Check every selected version and motivation against dated official sources and matrix.

**Expected:** No floating/latest pins or unsupported combination; retain decisions justified; minSdk policy and release-channel requirements explicit.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F14-QA02 — Clean dependency resolution

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Resolve baseline and each selected coherent group in a clean QA Gradle cache with authorized repository access.

**Expected:** Dependency graphs/versions reproducible; failures documented without silent version fallback or source changes outside group.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F14-QA03 — Host and lint comparison

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Run all owning host suites/build/lint on baseline/candidate and compare reports.

**Expected:** No new unresolved errors; warnings individually classified; no blanket suppression presented as improvement.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F14-QA04 — Migration and restore

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Upgrade populated baseline DB to candidate and round-trip every supported archive family.

**Expected:** Records/photos/history retained; schema generation/migration matches source; unknown archive versions reject safely.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F14-QA05 — Minified release paths

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Install isolated minified candidate; launch/capture/edit/search/review/photos/widgets and optional integration routes.

**Expected:** No R8-only crash/missing reflection/resource; release behavior matches debug where intended.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F14-QA06 — API and locale regression

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Run API28/API36, EN/FR/FA, RTL/light/dark/large-text and permission matrix.

**Expected:** Supported floor preserved; no new clipped actions, permission regression or Calendar membership change.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F14-QA07 — Performance and size

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Measure comparable APK bytes, cold-start/frame metrics and search/review timings on same hardware/fixture.

**Expected:** Raw before/after samples and p95 reported; regressions are explained or fixed, not attributed to upgrade without evidence.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F14-QA08 — CI artifacts and rollback

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Run candidate CI or equivalent documented local job; verify failure artifacts and restore prior configuration in disposable checkout.

**Expected:** Artifacts contain identity/reports without secrets; rollback group is recoverable; no hosted-CI pass claimed from workflow source alone.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

## Automated and manual ownership

Automated command/test-file ownership is in [the plan’s phase 2](../superpowers/plans/2026-10-26-roadmap-14-build-modernization.md); no tests are asserted to exist before that phase. Add domain/state cases for pure rules, real Room cases for migration/atomicity, and UI/native cases for interaction. Fakes cannot establish native permission, biometric, provider, launcher, file-picker or shutdown behavior.

Manual/platform obligations: Clean-cache resolution, minimum API device, minified release, comparable performance and actual CI when available.

Apply every relevant shared UI/platform matrix cell, not only the default-English emulator. For benchmarks use the shared 5-warmup/30-measurement protocol and report raw samples/p95; for task-specific failures retain the before-fix assertion and after-fix evidence.

## Execution ledger

Evidence directory during execution: `docs/feature-roadmap/evidence/<actual-run-date>/F14/`. Record source/build/environment identity once per run and link each row to its actual artifacts. Add matrix-cell rows as execution proceeds.

| Case | Result | Actual observation | Evidence | Defect/retest |
| --- | --- | --- | --- | --- |
| F14-QA01 | NOT RUN | — | — | — |
| F14-QA02 | NOT RUN | — | — | — |
| F14-QA03 | NOT RUN | — | — | — |
| F14-QA04 | NOT RUN | — | — | — |
| F14-QA05 | NOT RUN | — | — | — |
| F14-QA06 | NOT RUN | — | — | — |
| F14-QA07 | NOT RUN | — | — | — |
| F14-QA08 | NOT RUN | — | — | — |

## Exit gate

- [ ] All F14-R01–R05 assertions and scenarios mapped to actual tests/manual observations.
- [ ] Required host/build/lint/schema/instrumented gates pass for exact candidate; warnings and skips classified.
- [ ] Required native/manual/matrix/performance obligations complete with reproducible evidence.
- [ ] Defects corrected and affected results rerun; remaining blockers and invalidated old evidence explicit.
- [ ] Acceptance decision states exact completed scope. No unrun or blocked scenario is described as passing. For investigation-only outcome, feature implementation/runtime QA remains deferred explicitly.
