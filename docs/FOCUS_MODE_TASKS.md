# Focus mode — tasks and delivery status

Date: 30 October 2026 · Feature: **F16** · Priority: **P1 (proposed)** · Status: **FM01 DOMAIN QA PASS; FM02 STORAGE/REPOSITORY QA PASS; FM03 SOURCE COMPLETE + SCOPED API36 QA PASS; FM04 SOURCE COMPLETE + SCOPED API36.1 QA PASS; FM05 IN PROGRESS; FM06–FM08 NOT STARTED; FULL MATRIX AND INTEGRATED QA PENDING**

[Specification](superpowers/specs/2026-10-28-roadmap-16-focus-mode.md) · [Implementation plan](superpowers/plans/2026-10-28-roadmap-16-focus-mode.md) · [QA scenarios](feature-roadmap/16-focus-mode-qa.md) · [Roadmap](FEATURE_ROADMAP_2026-10-26.md) · [Shared workflow](feature-roadmap/WORKFLOW.md)

The user asked for focus-session storage, Today totals, weekly comparisons, allowed apps and a focus screen that cannot be left until Pause/Stop. FM01's pure domain source handoff and host-domain QA are complete. FM02's Room schema/migration and repository QA passed for the storage scope; [the run evidence](feature-roadmap/evidence/2026-10-30/F16/fm02-room-qa.md) records the exact limits and two unrelated Todo failures in the full data-module connected suite. FM03's lifecycle owner, recovery handling, guarded screen and Today/More entry points have a source handoff; [scoped API 36.1 QA passed](feature-roadmap/evidence/2026-10-30/F16/fm03-qa.md), with the platform matrix and integrated feature QA still pending. FM04 now has device-local preferences, launcher discovery, settings and checkpointed selected-app handoff; [scoped host/API 36.1 QA passed](feature-roadmap/evidence/2026-10-30/F16/fm04-qa.md). Its real external-app fixture and broader platform/accessibility matrix remain open. FM05 has independent report streams; its UI, history and integration remain pending. FM06–FM08 implementation remains pending. The personal-phone proposal guards Luma navigation; **device-wide restriction remains unresolved in FM08**. Do not close the original restriction request by calling app shortcuts an enforced allowlist.

## Proposed defaults to carry into execution

- Count-up stopwatch; Pause/Resume/Stop; positive stopped time counts, pauses do not.
- One open session across windows/process restarts. Screen-off/background time counts while running; this records the user's running timer rather than verified attention.
- Saved reports contain ended/interrupted credited runs. In-progress time stays separate until Stop or recovery finalizes it.
- Settings starts with no selected apps. Selected-app shortcuts are an exception to Luma's navigation guard; Android controls remain available.
- Today totals split at local midnight. Review follows existing ISO weeks and matching-weekday comparisons, with a partial-current-period label.
- Terminal history is portable; runtime and selected packages remain device-local. No new network, usage, accessibility or device-admin permission for the personal-phone proposal.

## Task backlog

The user directed FM03 QA immediately after its own source handoff. This is a task-scoped QA run; remaining feature-wide QA stays pending until FM04–FM06 source handoffs are complete. Pending QA for an earlier task does not block implementation of a later task; source dependencies still apply. FM01 host-domain QA was already performed. No Gradle, tests or device use during implementation; tests and schema generation belong to the subsequent QA phase. FM08 has an assessment handoff and QA verification of its claims; it does not imply permission to provision a device.

