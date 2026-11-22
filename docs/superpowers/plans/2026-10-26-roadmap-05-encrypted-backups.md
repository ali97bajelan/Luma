# F05 — Password-protected portable backups Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use delegation only if separately authorized. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Export a portable authenticated encrypted backup that can be restored with its password on a different device.

**Architecture:** Wrap the existing validated archive in an encrypted envelope; decryption authenticates fully into bounded private staging before legacy archive validation or preview. Use a maintained streaming-AEAD implementation rather than inventing a chunk/nonce protocol. Select its pinned version and serialized envelope during source design, and record compatibility before code integration.

**Tech Stack:** Existing Kotlin, pure domain contracts, Room, Compose, Java 17 and native Android adapters as appropriate; candidate dependency changes must be documented before integration.

**Spec:** [Password-protected portable backups specification](../specs/2026-10-26-roadmap-05-encrypted-backups.md)

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../specs/2026-10-26-roadmap-05-encrypted-backups.md) · [Plan](2026-10-26-roadmap-05-encrypted-backups.md) · [QA scenarios](../../feature-roadmap/05-encrypted-backups-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Global constraints

- Exactly two phases per task: implementation, then QA; do not interleave a test cycle into implementation.
- No Gradle, sync, resolution, build, lint, schema generation, test execution, emulator or device until source implementation is complete. For the IP-I02 handoff, the user explicitly requested exactly one `bash gradlew check` afterward; its result is recorded there and does not start QA. Test authoring and runtime QA remain owned by phase 2.
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

Implemented source contracts:

- `BackupEncryptionOptions(password: CharArray)` is transient; password data is not placed in saved state, Bundle, preferences or logs.
- `BackupService.export(output, focusChoice, encryption)` and `prepare(input, encryption)` keep plaintext callers compatible.
- `EncryptedBackupEnvelope.encrypt(output, password, writeArchive)` streams the existing ZIP into the selected Tink Streaming AEAD. `decryptToFile(input, password, target)` authenticates the complete bounded stream into private staging before returning to ZIP validation.
- LUMAENC v1, Tink 1.24.0, KDF, key wrapping, associated data and resource bounds are frozen in `docs/BACKUP_FORMAT.md`; QA must verify runtime behavior and cross-install portability.

QA-owned tests (create only in phase 2; extend existing equivalent suites instead of duplicating them where practical):

- QA create/extend: `core/domain/src/test/kotlin/com/luma/lifeos/domain/roadmap/F05ContractTest.kt`
- QA create/extend: `core/data/src/androidTest/kotlin/com/luma/lifeos/data/roadmap/F05PersistenceTest.kt`
- QA create/extend: `app/src/test/java/com/luma/lifeos/roadmap/F05StateTest.kt`
- QA create/extend: `app/src/androidTest/java/com/luma/lifeos/roadmap/F05FlowTest.kt`

## Phase 1 — Implementation

Initial status: **SOURCE COMPLETE — QA PENDING** as of 22 November 2026. See the [IP-I02 source handoff](2026-11-20-product-improvements-i02-backup-and-recovery.md#source-handoff).

### F05-I01 — Freeze cryptographic source design

- [x] Choose Tink Android 1.24.0 and its AES256_GCM_HKDF_1MB Streaming AEAD template. Freeze the byte-level envelope, key wrapping, KDF, authentication and bounds in BACKUP_FORMAT.md before codec integration; runtime cost remains QA.
- [x] Source-review the changed declarations/callers against F05-R01–R05 and record paths/decisions.


### F05-I02 — Implement envelope boundary

- [x] Create EncryptedBackupEnvelope and transient options. Route encryption around existing archive creation and authenticated staged decryption before prepare; handle truncation/final authentication and close failures with the selected library contracts.
- [x] Source-review the changed declarations/callers against F05-R01–R05 and record paths/decisions.


### F05-I03 — Add password UI and error states

- [x] Integrate opt-in export confirmation and import detection/password entry with no saved password. Keep wrong-password/corruption messages generic and preserve live data and existing backup cancellation behavior.
- [x] Source-review the changed declarations/callers against F05-R01–R05 and record paths/decisions.


### F05-I04 — Complete compatibility handoff

- [x] Document envelope versioning, fixed resource bounds and vectors to be generated in QA, legacy import routes, secret handling and abandoned-work cleanup in BACKUP_FORMAT.md. Tink runtime compatibility remains an explicit QA check.
- [x] Source-review the changed declarations/callers against F05-R01–R05 and record paths/decisions.

### F05-I05 — Source handoff

- [x] Review all changed files, call sites, permission/manifest declarations, EN/FR/FA resources and persistence/archive contracts for the actual selected scope.
- [x] Run source-only checks such as `git diff --check`, file/link checks and XML parsing where relevant. A successful static check is not a test/build pass.
- [x] Record Git/patch identity, implementation decisions, source-to-requirement mapping and known uncertainty. Generated schemas and all runtime verification remain pending QA.
- [x] Mark phase 1 **SOURCE COMPLETE — QA PENDING** after I01–I04 and this handoff are complete. The user-requested source check is not QA; all runtime verification remains pending.

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
