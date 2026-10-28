# Focus mode — tasks and delivery status

Date: 28 October 2026 · Feature: **F16** · Priority: **P1 (proposed)** · Status: **DRAFT — NOT IMPLEMENTED**

[Specification](superpowers/specs/2026-10-28-roadmap-16-focus-mode.md) · [Implementation plan](superpowers/plans/2026-10-28-roadmap-16-focus-mode.md) · [QA scenarios](feature-roadmap/16-focus-mode-qa.md) · [Roadmap](FEATURE_ROADMAP_2026-10-26.md) · [Shared workflow](feature-roadmap/WORKFLOW.md)

The user asked for focus-session storage, Today totals, weekly comparisons, allowed apps and a focus screen that cannot be left until Pause/Stop. This is the requested documentation package. All implementation and QA work is pending. The personal-phone proposal guards Luma navigation; **device-wide restriction remains unresolved in FM08**. Do not close the original restriction request by calling app shortcuts an enforced allowlist.

## Proposed defaults to carry into execution

- Count-up stopwatch; Pause/Resume/Stop; positive stopped time counts, pauses do not.
- One open session across windows/process restarts. Screen-off/background time counts while running; this records the user's running timer rather than verified attention.
- Saved reports contain ended/interrupted credited runs. In-progress time stays separate until Stop or recovery finalizes it.
- Settings starts with no selected apps. Selected-app shortcuts are an exception to Luma's navigation guard; Android controls remain available.
- Today totals split at local midnight. Review follows existing ISO weeks and matching-weekday comparisons, with a partial-current-period label.
- Terminal history is portable; runtime and selected packages remain device-local. No new network, usage, accessibility or device-admin permission for the personal-phone proposal.

## Task backlog

Each FM task follows exactly two phases: implementation/source handoff, then QA. No Gradle, tests or device use in implementation; tests and schema generation belong to QA under the shared workflow. FM08 has an assessment handoff and QA verification of its claims; it does not imply permission to provision a device.

| ID | Deliverable | Dependencies | Acceptance / QA | Implementation | QA |
| --- | --- | --- | --- | --- | --- |
| FM01 | Pure session state machine, timing and statistics contracts | None | Explicit transitions; pause exclusion; monotonic recovery; positive overlap totals; QA01–04, QA06–09, QA11 | PENDING | NOT RUN |
| FM02 | Room sessions/runs/runtime, singleton and command receipts | FM01 | Atomic durable/revision-safe writes; no duplicate credit; next available migration; QA02–04, QA09–11 | PENDING | NOT RUN |
| FM03 | Android clock/lifecycle coordinator and guarded focus screen | FM01–02 | Start/Pause/Resume/Stop, recreation, all Luma entry paths and emergency exit; QA01–04, QA12–13 | PENDING | NOT RUN |
| FM04 | Focus settings and selected-app shortcuts | FM03 | Empty default, narrow package discovery, stable selections, safe failed launch; QA05, QA12–13 | PENDING | NOT RUN |
| FM05 | Today Focus card, history and weekly comparisons | FM02–04 | Exact saved totals, distinct contributing counts, range drill-down, zero-baseline rules; QA06–09, QA12–14 | PENDING | NOT RUN |
| FM06 | Backup/legacy restore, replacement and reset integration | FM02–05; serialize with F01/F05 | Terminal history round-trip; no restored active restriction; no stale writes; QA10–11 | PENDING | NOT RUN |
| FM07 | Integrated source review and release QA | FM01–06 QA complete | Required cases/matrix/performance pass with evidence; truthful scope wording; QA01–14 | PENDING | NOT RUN |
| FM08 | Decide/prove strict device-wide enforcement scope | Can precede FM01; required before claiming original restriction complete | Compare supported personal-phone options vs managed-device DPC; retain explicit limit if deferred; QA15 | PENDING | NOT RUN |

## Sequence and boundaries

Recommended: resolve FM08 scope first if strict blocking is a release requirement; otherwise proceed FM01 → FM02 → FM03 → FM04 → FM05 → FM06 → FM07 for explicitly labeled personal-phone v1. FM08 can investigate independently of timer/report design. A decision to require managed-device enforcement revises the spec/plan and adds provisioned-device QA before implementation of that branch. No personal-device reprovisioning, factory reset, permission grant or device-owner setup is authorized by this package.

Source milestones do not equal feature acceptance. FM01/FM02 can complete their own QA without exposing a partial feature to users; keep new entry points gated until FM03–06 integrate. Do not release schema changes before exported schema/migration/legacy checks pass. Do not change original Txx/F01–F15 task IDs or mark any existing task complete.

## Completion checklist

- [ ] Proposed defaults and enforcement scope reconciled with the implementation request.
- [ ] FM01–06 each have a source handoff, QA outcomes and resolved defects.
- [ ] FM07 has an integrated acceptance record identifying exact package/build/fixture and matrix scope.
- [ ] FM08 records the selected approach and unresolved limitations; stronger blocking has its own revised implementation if selected.
- [ ] No full-request completion claim while the requested device-wide restriction is deferred.

## Execution ledger

| Date | Task | Source handoff / SHA and patch hash | QA evidence | Result |
| --- | --- | --- | --- | --- |
| — | FM01–08 | Not started | None | NOT RUN |