| ID | Deliverable | Dependencies | Acceptance / QA | Implementation | QA |
| --- | --- | --- | --- | --- | --- |
| FM01 | Pure session state machine, timing and statistics contracts | None | Explicit transitions; pause exclusion; monotonic recovery; positive overlap totals; QA01–04, QA06–09, QA11 | SOURCE COMPLETE — QA FIX APPLIED | DOMAIN PASS; INTEGRATED QA BLOCKED |
| FM02 | Room sessions/runs/runtime, singleton and command receipts | FM01 | Atomic durable/revision-safe writes; no duplicate credit; next available migration; QA02–04, QA09–11 | SOURCE COMPLETE — QA INDEX FIX APPLIED | PASS — Room/repository and migration scope; see [FM02 QA evidence](feature-roadmap/evidence/2026-10-30/F16/fm02-room-qa.md). Integrated FM03/05/06 branches remain pending. |
| FM03 | Android clock/lifecycle coordinator and guarded focus screen | FM01–02 | Start/Pause/Resume/Stop, recreation, all Luma entry paths and emergency exit; QA01–04, QA12–13 | SOURCE COMPLETE | PASS — scoped API 36.1 unit/instrumentation QA; full platform matrix remains pending. See [FM03 QA evidence](feature-roadmap/evidence/2026-10-30/F16/fm03-qa.md). |
| FM04 | Focus settings and selected-app shortcuts | FM03 | Empty default, narrow package discovery, stable selections, safe failed launch; QA05, QA12–13 | SOURCE COMPLETE | SCOPED PASS — 297/297 host tests; 20/20 API 36.1 connected tests; lint exit 0. QA05/12/13 full scenario matrix remains partial; see [FM04 QA evidence](feature-roadmap/evidence/2026-10-30/F16/fm04-qa.md). |
| FM05 | Today Focus card, history and weekly comparisons | FM02–04 | Exact saved totals, distinct contributing counts, range drill-down, zero-baseline rules; QA06–09, QA12–14 | IN PROGRESS — REPORT STREAMS | NOT RUN |
| FM06 | Backup/legacy restore, replacement and reset integration | FM02–05; serialize with F01/F05 | Terminal history round-trip; no restored active restriction; no stale writes; QA10–11 | PENDING | NOT RUN |
| FM07 | Integrated source review and release QA | FM01–06 source complete; QA after all implementation | Required cases/matrix/performance pass with evidence; truthful scope wording; QA01–14 | PENDING | NOT RUN |
| FM08 | Decide/prove strict device-wide enforcement scope | Can precede FM01; required before claiming original restriction complete | Compare supported personal-phone options vs managed-device DPC; retain explicit limit if deferred; QA15 | PENDING | NOT RUN |

## Sequence and boundaries

Recommended: resolve FM08 scope first if strict blocking is a release requirement; otherwise proceed FM01 → FM02 → FM03 → FM04 → FM05 → FM06 → FM07 for explicitly labeled personal-phone v1. FM08 can investigate independently of timer/report design. A decision to require managed-device enforcement revises the spec/plan and adds provisioned-device QA before implementation of that branch. No personal-device reprovisioning, factory reset, permission grant or device-owner setup is authorized by this package.

Source milestones do not equal feature acceptance. The user requested FM02 storage QA before all F16 implementation handoffs; the result is limited to Room/repository scope. Begin the remaining integrated QA only after all implementation handoffs; keep new entry points gated until FM03–06 integrate. Do not release schema changes before exported schema/migration/legacy checks pass. Do not change original Txx/F01–F15 task IDs or mark any existing task complete.

## Completion checklist

- [ ] Proposed defaults and enforcement scope reconciled with the implementation request.
- [ ] FM01–06 each have a source handoff, QA outcomes and resolved defects.
- [ ] FM07 has an integrated acceptance record identifying exact package/build/fixture and matrix scope.
- [ ] FM08 records the selected approach and unresolved limitations; stronger blocking has its own revised implementation if selected.
- [ ] No full-request completion claim while the requested device-wide restriction is deferred.

## Execution ledger

