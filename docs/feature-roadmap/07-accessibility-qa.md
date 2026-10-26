# F07 — Accessibility and adaptive layout gaps QA scenarios

Date: 26 October 2026 · Status: **NOT RUN** · Priority inherited from task: **P1**

[Roadmap](../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../superpowers/specs/2026-10-26-roadmap-07-accessibility.md) · [Plan](../superpowers/plans/2026-10-26-roadmap-07-accessibility.md) · [QA scenarios](07-accessibility-qa.md) · [Shared workflow](WORKFLOW.md)

## Entry gate and scope

This document is **phase 2** of a two-phase task. Begin only after F07-I01–I05 are source complete with a recorded handoff. Test authoring, Gradle/sync/build/lint/schema generation, automated execution, emulator/device and manual checks occur here only. The documentation generation session did not execute these scenarios.

Read [WORKFLOW.md](WORKFLOW.md) for isolation, exact result semantics, required locale/theme/window/API matrix, automated command catalogue and performance protocol. Read the entire specification before deriving test assertions. All cases require task-specific preconditions; do not mark irrelevant branch scenarios PASS—use N/A only with a source-supported scope rationale, and do not turn a deferred investigation into shipped-feature acceptance.

## Fixture

F07: long mixed Persian/English task/habit/person names; 20 photos; journal at existing size bounds; validation/save error; 123-row agenda; empty and partial-data Review. Required window/font/locale/theme matrix is defined in WORKFLOW.md; actual widgets require launcher-host cases.

Freeze clocks through injection for automation; use dedicated AVDs for manual time/zone/credential changes. Record fixture seed/hash and expected IDs/values independently from production predicates. Reset baseline between independent scenarios. Never substitute real personal records, credentials or health data.

## Coverage obligations

The mapping below identifies the scenarios responsible for each requirement; cases may cover several requirements. During QA add assertion-level mappings and authored test names without dropping any requirement or shared matrix obligation.

| Requirement | Scenario coverage set | Initial result |
| --- | --- | --- |
| F07-R01 — Reachability | F07-QA01, F07-QA06, F07-QA07 | NOT RUN |
| F07-R02 — Semantics and focus | F07-QA02, F07-QA03 | NOT RUN |
| F07-R03 — Input and contrast | F07-QA03, F07-QA05 | NOT RUN |
| F07-R04 — Locale and adaptation | F07-QA04, F07-QA06, F07-QA07 | NOT RUN |
| F07-R05 — Coverage and evidence | F07-QA02, F07-QA07, F07-QA08 | NOT RUN |

## Scenarios

### F07-QA01 — Editor and IME

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** At 320 dp/200% fonts focus final field with IME visible, scroll, save/cancel and correct an error.

**Expected:** Essential actions and errors are reachable, focus is visible, content is retained and no action overlaps system/IME bounds.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F07-QA02 — TalkBack core journey

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Capture a mood/journal, complete and undo task/habit, navigate Calendar and open search result with TalkBack.

**Expected:** Logical labels/roles/states, no duplicate speech, single save/error announcement and correct focus return.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F07-QA03 — Keyboard and dialogs

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Use external keyboard/D-pad only through pickers, optional sections, dialogs and destructive confirmations.

**Expected:** Every touch action is keyboard-operable; focus order follows reading order and Escape/Back/cancel restores caller focus.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F07-QA04 — RTL and locale changes

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Use EN/FR/FA with mixed-direction text and change language while editing and browsing a selected date.

**Expected:** No clipped leading glyphs, mirrored-time corruption or lost fields; stored dates and IDs unchanged.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F07-QA05 — Contrast and targets

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Measure light/dark screenshots and hit bounds for disabled, selected, error and normal controls.

**Expected:** Targets >=48 dp and specified contrast ratios hold; status remains understandable without color.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F07-QA06 — Resize and orientation

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Rotate populated editor and resize 411→840 dp while Calendar/search is scrolled.

**Expected:** Draft/date/filter/scroll state preserved; sensible reading width and reachable final actions without duplicated panes.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F07-QA07 — Widgets

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Place small/large habit/task/capture widgets in actual launcher with font/locale/theme changes.

**Expected:** No clipped names/status or unreachable actions; summaries/private-detail choices and accessible click targets remain correct.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F07-QA08 — Regression and evidence

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Exercise existing record values/routes and collect before/after artifacts per finding.

**Expected:** Every confirmed issue has regression coverage and evidence; semantics-only passes do not claim TalkBack or launcher acceptance.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

## Automated and manual ownership

Automated command/test-file ownership is in [the plan’s phase 2](../superpowers/plans/2026-10-26-roadmap-07-accessibility.md); no tests are asserted to exist before that phase. Add domain/state cases for pure rules, real Room cases for migration/atomicity, and UI/native cases for interaction. Fakes cannot establish native permission, biometric, provider, launcher, file-picker or shutdown behavior.

Manual/platform obligations: Human TalkBack, external keyboard, contrast measurement, IME and actual launcher matrix.

Apply every relevant shared UI/platform matrix cell, not only the default-English emulator. For benchmarks use the shared 5-warmup/30-measurement protocol and report raw samples/p95; for task-specific failures retain the before-fix assertion and after-fix evidence.

## Execution ledger

Evidence directory during execution: `docs/feature-roadmap/evidence/<actual-run-date>/F07/`. Record source/build/environment identity once per run and link each row to its actual artifacts. Add matrix-cell rows as execution proceeds.

| Case | Result | Actual observation | Evidence | Defect/retest |
| --- | --- | --- | --- | --- |
| F07-QA01 | NOT RUN | — | — | — |
| F07-QA02 | NOT RUN | — | — | — |
| F07-QA03 | NOT RUN | — | — | — |
| F07-QA04 | NOT RUN | — | — | — |
| F07-QA05 | NOT RUN | — | — | — |
| F07-QA06 | NOT RUN | — | — | — |
| F07-QA07 | NOT RUN | — | — | — |
| F07-QA08 | NOT RUN | — | — | — |

## Exit gate

- [ ] All F07-R01–R05 assertions and scenarios mapped to actual tests/manual observations.
- [ ] Required host/build/lint/schema/instrumented gates pass for exact candidate; warnings and skips classified.
- [ ] Required native/manual/matrix/performance obligations complete with reproducible evidence.
- [ ] Defects corrected and affected results rerun; remaining blockers and invalidated old evidence explicit.
- [ ] Acceptance decision states exact completed scope. No unrun or blocked scenario is described as passing. For investigation-only outcome, feature implementation/runtime QA remains deferred explicitly.
