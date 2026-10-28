# Luma — Feature roadmap specifications, plans and QA

Date: 26 October 2026 · Source: [prioritized proposal](FEATURE_IMPROVEMENT_PROPOSAL_2026-10-26.md)

This package contains **15 specifications, 15 implementation plans and 15 QA scenario documents**. F01–F14 follow the proposal’s importance ranking; F15 was added on 28 October for the requested memory/mood sharing feature, with proposed priority P2. F04 and F07 source phases are complete. On 27 October, F04 passed the domain and app/data host suites, all three focused reminder instrumentation tests on `Medium_Phone_API_36.1`, and manual notification permission/channel, scheduled task delivery and Complete-action checks. Full F04 acceptance remains incomplete; one unrelated activity lifecycle test failed in the wider connected suite, and several F04 scenarios remain unrun. F07 and the remaining tasks have not entered QA.

## Required two-phase workflow

Each task has exactly two phases:

1. **Implementation:** production/configuration/resource/documentation work and source review only. **No Gradle commands, sync, dependency resolution, schema generation, build, lint, test execution or emulator/device use. Test authoring is deferred to QA.** Exit: SOURCE COMPLETE — QA PENDING.
2. **QA:** test/fixture authoring, Gradle/build/lint/schema generation, automated and native/manual execution, defect corrections/retests, and acceptance evidence. Entry: that task’s implementation handoff. Exit: required validated scope passes, or explicit unresolved/deferred status.

Run these phases per task; this does not require all implementations before any QA. [Shared workflow](feature-roadmap/WORKFLOW.md) defines isolation, matrix, evidence, commands, schema-version coordination and status rules. Existing plans that put tests in implementation are superseded only for execution of these roadmap tasks. Current accepted product behaviors remain authoritative.

New defaults in the specs are proposed choices for later review, not prior user decisions. This package generation did not begin product implementation or authorize a release. F04's source handoff and focused QA results are recorded in its implementation plan and QA ledger; full acceptance remains incomplete.

F04 source follow-up on 27 October adds notification access and recovery controls to Reminders settings and corrects the alarm/action broadcast payload. Its source handoff is recorded in the F04 plan; focused Android and manual checks are recorded in the F04 QA ledger, with remaining acceptance gaps listed there.

F15 documentation added on 28 October proposes single saved memory/mood sharing as editable plain text through Android's system share sheet. Image cards and photo sharing are deferred. This addition leaves F12's journal/memory file-export scope unchanged; source implementation is complete; all eight F15 QA scenarios are NOT RUN.

## Task packages