| Date | Task | Source handoff / SHA and patch hash | QA evidence | Result |
| --- | --- | --- | --- | --- |
| 29 Oct 2026 | FM01 source | Base `7beffaf`; source patch SHA-256 `84de2cabaaaca3e105efb059ae229f0ac162fcf8a6f2b569e736f5a9d89eed50` | Source-only checks at handoff | SOURCE COMPLETE |
| 30 Oct 2026 | FM01 QA | Candidate base `94490ad`; QA source/test patch recorded in [FM01 QA evidence](feature-roadmap/evidence/2026-10-30/F16/fm01-domain-qa.md) | 19 new host tests; all 157 domain tests passed, 0 failures/errors/skips | DOMAIN PASS — integrated cases remain BLOCKED |
| 30 Oct 2026 | FM02 source | Base `bf95757`; production patch SHA-256 `a9d5f63b479b8dae184f696b850d843778d037aab8470ddcb0009f6d2b0c1871` | Source checks pass; compile attempt blocked before compiler; no tests/schema-export/device work | SOURCE COMPLETE — QA PENDING |
| 30 Oct 2026 | FM02 QA | Candidate base `a0c22f3749c2f6e705659b734b9e660a4f943295`; QA code/schema SHA-256 `002b351ddfd9b9d40da8357a44db11d16bf4ddacd3c423f1715e89f4db38a936` | 29/29 host tests; 9/9 focused FM02 instrumentation; populated v7→v8 and existing v1→v8 migrations pass. Full 172-test connected suite has 2 non-FM02 Todo failures. [Evidence](feature-roadmap/evidence/2026-10-30/F16/fm02-room-qa.md). | FM02 STORAGE/REPOSITORY QA PASS; integrated cases remain BLOCKED |
| 30 Oct 2026 | FM03 source | Base `4472f5f89d4586795b3759ac8454d021d44c5ff4`; production patch SHA-256 `8a3f2f1538663fc27facb1a7581eebbf273009dd79b2cb85a3a7c2860e2c1f9e` | Lifecycle/runtime, guarded UI/navigation, emergency marker/recovery and localized Today/More entry wiring; `git diff --check` and 3-string XML parse pass; no test, Gradle or device work in this phase | SOURCE COMPLETE — QA PENDING |
| 30 Oct 2026 | FM03 QA | Candidate base `4472f5f89d4586795b3759ac8454d021d44c5ff4`; code/config/test patch SHA-256 `461eb72ef9df56c0c540b9a2ea25a83fe29dd9190b45ebed1d77f8287439d2c6` | 293/293 app host tests; 6/6 focused connected tests on isolated `focusQaDebug` / API 36.1; lint exit 0, no warnings in changed FM03 production files. See [FM03 QA evidence](feature-roadmap/evidence/2026-10-30/F16/fm03-qa.md). | SCOPED API36 QA PASS; native/platform matrix and integrated F16 QA remain pending |
| 30 Oct 2026 | FM04 source | Base `0c3ae4cc055188818586253a7e210b8d96be675c`; source and QA changes remain uncommitted | Device-local empty-default preferences, searchable launcher picker, stale choice removal, guarded checkpointed handoff/retry, localized scope guidance and FM03 screen integration. | SOURCE COMPLETE |
| 30 Oct 2026 | FM04 QA | Candidate base `0c3ae4cc055188818586253a7e210b8d96be675c` | 297/297 app host tests; 20/20 focused connected tests on `focusQaDebug` / Medium Phone API 36.1; lint exit 0; exact commands and limits in [FM04 QA evidence](feature-roadmap/evidence/2026-10-30/F16/fm04-qa.md). | SCOPED QA PASS; actual external fixture launch/return and full platform/accessibility matrix remain pending |

Suggested commit message: `feat(focus): add revision-safe Room session storage`

Suggested commit message for FM02 QA: `test(focus): verify Room durability, recovery and migrations`

FM03 implementation: `FocusRuntime` owns reconciliation, one foreground checkpoint loop, background checkpoints, app-wide session state and emergency preference markers; `FocusViewModel` retains stable retry operations; `FocusRoute`/`FocusScreen` expose loading, recovery, running, paused, terminal, retry and emergency states. `LifeOsApp` gates navigation, retains one explicit pending destination and wires widget/reminder/tab routes; Today and More expose Focus. QA corrected startup-time emergency exit, kept elapsed-time accounting active after emergency release, exposed emergency exit in the healthy running screen, and waited for durable Pause/Stop success before leaving the Back prompt. The Android clock and coordinator use wall, elapsed-realtime and best-effort boot-count samples. Focused host, UI and runtime-recovery checks passed on API 36.1; OS-level death/reboot/sleep, storage fault injection, API 28 and accessibility/localization/window matrices remain open in [FM03 QA evidence](feature-roadmap/evidence/2026-10-30/F16/fm03-qa.md). Suggested commit message: `feat(focus): add guarded session lifecycle and recovery`.

FM04: added device-local package selection and current-profile launcher discovery with the existing narrow manifest query, a Settings → Focus mode picker, stale selection removal, and a checkpointed/revalidated explicit shortcut handoff with retry. Focus-only shortcuts do not restrict Android/system controls or apps opened elsewhere. Scoped host, API 36.1 connected QA and lint passed; see [FM04 evidence](feature-roadmap/evidence/2026-10-30/F16/fm04-qa.md). External fixture launch/return and the full locale/theme/window/accessibility/API matrix remain pending. Suggested commit message: `feat(focus): add settings and safe selected-app shortcuts`.

FM05 progress: added independent day and weekly report streams using repository statistics and domain comparison rules. Today/Review cards, history, deletion, navigation, localization and integration remain outstanding. No QA was run.
