# F06 — Optional app lock and private previews QA scenarios

Date: 26 October 2026 · Status: **NOT RUN** · Priority inherited from task: **P1**

[Roadmap](../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../superpowers/specs/2026-10-26-roadmap-06-app-lock.md) · [Plan](../superpowers/plans/2026-10-26-roadmap-06-app-lock.md) · [QA scenarios](06-app-lock-qa.md) · [Shared workflow](WORKFLOW.md)

## Entry gate and scope

This document is **phase 2** of a two-phase task. Begin only after F06-I01–I05 are source complete with a recorded handoff. Test authoring, Gradle/sync/build/lint/schema generation, automated execution, emulator/device and manual checks occur here only. The documentation generation session did not execute these scenarios.

Read [WORKFLOW.md](WORKFLOW.md) for isolation, exact result semantics, required locale/theme/window/API matrix, automated command catalogue and performance protocol. Read the entire specification before deriving test assertions. All cases require task-specific preconditions; do not mark irrelevant branch scenarios PASS—use N/A only with a source-supported scope rationale, and do not turn a deferred investigation into shipped-feature acceptance.

## Fixture

F06: synthetic journal draft with photo and selected person, locked/unlocked app states, three timeout options, queued widget/task-notification destinations, credential-enabled and credential-absent devices, API 28 and API 30+ authentication paths.

Freeze clocks through injection for automation; use dedicated AVDs for manual time/zone/credential changes. Record fixture seed/hash and expected IDs/values independently from production predicates. Reset baseline between independent scenarios. Never substitute real personal records, credentials or health data.

## Coverage obligations

The mapping below identifies the scenarios responsible for each requirement; cases may cover several requirements. During QA add assertion-level mappings and authored test names without dropping any requirement or shared matrix obligation.

| Requirement | Scenario coverage set | Initial result |
| --- | --- | --- |
| F06-R01 — Enable and recovery | F06-QA01, F06-QA05 | NOT RUN |
| F06-R02 — Lifecycle and entry points | F06-QA02, F06-QA03 | NOT RUN |
| F06-R03 — Draft preservation | F06-QA03, F06-QA04 | NOT RUN |
| F06-R04 — Preview privacy | F06-QA06, F06-QA07 | NOT RUN |
| F06-R05 — External surfaces and restore | F06-QA07, F06-QA08 | NOT RUN |

## Scenarios

### F06-QA01 — Enable and disable

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Try enable without credential, cancel authentication, enable successfully and disable with cancelled/successful auth.

**Expected:** No unauthenticated toggle; no-credential state explains setup; only successful auth changes protection.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F06-QA02 — Timeout boundaries

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Background for timeout−1 ms and timeout exactly; repeat with immediate, 1-minute and 5-minute settings; cold-kill/relaunch.

**Expected:** Monotonic boundary is exact; cold process always locks when enabled; wall-clock change cannot extend unlock.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F06-QA03 — All entry points

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Launch from icon, task widget, quick capture and notification while locked, recording first rendered frames; also tap widget Complete and notification Log/Complete, cancel auth, then retry after source edit and with valid command.

**Expected:** No private content flashes; auth precedes route; cancelled auth never mutates; stale/expired commands fail after auth; valid command is consumed once with no double completion/log.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F06-QA04 — Draft and prompt lifecycle

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Lock a populated editor, rotate during prompt, cancel/retry, then resume.

**Expected:** Fields/photos/context survive; no prompt loop or duplicate save; interrupted process requests new auth.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F06-QA05 — Credential change and lockout

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Change/remove device credential and trigger biometric lockout on isolated hardware.

**Expected:** Supported device-credential fallback works; absent credential remains gated with setup/reset recovery, without silent unlock.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F06-QA06 — Previews and screenshots

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Open private data then Recents while enabled; toggle screenshot protection and attempt screenshots.

**Expected:** Recent snapshot is masked; screenshot policy matches switch; unlocked screenshot capability does not unmask Recents.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F06-QA07 — Widgets and notifications

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Inspect detailed/summary widgets and queued reminders before/after locking and hide-details action.

**Expected:** Generic newly posted locked notifications; widget independence explained, explicit hide hides details; no claim cached launcher copies are erased.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F06-QA08 — Restore and reset

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Restore a backup from a locked source onto another device; separately confirm local reset.

**Expected:** No auth state or credential transfers; local destination policy remains coherent; reset removes lock prefs and private drafts as documented.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

## Automated and manual ownership

Automated command/test-file ownership is in [the plan’s phase 2](../superpowers/plans/2026-10-26-roadmap-06-app-lock.md); no tests are asserted to exist before that phase. Add domain/state cases for pure rules, real Room cases for migration/atomicity, and UI/native cases for interaction. Fakes cannot establish native permission, biometric, provider, launcher, file-picker or shutdown behavior.

Manual/platform obligations: Real biometric/device-credential UI on API 28 and modern hardware, Recents snapshots, screenshots, widget and notification launches.

Apply every relevant shared UI/platform matrix cell, not only the default-English emulator. For benchmarks use the shared 5-warmup/30-measurement protocol and report raw samples/p95; for task-specific failures retain the before-fix assertion and after-fix evidence.

## Execution ledger

Evidence directory during execution: `docs/feature-roadmap/evidence/<actual-run-date>/F06/`. Record source/build/environment identity once per run and link each row to its actual artifacts. Add matrix-cell rows as execution proceeds.

| Case | Result | Actual observation | Evidence | Defect/retest |
| --- | --- | --- | --- | --- |
| F06-QA01 | NOT RUN | — | — | — |
| F06-QA02 | NOT RUN | — | — | — |
| F06-QA03 | NOT RUN | — | — | — |
| F06-QA04 | NOT RUN | — | — | — |
| F06-QA05 | NOT RUN | — | — | — |
| F06-QA06 | NOT RUN | — | — | — |
| F06-QA07 | NOT RUN | — | — | — |
| F06-QA08 | NOT RUN | — | — | — |

## Exit gate

- [ ] All F06-R01–R05 assertions and scenarios mapped to actual tests/manual observations.
- [ ] Required host/build/lint/schema/instrumented gates pass for exact candidate; warnings and skips classified.
- [ ] Required native/manual/matrix/performance obligations complete with reproducible evidence.
- [ ] Defects corrected and affected results rerun; remaining blockers and invalidated old evidence explicit.
- [ ] Acceptance decision states exact completed scope. No unrun or blocked scenario is described as passing. For investigation-only outcome, feature implementation/runtime QA remains deferred explicitly.
