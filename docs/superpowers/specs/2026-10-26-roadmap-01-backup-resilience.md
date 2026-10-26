# F01 — Backup and restore resilience specification

Date: 26 October 2026 · Priority: **P0** · Status: **DRAFT — NOT IMPLEMENTED / NOT VALIDATED**

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](2026-10-26-roadmap-01-backup-resilience.md) · [Plan](../plans/2026-10-26-roadmap-01-backup-resilience.md) · [QA scenarios](../../feature-roadmap/01-backup-resilience-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Intent and dependencies

Demonstrate that backup failures and interrupted restores preserve a consistent dataset and referenced photos.

Dependencies: None; foundational task.

Rank and scope come from [the feature proposal](../../FEATURE_IMPROVEMENT_PROPOSAL_2026-10-26.md). Specific new defaults in this document are proposed implementation choices, not claims of user approval. Read the existing implementation at execution time; this document does not certify its current runtime behavior.

## Architecture and alternatives

Recommended approach: Retain BackupService and PreparedBackup and their replacement transaction. Add narrowly scoped failure-injection seams only where existing stream, filesystem, and coroutine boundaries cannot be controlled; production defaults never inject failures.

Considered alternatives: retain the current behavior and document its limitations, or introduce an independent subsystem. Retaining behavior is acceptable only for an explicitly documented investigation/deferred outcome; otherwise it does not meet the intent above. An independent replacement would duplicate existing persistence/navigation and recovery paths; extend the current module contracts unless a specific requirement makes reuse impossible. The recommendation is proposed, not a recorded user selection.

## Existing source and handoff material

- [core/domain/src/main/kotlin/com/luma/lifeos/domain/backup/BackupService.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/backup/BackupService.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/backup/RoomBackupService.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/backup/RoomBackupService.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupArchive.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupArchive.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupValidation.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupValidation.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/media/ManagedPhotoFiles.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/media/ManagedPhotoFiles.kt)
- [app/src/main/java/com/luma/lifeos/ui/backup/BackupViewModel.kt](../../../app/src/main/java/com/luma/lifeos/ui/backup/BackupViewModel.kt)
- [docs/BACKUP_FORMAT.md](../../BACKUP_FORMAT.md)



## Contracts and ownership

Names below are proposed interfaces to create or adapt, not claims that these declarations already exist. Keep existing public callers working or migrate every caller explicitly in the owning change. Resolve exact current types while preserving these behaviors.

- Keep `BackupService.export(output: OutputStream)` and `prepare(input: InputStream): PreparedBackup` source compatible.
- Proposed internal `BackupFailurePoint.reached(stage: BackupStage)` is a suspending no-op in production; stages name snapshot completion, photo sync, before DB commit, after DB commit, and cleanup. It must not expose an external trigger.
- `PreparedBackup.restore()` remains one-shot; `close()` releases prepared resources without changing live records.

## Requirements

### F01-R01 — Atomic replacement

Before publication commits, failure or cancellation leaves the original records and referenced photo bytes unchanged. After commit, restart exposes the complete restored dataset, never a mixture. Report an uncertain interrupted result by inspecting state, not by promising rollback after commit.

### F01-R02 — Export truthfulness

Success is shown only after archive finalization and caller stream close succeed. Missing referenced photos, output errors, and cancellation cannot produce success. A provider may retain an incomplete file; explain that outcome and offer retry.

### F01-R03 — Validation and bounds

Preserve supported legacy schemas, exact photo inventory, UUID/path checks, 2 GiB expanded-content limit, 25 MiB/photo limit, and rejection before publication. Do not relax validators to make fixtures pass.

### F01-R04 — Concurrency and recovery

Backup, photo import/cleanup, restore, and protected drafts coordinate through the existing ownership rules. Delete only orphan staging owned by the failed operation; do not remove live photos or another editor’s lease.

### F01-R05 — Usable recovery

Cancellation is allowed before publication. During the non-cancellable publication boundary explain that replacement is finishing. On return/restart, show an actionable failure or actual completed state without automatic replay of replacement.

## Two-phase delivery contract

**Phase 1 — Implementation:** implement the source/configuration/resources/documentation described in the plan, review the source and diff, and record the handoff. **No Gradle command, sync, dependency resolution, build, schema generation, lint, automated test execution, emulator launch or device test. Test authoring is deferred to QA.** Source checks do not prove compilation or correctness.

**Phase 2 — QA:** author tests/fixtures, generate and inspect schemas where relevant, run Gradle/build/lint/automated suites, execute native/manual cases, correct discovered defects and record evidence. Begin only after this task’s implementation source handoff; all initial outcomes are NOT RUN. Follow the [shared workflow](../../feature-roadmap/WORKFLOW.md) for isolation, matrix, commands, evidence and completion gates.

## Scope boundaries

No merge restore, encrypted envelope, automatic upload, or backup scheduling in this task.

## Acceptance and traceability

All five requirements must map to implementation steps and QA outcomes. [The QA document](../../feature-roadmap/01-backup-resilience-qa.md) provides executable scenarios with fixtures/actions/oracles; [the plan](../plans/2026-10-26-roadmap-01-backup-resilience.md) owns concrete files and implementation/QA checklists. Both phases start pending. A source-complete task is not feature-complete. For assessment/deferred branches, report the delivered investigation separately from feature/runtime acceptance.
