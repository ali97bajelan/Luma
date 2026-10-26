# F11 — Reusable journal capture templates QA scenarios

Date: 26 October 2026 · Status: **NOT RUN** · Priority inherited from task: **P2**

[Roadmap](../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../superpowers/specs/2026-10-26-roadmap-11-capture-templates.md) · [Plan](../superpowers/plans/2026-10-26-roadmap-11-capture-templates.md) · [QA scenarios](11-capture-templates-qa.md) · [Shared workflow](WORKFLOW.md)

## Entry gate and scope

This document is **phase 2** of a two-phase task. Begin only after F11-I01–I05 are source complete with a recorded handoff. Test authoring, Gradle/sync/build/lint/schema generation, automated execution, emulator/device and manual checks occur here only. The documentation generation session did not execute these scenarios.

Read [WORKFLOW.md](WORKFLOW.md) for isolation, exact result semantics, required locale/theme/window/API matrix, automated command catalogue and performance protocol. Read the entire specification before deriving test assertions. All cases require task-specific preconditions; do not mark irrelevant branch scenarios PASS—use N/A only with a source-supported scope rationale, and do not turn a deferred investigation into shipped-feature acceptance.

## Fixture

F11: empty draft, populated title/body/tag draft, draft with 2 photos/person/place and explicit past timestamp, near-limit journal body, 50 tags, templates with same name/different IDs, translated starter and custom Persian template.

Freeze clocks through injection for automation; use dedicated AVDs for manual time/zone/credential changes. Record fixture seed/hash and expected IDs/values independently from production predicates. Reset baseline between independent scenarios. Never substitute real personal records, credentials or health data.

## Coverage obligations

The mapping below identifies the scenarios responsible for each requirement; cases may cover several requirements. During QA add assertion-level mappings and authored test names without dropping any requirement or shared matrix obligation.

| Requirement | Scenario coverage set | Initial result |
| --- | --- | --- |
| F11-R01 — Template content | F11-QA01, F11-QA02 | NOT RUN |
| F11-R02 — Safe application | F11-QA03, F11-QA04 | NOT RUN |
| F11-R03 — Context and validation | F11-QA04, F11-QA05 | NOT RUN |
| F11-R04 — Starter templates | F11-QA06 | NOT RUN |
| F11-R05 — Persistence and navigation | F11-QA01, F11-QA05, F11-QA07, F11-QA08 | NOT RUN |

## Scenarios

### F11-QA01 — Template CRUD

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Create duplicate-name templates, update with stale revision, delete one and reopen offline.

**Expected:** Independent IDs retained, stale write rejected and retry safe; deleting one does not remove other template or journals.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F11-QA02 — Empty apply and save

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Apply valid template to empty draft, modify body and save journal.

**Expected:** Snapshot fields copied with current draft date/context; one normal journal saved; template remains unchanged.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F11-QA03 — Populated draft modes

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Try Cancel, Append and Replace on populated draft.

**Expected:** Cancel leaves exact original; Append uses two newlines/title rule; Replace explicit; tags merged or operation rejected atomically.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F11-QA04 — Limits and Undo

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Apply exceeding body/tag limits; then valid apply and Undo before/after a text edit.

**Expected:** No truncation or partial application; Undo restores previous snapshot only while available; subsequent edit invalidates stale undo.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F11-QA05 — Context and recreation

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Apply with photos/person/place/past date, open template editor, rotate and return.

**Expected:** All parent context/identity and unsaved fields survive; template editor does not save a journal.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F11-QA06 — Starter localization

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Inspect/install EN/FR/FA starter, edit it then change app language.

**Expected:** Current-language bundled text shown; install explicit; user-edited copy never silently retranslated or duplicated.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F11-QA07 — Saved entry independence

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Save from template, edit/delete template and reopen journal.

**Expected:** Journal body/title/tags unchanged; no live template reference corrupts existing entry.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F11-QA08 — Archives and accessibility

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Round-trip custom templates and restore legacy archive; use picker at 200% fonts/RTL with IME.

**Expected:** Custom content preserved in new archives, none from old; all controls/readable prompts reachable; failures retain draft.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

## Automated and manual ownership

Automated command/test-file ownership is in [the plan’s phase 2](../superpowers/plans/2026-10-26-roadmap-11-capture-templates.md); no tests are asserted to exist before that phase. Add domain/state cases for pure rules, real Room cases for migration/atomicity, and UI/native cases for interaction. Fakes cannot establish native permission, biometric, provider, launcher, file-picker or shutdown behavior.

Manual/platform obligations: Populated-draft dialogs, IME, RTL starter text and parent picker/editor lifecycle.

Apply every relevant shared UI/platform matrix cell, not only the default-English emulator. For benchmarks use the shared 5-warmup/30-measurement protocol and report raw samples/p95; for task-specific failures retain the before-fix assertion and after-fix evidence.

## Execution ledger

Evidence directory during execution: `docs/feature-roadmap/evidence/<actual-run-date>/F11/`. Record source/build/environment identity once per run and link each row to its actual artifacts. Add matrix-cell rows as execution proceeds.

| Case | Result | Actual observation | Evidence | Defect/retest |
| --- | --- | --- | --- | --- |
| F11-QA01 | NOT RUN | — | — | — |
| F11-QA02 | NOT RUN | — | — | — |
| F11-QA03 | NOT RUN | — | — | — |
| F11-QA04 | NOT RUN | — | — | — |
| F11-QA05 | NOT RUN | — | — | — |
| F11-QA06 | NOT RUN | — | — | — |
| F11-QA07 | NOT RUN | — | — | — |
| F11-QA08 | NOT RUN | — | — | — |

## Exit gate

- [ ] All F11-R01–R05 assertions and scenarios mapped to actual tests/manual observations.
- [ ] Required host/build/lint/schema/instrumented gates pass for exact candidate; warnings and skips classified.
- [ ] Required native/manual/matrix/performance obligations complete with reproducible evidence.
- [ ] Defects corrected and affected results rerun; remaining blockers and invalidated old evidence explicit.
- [ ] Acceptance decision states exact completed scope. No unrun or blocked scenario is described as passing. For investigation-only outcome, feature implementation/runtime QA remains deferred explicitly.
