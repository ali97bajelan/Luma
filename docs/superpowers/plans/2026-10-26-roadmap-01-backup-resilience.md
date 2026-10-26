# F01 — Backup and restore resilience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use delegation only if separately authorized. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Demonstrate that backup failures and interrupted restores preserve a consistent dataset and referenced photos.

**Architecture:** Retain BackupService and PreparedBackup and their replacement transaction. Add narrowly scoped failure-injection seams only where existing stream, filesystem, and coroutine boundaries cannot be controlled; production defaults never inject failures.

**Tech Stack:** Existing Kotlin, pure domain contracts, Room, Compose, Java 17 and native Android adapters as appropriate; candidate dependency changes must be documented before integration.

**Spec:** [Backup and restore resilience specification](../specs/2026-10-26-roadmap-01-backup-resilience.md)

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../specs/2026-10-26-roadmap-01-backup-resilience.md) · [Plan](2026-10-26-roadmap-01-backup-resilience.md) · [QA scenarios](../../feature-roadmap/01-backup-resilience-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Global constraints

- Exactly two phases per task: implementation, then QA; do not interleave a test cycle into implementation.
- During implementation: no Gradle, sync, resolution, build, lint, schema generation, test execution, emulator or device. Test authoring belongs to QA.
- Preserve existing personal data/user edits, module boundaries, minimum API28 and local-first/offline defaults; follow the spec for any explicit compatibility exception.
- Calendar stays tasks/memories only; preserve navigation, drafts, revision checks, photo ownership and EN/FR/FA/RTL/light/dark support.
- Read [WORKFLOW.md](../../feature-roadmap/WORKFLOW.md) for exact isolation, source handoff and QA result/evidence obligations.

Dependencies: None; foundational task.

## Review focus

- F01-R01 (Atomic replacement): execute F01-QA01, F01-QA02, F01-QA04, F01-QA05; author assertion-level regressions in Q01 for the specified conditions.
- F01-R02 (Export truthfulness): execute F01-QA03, F01-QA08; author assertion-level regressions in Q01 for the specified conditions.
- F01-R03 (Validation and bounds): execute F01-QA01, F01-QA06; author assertion-level regressions in Q01 for the specified conditions.
- F01-R04 (Concurrency and recovery): execute F01-QA04, F01-QA05, F01-QA07; author assertion-level regressions in Q01 for the specified conditions.
- F01-R05 (Usable recovery): execute F01-QA02, F01-QA03, F01-QA04, F01-QA05, F01-QA08; author assertion-level regressions in Q01 for the specified conditions.

## File map and interfaces

Existing paths are source anchors, not a request to edit every file regardless of need. New paths are proposed ownership boundaries; reconcile with existing equivalents before creating duplicates.

- Modify/review: [core/domain/src/main/kotlin/com/luma/lifeos/domain/backup/BackupService.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/backup/BackupService.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/backup/RoomBackupService.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/backup/RoomBackupService.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupArchive.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupArchive.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupValidation.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupValidation.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/media/ManagedPhotoFiles.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/media/ManagedPhotoFiles.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/backup/BackupViewModel.kt](../../../app/src/main/java/com/luma/lifeos/ui/backup/BackupViewModel.kt)
- Modify/review: [docs/BACKUP_FORMAT.md](../../BACKUP_FORMAT.md)
- Create if required by selected implementation: `core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupFailurePoint.kt`

Interfaces consumed/produced:

- Keep `BackupService.export(output: OutputStream)` and `prepare(input: InputStream): PreparedBackup` source compatible.
- Proposed internal `BackupFailurePoint.reached(stage: BackupStage)` is a suspending no-op in production; stages name snapshot completion, photo sync, before DB commit, after DB commit, and cleanup. It must not expose an external trigger.
- `PreparedBackup.restore()` remains one-shot; `close()` releases prepared resources without changing live records.

QA-owned tests (create only in phase 2; extend existing equivalent suites instead of duplicating them where practical):

- QA create/extend: `core/data/src/androidTest/kotlin/com/luma/lifeos/data/roadmap/F01PersistenceTest.kt`
- QA create/extend: `app/src/test/java/com/luma/lifeos/roadmap/F01StateTest.kt`
- QA create/extend: `app/src/androidTest/java/com/luma/lifeos/roadmap/F01FlowTest.kt`

## Phase 1 — Implementation

Initial status: **PENDING**.

### F01-I01 — Trace the publication boundary

- [ ] Read RoomBackupService, stream ownership and photo mutex/leases; document the exact transaction commit and cancellation points in BACKUP_FORMAT.md. Preserve public interfaces.
- [ ] Source-review the changed declarations/callers against F01-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F01-I02 — Add bounded failure seams

- [ ] Implement internal BackupStage/BackupFailurePoint and inject the no-op default through the service constructor. Expose no intent, preference, or production UI for failure injection.
- [ ] Source-review the changed declarations/callers against F01-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F01-I03 — Harden proven static gaps

- [ ] Audit cleanup finally blocks, destination-close success ordering, cancelled prepared handles and post-commit cleanup. Correct only demonstrable source-level issues; leave runtime hypotheses for QA.
- [ ] Source-review the changed declarations/callers against F01-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F01-I04 — Prepare recovery handoff

- [ ] Document expected pre/post-commit datasets, staging ownership and user messages; supply a source-to-stage map so QA can inject failures without editing personal data.
- [ ] Source-review the changed declarations/callers against F01-R01–R05 and record paths/decisions. Do not run compilation or tests.

### F01-I05 — Source handoff

- [ ] Review all changed files, call sites, permission/manifest declarations, EN/FR/FA resources and persistence/archive contracts for the actual selected scope.
- [ ] Run source-only checks such as `git diff --check`, file/link checks and XML parsing where relevant. A successful static check is not a test/build pass.
- [ ] Record Git/patch identity, implementation decisions, source-to-requirement mapping and known uncertainty. Generated schemas and all runtime verification remain pending QA.
- [ ] Mark phase 1 **SOURCE COMPLETE — QA PENDING** only after I01–I04 and this handoff are complete. Do not mark the task complete or run a Gradle/emulator command here.

## Phase 2 — QA

Initial status: **NOT RUN**. Entry requires I01–I05 handoff and isolated target verification. This phase owns test authoring, Gradle, schemas, emulator/device use and defect retesting.

### F01-Q01 — Fixtures and automated coverage

- [ ] Read the full [QA scenarios](../../feature-roadmap/01-backup-resilience-qa.md), prepare its synthetic fixture, and create the QA-owned tests above. Give tests descriptive scenario-matching names; assert expected IDs/values and failure atomicity independently of implementation helpers.
- [ ] Cover every requirement and review-focus condition. Prefer small deterministic domain/state tests, real Room migration/transaction tests for persistence and Compose/native flows for platform behavior. Existing test passes cannot replace missing native or manual coverage.
- [ ] If a QA defect needs production correction, retain a failing regression and capture failure before correction where feasible; fix and rerun here without rewriting expected behavior to match a defect.
- [ ] For an explicitly deferred investigation/no-change branch, do not author tests that pretend new behavior exists. Record the branch evidence, mark inapplicable feature assertions N/A with rationale, and run the applicable existing baseline gates below. This accepts assessment scope only; feature runtime QA stays deferred.

### F01-Q02 — Gradle, schemas and affected regressions

Run from repository root **only in QA**, after reading the shared command/isolation policy:

```sh
bash gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest
bash gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :core:data:assembleDebugAndroidTest :core:data:lintDebug
```

- [ ] Record actual commands, exit codes and JUnit/lint outcomes. Expected automated gate: exit 0; required tests actually executed with no failures/errors; skipped tests explained; no lint errors/fatals and new warnings classified.
- [ ] Where schema changed, generate/export current Room schema through QA build/KSP, inspect against hand-written migration, preserve old JSON and execute populated-upgrade plus archive compatibility cases. Commit schema evidence with the validated task, not as an unverified phase-1 claim.
- [ ] Run affected instrumented suites only on a confirmed isolated package/AVD using the shared QA catalogue; do not assume current debug package is isolated. Test-class filters and variant commands must match authored test packages and target identity.

### F01-Q03 — Scenarios, native behavior and acceptance

- [ ] Execute all F01-QA01–QA08 cases and every relevant shared matrix cell; Real document provider, disk exhaustion, lifecycle interruption and abrupt-shutdown recovery on disposable storage.
- [ ] Record actual result, requirement mapping and artifact links in the scenario ledger. Source/compile/host passes cannot certify native/manual cases.
- [ ] Resolve defects, rerun owning and affected regression cases, and identify invalidated evidence. Leave blocked/unrun cases visible with precise prerequisites.
- [ ] Mark complete only for the exact validated scope when required cases pass. Assessment/deferred branch closure must explicitly state that feature runtime acceptance has not occurred. Record release blockers/authorized exceptions without silently self-waiving.

Suggested future commit grouping: one source handoff followed by QA tests, generated schemas and evidence; do not commit unrelated user changes. Creating these documents does not authorize implementation, release or commit actions now.
