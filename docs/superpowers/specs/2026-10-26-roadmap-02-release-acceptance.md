# F02 — Current release and Calendar acceptance specification

Date: 26 October 2026 · Priority: **P0** · Status: **DRAFT — NOT IMPLEMENTED / NOT VALIDATED**

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](2026-10-26-roadmap-02-release-acceptance.md) · [Plan](../plans/2026-10-26-roadmap-02-release-acceptance.md) · [QA scenarios](../../feature-roadmap/02-release-acceptance-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Intent and dependencies

Produce current, revision-bound release acceptance for existing features and the tasks-and-memories Calendar.

Dependencies: Task 01 for recovery release evidence; coordinate defects with task 07.

Rank and scope come from [the feature proposal](../../FEATURE_IMPROVEMENT_PROPOSAL_2026-10-26.md). Specific new defaults in this document are proposed implementation choices, not claims of user approval. Read the existing implementation at execution time; this document does not certify its current runtime behavior.

## Architecture and alternatives

Recommended approach: Use the existing Calendar QA package as the authoritative behavioral baseline. Consolidate outstanding checks into one evidence ledger; do not create a second Calendar implementation or change its membership to satisfy tests.

Considered alternatives: retain the current behavior and document its limitations, or introduce an independent subsystem. Retaining behavior is acceptable only for an explicitly documented investigation/deferred outcome; otherwise it does not meet the intent above. An independent replacement would duplicate existing persistence/navigation and recovery paths; extend the current module contracts unless a specific requirement makes reuse impossible. The recommendation is proposed, not a recorded user selection.

## Existing source and handoff material

- [docs/CALENDAR_QA.md](../../CALENDAR_QA.md)
- [docs/CALENDAR_TASKS.md](../../CALENDAR_TASKS.md)
- [docs/validation/2026-10-25-calendar-agenda.md](../../validation/2026-10-25-calendar-agenda.md)
- [docs/TODO_ACCEPTANCE_2026-10-19.md](../../TODO_ACCEPTANCE_2026-10-19.md)
- [docs/VALIDATION.md](../../VALIDATION.md)
- [.github/workflows/android.yml](../../../.github/workflows/android.yml)
- [app/src/main/java/com/luma/lifeos/ui/calendar/CalendarViewModel.kt](../../../app/src/main/java/com/luma/lifeos/ui/calendar/CalendarViewModel.kt)
- [app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt](../../../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt)



## Contracts and ownership

Names below are proposed interfaces to create or adapt, not claims that these declarations already exist. Keep existing public callers working or migrate every caller explicitly in the owning change. Resolve exact current types while preserving these behaviors.

- Evidence rows contain scenario ID, source revision plus dirty patch hash, build variant, device/API, fixture ID, command/action, expected/actual outcome and artifact link.
- Reuse CQA-01 through CQA-32 identifiers and their complete expected results; this package adds a release rollup, not replacement abbreviated checks.

## Requirements

### F02-R01 — Scope freeze

Record the candidate source identity, app version, dirty files and test isolation before any QA run. Existing user metadata/APK edits remain outside scope unless deliberately part of the candidate.

### F02-R02 — Calendar contract

Preserve Today → Timeline → Calendar → Review → More. Calendar includes dated open tasks and memory-marked records only; habits and ordinary journals/moods remain excluded. Execute all 32 CQA scenarios without dropping matrix obligations.

### F02-R03 — Historical failures

Reproduce older Timeline ordering/page failures against the candidate. Record current passes with exact fixtures or fix a reproduced defect; a historical result neither certifies nor condemns the current build.

### F02-R04 — Critical platform flows

Verify optional permission grant/denial/revocation, integration refresh, photo/file pickers, reset/reopen, and minified-release startup using isolated synthetic data.

### F02-R05 — Release decision

Every required case has PASS, FAIL, BLOCKED or NOT RUN. Critical data/navigation/privacy failures and incomplete required scenarios block acceptance. Any lower-risk waiver must identify owner, reason, scope and expiry; the executor cannot silently self-waive.

## Two-phase delivery contract

**Phase 1 — Implementation:** implement the source/configuration/resources/documentation described in the plan, review the source and diff, and record the handoff. **No Gradle command, sync, dependency resolution, build, schema generation, lint, automated test execution, emulator launch or device test. Test authoring is deferred to QA.** Source checks do not prove compilation or correctness.

**Phase 2 — QA:** author tests/fixtures, generate and inspect schemas where relevant, run Gradle/build/lint/automated suites, execute native/manual cases, correct discovered defects and record evidence. Begin only after this task’s implementation source handoff; all initial outcomes are NOT RUN. Follow the [shared workflow](../../feature-roadmap/WORKFLOW.md) for isolation, matrix, commands, evidence and completion gates.

## Scope boundaries

No new feature scope, Calendar habit membership, or new navigation redesign.

## Acceptance and traceability

All five requirements must map to implementation steps and QA outcomes. [The QA document](../../feature-roadmap/02-release-acceptance-qa.md) provides executable scenarios with fixtures/actions/oracles; [the plan](../plans/2026-10-26-roadmap-02-release-acceptance.md) owns concrete files and implementation/QA checklists. Both phases start pending. A source-complete task is not feature-complete. For assessment/deferred branches, report the delivered investigation separately from feature/runtime acceptance.
