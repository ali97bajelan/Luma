# F05 — Password-protected portable backups Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use delegation only if separately authorized. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Export a portable authenticated encrypted backup that can be restored with its password on a different device.

**Architecture:** Wrap the existing validated archive in an encrypted envelope; decryption authenticates fully into bounded private staging before legacy archive validation or preview. Use a maintained streaming-AEAD implementation rather than inventing a chunk/nonce protocol. Select its pinned version and serialized envelope during source design, and record compatibility before code integration.

**Tech Stack:** Existing Kotlin, pure domain contracts, Room, Compose, Java 17 and native Android adapters as appropriate; candidate dependency changes must be documented before integration.

**Spec:** [Password-protected portable backups specification](../specs/2026-10-26-roadmap-05-encrypted-backups.md)

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../specs/2026-10-26-roadmap-05-encrypted-backups.md) · [Plan](2026-10-26-roadmap-05-encrypted-backups.md) · [QA scenarios](../../feature-roadmap/05-encrypted-backups-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Global constraints

- Exactly two phases per task: implementation, then QA; do not interleave a test cycle into implementation.
- During implementation: no Gradle, sync, resolution, build, lint, schema generation, test execution, emulator or device. Test authoring belongs to QA.
- Preserve existing personal data/user edits, module boundaries, minimum API28 and local-first/offline defaults; follow the spec for any explicit compatibility exception.
- Calendar stays tasks/memories only; preserve navigation, drafts, revision checks, photo ownership and EN/FR/FA/RTL/light/dark support.
- Read [WORKFLOW.md](../../feature-roadmap/WORKFLOW.md) for exact isolation, source handoff and QA result/evidence obligations.

Dependencies: Task 01; coordinate completion metadata with task 09.

## Review focus

- F05-R01 (Portable format contract): execute F05-QA02, F05-QA04, F05-QA08; author assertion-level regressions in Q01 for the specified conditions.
- F05-R02 (Authentication boundary): execute F05-QA03, F05-QA04; author assertion-level regressions in Q01 for the specified conditions.
- F05-R03 (User interaction): execute F05-QA01, F05-QA05; author assertion-level regressions in Q01 for the specified conditions.
- F05-R04 (Secret lifetime and failures): execute F05-QA01, F05-QA06, F05-QA07; author assertion-level regressions in Q01 for the specified conditions.
- F05-R05 (Compatibility): execute F05-QA02, F05-QA03, F05-QA05, F05-QA08; author assertion-level regressions in Q01 for the specified conditions.

## File map and interfaces

Existing paths are source anchors, not a request to edit every file regardless of need. New paths are proposed ownership boundaries; reconcile with existing equivalents before creating duplicates.

- Modify/review: [core/domain/src/main/kotlin/com/luma/lifeos/domain/backup/BackupService.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/backup/BackupService.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupArchive.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupArchive.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupValidation.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupValidation.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/backup/RoomBackupService.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/backup/RoomBackupService.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/backup/BackupViewModel.kt](../../../app/src/main/java/com/luma/lifeos/ui/backup/BackupViewModel.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/backup/BackupSettingsCard.kt](../../../app/src/main/java/com/luma/lifeos/ui/backup/BackupSettingsCard.kt)
- Modify/review: [gradle/libs.versions.toml](../../../gradle/libs.versions.toml)
- Modify/review: [core/data/build.gradle.kts](../../../core/data/build.gradle.kts)
- Modify/review: [docs/BACKUP_FORMAT.md](../../BACKUP_FORMAT.md)
- Create if required by selected implementation: `core/data/src/main/kotlin/com/luma/lifeos/data/backup/EncryptedBackupEnvelope.kt`
- Create if required by selected implementation: `core/domain/src/main/kotlin/com/luma/lifeos/domain/backup/BackupEncryptionOptions.kt`

Interfaces consumed/produced:

- Proposed `BackupEncryptionOptions(password: CharArray)` is transient; no password in saved state/Bundle/preferences/logging.
- Proposed `EncryptedBackupEnvelope.encrypt(input: InputStream, output: OutputStream, password: CharArray)` and `decryptToStaging(input: InputStream, password: CharArray): AuthenticatedArchive`; only authenticated completion returns a handle.
- Use a maintained streaming-AEAD API with 256-bit authenticated encryption and a separate password-derived key. Envelope version, library key format, KDF identifier/parameters, random salt and all public header fields must be authenticated; library-specific protocol is fixed in the format note before implementation.

QA-owned tests (create only in phase 2; extend existing equivalent suites instead of duplicating them where practical):

- QA create/extend: `core/domain/src/test/kotlin/com/luma/lifeos/domain/roadmap/F05ContractTest.kt`
- QA create/extend: `core/data/src/androidTest/kotlin/com/luma/lifeos/data/roadmap/F05PersistenceTest.kt`
- QA create/extend: `app/src/test/java/com/luma/lifeos/roadmap/F05StateTest.kt`
- QA create/extend: `app/src/androidTest/java/com/luma/lifeos/roadmap/F05FlowTest.kt`

## Phase 1 — Implementation

Initial status: **PENDING**.

### F05-I01 — Freeze cryptographic source design

- [ ] Choose a maintained streaming-AEAD implementation from official documentation, inspect its supported API/minSdk/license and pin it in the catalog. Write byte-level envelope/KDF/authentication and resource-bound decisions in BACKUP_FORMAT.md. This source review must precede envelope code; Gradle resolution and performance remain QA.
- [ ] Source-review the changed declarations/callers against F05-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F05-I02 — Implement envelope boundary

- [ ] Create EncryptedBackupEnvelope and transient options. Route encryption around existing archive creation and authenticated staged decryption before prepare; explicitly handle truncation/final authentication and close errors using the selected library contracts.
- [ ] Source-review the changed declarations/callers against F05-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F05-I03 — Add password UI and error states

- [ ] Integrate opt-in export confirmation and import detection/password entry with no saved password. Keep wrong-password/corruption messages generic and preserve live data and existing backup cancellation behavior.
- [ ] Source-review the changed declarations/callers against F05-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F05-I04 — Complete compatibility handoff

- [ ] Document envelope versioning, fixed resource bounds and vectors to be generated in QA, legacy import routes, secret handling and startup cleanup. Unresolved source-library compatibility is a documented implementation blocker, never a homemade cipher fallback.
- [ ] Source-review the changed declarations/callers against F05-R01–R05 and record paths/decisions. Do not run compilation or tests.

### F05-I05 — Source handoff

- [ ] Review all changed files, call sites, permission/manifest declarations, EN/FR/FA resources and persistence/archive contracts for the actual selected scope.
- [ ] Run source-only checks such as `git diff --check`, file/link checks and XML parsing where relevant. A successful static check is not a test/build pass.
- [ ] Record Git/patch identity, implementation decisions, source-to-requirement mapping and known uncertainty. Generated schemas and all runtime verification remain pending QA.
- [ ] Mark phase 1 **SOURCE COMPLETE — QA PENDING** only after I01–I04 and this handoff are complete. Do not mark the task complete or run a Gradle/emulator command here.

## Phase 2 — QA

Initial status: **NOT RUN**. Entry requires I01–I05 handoff and isolated target verification. This phase owns test authoring, Gradle, schemas, emulator/device use and defect retesting.

### F05-Q01 — Fixtures and automated coverage

- [ ] Read the full [QA scenarios](../../feature-roadmap/05-encrypted-backups-qa.md), prepare its synthetic fixture, and create the QA-owned tests above. Give tests descriptive scenario-matching names; assert expected IDs/values and failure atomicity independently of implementation helpers.
- [ ] Cover every requirement and review-focus condition. Prefer small deterministic domain/state tests, real Room migration/transaction tests for persistence and Compose/native flows for platform behavior. Existing test passes cannot replace missing native or manual coverage.
- [ ] If a QA defect needs production correction, retain a failing regression and capture failure before correction where feasible; fix and rerun here without rewriting expected behavior to match a defect.
- [ ] For an explicitly deferred investigation/no-change branch, do not author tests that pretend new behavior exists. Record the branch evidence, mark inapplicable feature assertions N/A with rationale, and run the applicable existing baseline gates below. This accepts assessment scope only; feature runtime QA stays deferred.

### F05-Q02 — Gradle, schemas and affected regressions

Run from repository root **only in QA**, after reading the shared command/isolation policy:

```sh
bash gradlew -PdomainOnly=true :core:domain:test
bash gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest
bash gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :core:data:assembleDebugAndroidTest :core:data:lintDebug
```

- [ ] Record actual commands, exit codes and JUnit/lint outcomes. Expected automated gate: exit 0; required tests actually executed with no failures/errors; skipped tests explained; no lint errors/fatals and new warnings classified.
- [ ] Where schema changed, generate/export current Room schema through QA build/KSP, inspect against hand-written migration, preserve old JSON and execute populated-upgrade plus archive compatibility cases. Commit schema evidence with the validated task, not as an unverified phase-1 claim.
- [ ] Run affected instrumented suites only on a confirmed isolated package/AVD using the shared QA catalogue; do not assume current debug package is isolated. Test-class filters and variant commands must match authored test packages and target identity.

### F05-Q03 — Scenarios, native behavior and acceptance

- [ ] Execute all F05-QA01–QA08 cases and every relevant shared matrix cell; Two-device portability, password input/rotation, near-limit file handling and focused cryptographic format review.
- [ ] Record actual result, requirement mapping and artifact links in the scenario ledger. Source/compile/host passes cannot certify native/manual cases.
- [ ] Resolve defects, rerun owning and affected regression cases, and identify invalidated evidence. Leave blocked/unrun cases visible with precise prerequisites.
- [ ] Mark complete only for the exact validated scope when required cases pass. Assessment/deferred branch closure must explicitly state that feature runtime acceptance has not occurred. Record release blockers/authorized exceptions without silently self-waiving.

Suggested future commit grouping: one source handoff followed by QA tests, generated schemas and evidence; do not commit unrelated user changes. Creating these documents does not authorize implementation, release or commit actions now.