| Task | Priority | Scope | Specification | Implementation plan | QA | Implementation | QA status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F01 | P0 | Backup and restore resilience | [Spec](superpowers/specs/2026-10-26-roadmap-01-backup-resilience.md) | [Plan](superpowers/plans/2026-10-26-roadmap-01-backup-resilience.md) | [QA scenarios](feature-roadmap/01-backup-resilience-qa.md) | PENDING | NOT RUN |
| F02 | P0 | Current release and Calendar acceptance | [Spec](superpowers/specs/2026-10-26-roadmap-02-release-acceptance.md) | [Plan](superpowers/plans/2026-10-26-roadmap-02-release-acceptance.md) | [QA scenarios](feature-roadmap/02-release-acceptance-qa.md) | PENDING | NOT RUN |
| F03 | P1 | Fast search across entry content | [Spec](superpowers/specs/2026-10-26-roadmap-03-search.md) | [Plan](superpowers/plans/2026-10-26-roadmap-03-search.md) | [QA scenarios](feature-roadmap/03-search-qa.md) | PENDING | NOT RUN |
| F04 | P1 | Automatic task and optional habit reminders | [Spec](superpowers/specs/2026-10-26-roadmap-04-reminders.md) | [Plan](superpowers/plans/2026-10-26-roadmap-04-reminders.md) | [QA scenarios](feature-roadmap/04-reminders-qa.md) | SOURCE COMPLETE — QA PENDING | PARTIAL: QA01/QA03/QA05/QA06 partial; QA02/QA04/QA07/QA08 NOT RUN |
| F05 | P1 | Password-protected portable backups | [Spec](superpowers/specs/2026-10-26-roadmap-05-encrypted-backups.md) | [Plan](superpowers/plans/2026-10-26-roadmap-05-encrypted-backups.md) | [QA scenarios](feature-roadmap/05-encrypted-backups-qa.md) | PENDING | NOT RUN |
| F06 | P1 | Optional app lock and private previews | [Spec](superpowers/specs/2026-10-26-roadmap-06-app-lock.md) | [Plan](superpowers/plans/2026-10-26-roadmap-06-app-lock.md) | [QA scenarios](feature-roadmap/06-app-lock-qa.md) | PENDING | NOT RUN |
| F07 | P1 | Accessibility and adaptive layout gaps | [Spec](superpowers/specs/2026-10-26-roadmap-07-accessibility.md) | [Plan](superpowers/plans/2026-10-26-roadmap-07-accessibility.md) | [QA scenarios](feature-roadmap/07-accessibility-qa.md) | SOURCE COMPLETE — QA PENDING | NOT RUN |
| F08 | P2 | Recurring tasks | [Spec](superpowers/specs/2026-10-26-roadmap-08-recurring-tasks.md) | [Plan](superpowers/plans/2026-10-26-roadmap-08-recurring-tasks.md) | [QA scenarios](feature-roadmap/08-recurring-tasks-qa.md) | PENDING | NOT RUN |
| F09 | P2 | Backup freshness and in-app nudges | [Spec](superpowers/specs/2026-10-26-roadmap-09-backup-freshness.md) | [Plan](superpowers/plans/2026-10-26-roadmap-09-backup-freshness.md) | [QA scenarios](feature-roadmap/09-backup-freshness-qa.md) | PENDING | NOT RUN |
| F10 | P2 | Monthly reflection and review notes | [Spec](superpowers/specs/2026-10-26-roadmap-10-monthly-review.md) | [Plan](superpowers/plans/2026-10-26-roadmap-10-monthly-review.md) | [QA scenarios](feature-roadmap/10-monthly-review-qa.md) | PENDING | NOT RUN |
| F11 | P2 | Reusable journal capture templates | [Spec](superpowers/specs/2026-10-26-roadmap-11-capture-templates.md) | [Plan](superpowers/plans/2026-10-26-roadmap-11-capture-templates.md) | [QA scenarios](feature-roadmap/11-capture-templates-qa.md) | PENDING | NOT RUN |
| F12 | P2 | Selective readable exports | [Spec](superpowers/specs/2026-10-26-roadmap-12-readable-exports.md) | [Plan](superpowers/plans/2026-10-26-roadmap-12-readable-exports.md) | [QA scenarios](feature-roadmap/12-readable-exports-qa.md) | PENDING | NOT RUN |
| F13 | P3 | Optional sleep and workout imports | [Spec](superpowers/specs/2026-10-26-roadmap-13-health-imports.md) | [Plan](superpowers/plans/2026-10-26-roadmap-13-health-imports.md) | [QA scenarios](feature-roadmap/13-health-imports-qa.md) | PENDING | NOT RUN |
| F14 | P3 | Build and dependency modernization assessment | [Spec](superpowers/specs/2026-10-26-roadmap-14-build-modernization.md) | [Plan](superpowers/plans/2026-10-26-roadmap-14-build-modernization.md) | [QA scenarios](feature-roadmap/14-build-modernization-qa.md) | PENDING | NOT RUN |
| F15 | P2 (proposed) | Memory and mood text sharing | [Spec](superpowers/specs/2026-10-28-roadmap-15-memory-mood-sharing.md) | [Plan](superpowers/plans/2026-10-28-roadmap-15-memory-mood-sharing.md) | [QA scenarios](feature-roadmap/15-memory-mood-sharing-qa.md) | SOURCE COMPLETE — QA PENDING | NOT RUN |

## Dependencies and sequencing

- **Release foundation:** F01 then F02. F07 can address confirmed accessibility defects from release QA; urgent verified compatibility/security blockers can promote F14.
- **Daily value/privacy:** F03–F07. F04 reuses existing habit reminder contracts; F05 builds on safe archive handling; F06 coordinates notification/entry-point privacy.
- **Extensions:** F08 follows reminder/date contracts; F09 can follow F01 independently as a small in-app-only improvement; F10–F12 add new user content and must coordinate schema/archive versions.
- **Investigation:** F13 first proves demand/provider capability; F14 first documents justified compatibility choices. A documented deferred/no-change recommendation closes assessment scope only, never certifies an unimplemented feature.
- **Single-entry sharing:** F15 reuses saved-entry reads independently of F12; coordinate F06's privacy gate if present. Text-only v1 needs no file-export, backup or schema dependency. Its appended task number preserves existing IDs rather than changing priority order.

Assign next Room/archive versions at actual execution baseline, not from these parallel documents. Preserve old schema JSON and supported adapters; generate/validate new schemas only in QA. All code changes sharing schema/archive inventory need serialized integration.

## Document review and actual execution

Each spec defines goals, source anchors, proposed interfaces, five requirement IDs, defaults and exclusions. Each plan has five implementation checklist items followed by three QA checklist groups. Each QA document has eight fixture/action/expected-result scenarios and an execution ledger: **120 scenarios** overall, plus the shared matrix and linked full Calendar acceptance package. New cases start NOT RUN; actual execution results belong in their owning ledgers.

Read a selected task’s spec, plan, QA and shared workflow together. Before future implementation, reconcile defaults and file paths with then-current source. During QA put actual artifacts under `docs/feature-roadmap/evidence/<actual-run-date>/Fnn/` and update outcomes honestly; do not copy historical pass counts into fresh acceptance records.
