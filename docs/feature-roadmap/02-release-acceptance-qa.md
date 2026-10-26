# F02 — Current release and Calendar acceptance QA scenarios

Date: 26 October 2026 · Status: **NOT RUN** · Priority inherited from task: **P0**

[Roadmap](../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../superpowers/specs/2026-10-26-roadmap-02-release-acceptance.md) · [Plan](../superpowers/plans/2026-10-26-roadmap-02-release-acceptance.md) · [QA scenarios](02-release-acceptance-qa.md) · [Shared workflow](WORKFLOW.md)

## Entry gate and scope

This document is **phase 2** of a two-phase task. Begin only after F02-I01–I05 are source complete with a recorded handoff. Test authoring, Gradle/sync/build/lint/schema generation, automated execution, emulator/device and manual checks occur here only. The documentation generation session did not execute these scenarios.

Read [WORKFLOW.md](WORKFLOW.md) for isolation, exact result semantics, required locale/theme/window/API matrix, automated command catalogue and performance protocol. Read the entire specification before deriving test assertions. All cases require task-specific preconditions; do not mark irrelevant branch scenarios PASS—use N/A only with a source-supported scope rationale, and do not turn a deferred investigation into shipped-feature acceptance.

## Fixture

F02: use CQA fixtures F1–F4 from docs/CALENDAR_QA.md unchanged, plus task/usage/health/photo/backup fixtures from the corresponding acceptance records. Establish both fresh-install and upgraded database states. Synthetic package IDs must be distinct from the personal install.

Freeze clocks through injection for automation; use dedicated AVDs for manual time/zone/credential changes. Record fixture seed/hash and expected IDs/values independently from production predicates. Reset baseline between independent scenarios. Never substitute real personal records, credentials or health data.

## Coverage obligations

The mapping below identifies the scenarios responsible for each requirement; cases may cover several requirements. During QA add assertion-level mappings and authored test names without dropping any requirement or shared matrix obligation.

| Requirement | Scenario coverage set | Initial result |
| --- | --- | --- |
| F02-R01 — Scope freeze | F02-QA01, F02-QA08 | NOT RUN |
| F02-R02 — Calendar contract | F02-QA02 | NOT RUN |
| F02-R03 — Historical failures | F02-QA03 | NOT RUN |
| F02-R04 — Critical platform flows | F02-QA04, F02-QA05, F02-QA06, F02-QA07 | NOT RUN |
| F02-R05 — Release decision | F02-QA08 | NOT RUN |

## Scenarios

### F02-QA01 — Candidate identity

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Record Git SHA, dirty-patch hash, APK SHA, version, package IDs and full environment before installation.

**Expected:** Every later result resolves to the candidate; no real app data is installed over or reset.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F02-QA02 — Calendar full package

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Execute CQA-01–32 and required locale/theme/window/font/IME/API matrix cells from CALENDAR_QA.md.

**Expected:** Each full scenario has its own evidence; host tests and the focused habit glyph pass do not pre-certify unrun cases.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F02-QA03 — Timeline old failures

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Recreate the prior ordering/page datasets and run affected persistence cases plus day/paging UI flows.

**Expected:** Stable occurrence/ID order and bounded pages pass, or a reproducible defect with source-linked correction remains release-blocking.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F02-QA04 — Permissions and import

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Grant, deny and revoke usage/health access; toggle imports and return to foreground with native synthetic provider records.

**Expected:** Eligibility is truthful; allowed imports refresh, denial/revocation stops reads, saved history stays intact.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F02-QA05 — Photos and restore

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Select system photos, remove originals, reopen app; use backup pickers and replacement validation.

**Expected:** Private copied photos persist; task/memory memberships refresh after restore; no stale action mutates replacement rows.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F02-QA06 — Reset and reopen

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Populate a disposable install, confirm reset, relaunch and inspect records/photos/drafts/preferences.

**Expected:** Private app data is cleared as documented; external backups/provider originals are not silently deleted; relaunch is usable.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F02-QA07 — Release build smoke

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Build a minified release candidate, install only the isolated signed QA variant, capture/edit/search/open widgets and Review.

**Expected:** Startup, navigation, persistence and reflection-dependent paths work after shrinking; APK identity is recorded.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F02-QA08 — Final release ledger

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Review required results, rerun affected cases after each correction and invalidate outdated evidence.

**Expected:** No FAIL/BLOCKED/NOT RUN is reported as PASS; release acceptance remains pending until required obligations are satisfied.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

## Automated and manual ownership

Automated command/test-file ownership is in [the plan’s phase 2](../superpowers/plans/2026-10-26-roadmap-02-release-acceptance.md); no tests are asserted to exist before that phase. Add domain/state cases for pure rules, real Room cases for migration/atomicity, and UI/native cases for interaction. Fakes cannot establish native permission, biometric, provider, launcher, file-picker or shutdown behavior.

Manual/platform obligations: Complete CQA matrix, native permissions/provider fixtures, TalkBack, release smoke, pickers and reset on isolated devices.

Apply every relevant shared UI/platform matrix cell, not only the default-English emulator. For benchmarks use the shared 5-warmup/30-measurement protocol and report raw samples/p95; for task-specific failures retain the before-fix assertion and after-fix evidence.

## Execution ledger

Evidence directory during execution: `docs/feature-roadmap/evidence/<actual-run-date>/F02/`. Record source/build/environment identity once per run and link each row to its actual artifacts. Add matrix-cell rows as execution proceeds.

| Case | Result | Actual observation | Evidence | Defect/retest |
| --- | --- | --- | --- | --- |
| F02-QA01 | NOT RUN | — | — | — |
| F02-QA02 | NOT RUN | — | — | — |
| F02-QA03 | NOT RUN | — | — | — |
| F02-QA04 | NOT RUN | — | — | — |
| F02-QA05 | NOT RUN | — | — | — |
| F02-QA06 | NOT RUN | — | — | — |
| F02-QA07 | NOT RUN | — | — | — |
| F02-QA08 | NOT RUN | — | — | — |

## Exit gate

- [ ] All F02-R01–R05 assertions and scenarios mapped to actual tests/manual observations.
- [ ] Required host/build/lint/schema/instrumented gates pass for exact candidate; warnings and skips classified.
- [ ] Required native/manual/matrix/performance obligations complete with reproducible evidence.
- [ ] Defects corrected and affected results rerun; remaining blockers and invalidated old evidence explicit.
- [ ] Acceptance decision states exact completed scope. No unrun or blocked scenario is described as passing. For investigation-only outcome, feature implementation/runtime QA remains deferred explicitly.
