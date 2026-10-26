# F09 — Backup freshness and in-app nudges specification

Date: 26 October 2026 · Priority: **P2** · Status: **DRAFT — NOT IMPLEMENTED / NOT VALIDATED**

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](2026-10-26-roadmap-09-backup-freshness.md) · [Plan](../plans/2026-10-26-roadmap-09-backup-freshness.md) · [QA scenarios](../../feature-roadmap/09-backup-freshness-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Intent and dependencies

Show a truthful device-local last-export time and a dismissible reminder to make a manual backup.

Dependencies: Task 01; show encrypted action after task 05 is available. Task 04 is not required for in-app-only nudges.

Rank and scope come from [the feature proposal](../../FEATURE_IMPROVEMENT_PROPOSAL_2026-10-26.md). Specific new defaults in this document are proposed implementation choices, not claims of user approval. Read the existing implementation at execution time; this document does not certify its current runtime behavior.

## Architecture and alternatives

Recommended approach: Persist only successful completion metadata in a device-local preference store after the complete export stream closes. Derive freshness from an injected clock and show a Settings/Today in-app nudge; do not schedule background export or notifications.

Considered alternatives: retain the current behavior and document its limitations, or introduce an independent subsystem. Retaining behavior is acceptable only for an explicitly documented investigation/deferred outcome; otherwise it does not meet the intent above. An independent replacement would duplicate existing persistence/navigation and recovery paths; extend the current module contracts unless a specific requirement makes reuse impossible. The recommendation is proposed, not a recorded user selection.

## Existing source and handoff material

- [app/src/main/java/com/luma/lifeos/ui/backup/BackupViewModel.kt](../../../app/src/main/java/com/luma/lifeos/ui/backup/BackupViewModel.kt)
- [app/src/main/java/com/luma/lifeos/ui/backup/BackupSettingsCard.kt](../../../app/src/main/java/com/luma/lifeos/ui/backup/BackupSettingsCard.kt)
- [app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt](../../../app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt)
- [app/src/main/java/com/luma/lifeos/ui/privacy/AppDataReset.kt](../../../app/src/main/java/com/luma/lifeos/ui/privacy/AppDataReset.kt)



## Contracts and ownership

Names below are proposed interfaces to create or adapt, not claims that these declarations already exist. Keep existing public callers working or migrate every caller explicitly in the owning change. Resolve exact current types while preserving these behaviors.

- Proposed `BackupSuccess(at: Instant, encrypted: Boolean)` contains no provider URI or record contents.
- Proposed `BackupFreshnessStore.recordSuccess(value: BackupSuccess)` and `observe(): Flow<BackupFreshnessState>`; record only after service completion AND output close.
- Proposed `shouldShowBackupNudge(now: Instant, lastSuccess: Instant?, snoozedUntil: Instant?, enabled: Boolean): Boolean`; default stale threshold 7 days and dismissal snooze 7 days.

## Requirements

### F09-R01 — Truthful status

Fresh install displays Never exported on this device. Only successful closed export advances metadata; failure/cancel/restore/import do not. Explain that a past export does not prove the external file still exists or remains readable.

### F09-R02 — Nudge schedule

Nudges enabled by default in-app, first eligible 7 days after install when no export exists, otherwise 7 days after last success. Dismiss snoozes 7 days; turn off persists. Exact threshold is inclusive; use elapsed duration, not local midnight counts.

### F09-R03 — Time corrections

If stored success/install/snooze time lies in the future relative to now, display time uncertainty and suppress age-based nudge until chronology is valid or a new export succeeds; do not label a future export freshly verified.

### F09-R04 — Privacy and destination

Nudge text contains no names/counts/titles, opens existing manual backup UI, and does not retain provider URI. Encrypted export is offered if available, never implied for old plaintext exports. No background job, auto-upload or notification permission.

### F09-R05 — Restore/reset and failure

Metadata/preferences are device-local, excluded from portable archive and preserved across replacement of content. Full reset clears them. Failure to save freshness must not claim the archive failed: show export success plus status-not-updated warning and avoid duplicate export.

## Two-phase delivery contract

**Phase 1 — Implementation:** implement the source/configuration/resources/documentation described in the plan, review the source and diff, and record the handoff. **No Gradle command, sync, dependency resolution, build, schema generation, lint, automated test execution, emulator launch or device test. Test authoring is deferred to QA.** Source checks do not prove compilation or correctness.

**Phase 2 — QA:** author tests/fixtures, generate and inspect schemas where relevant, run Gradle/build/lint/automated suites, execute native/manual cases, correct discovered defects and record evidence. Begin only after this task’s implementation source handoff; all initial outcomes are NOT RUN. Follow the [shared workflow](../../feature-roadmap/WORKFLOW.md) for isolation, matrix, commands, evidence and completion gates.

## Scope boundaries

No scheduled export, persisted destination access, cloud sync or notification reminder in v1.

## Acceptance and traceability

All five requirements must map to implementation steps and QA outcomes. [The QA document](../../feature-roadmap/09-backup-freshness-qa.md) provides executable scenarios with fixtures/actions/oracles; [the plan](../plans/2026-10-26-roadmap-09-backup-freshness.md) owns concrete files and implementation/QA checklists. Both phases start pending. A source-complete task is not feature-complete. For assessment/deferred branches, report the delivered investigation separately from feature/runtime acceptance.
