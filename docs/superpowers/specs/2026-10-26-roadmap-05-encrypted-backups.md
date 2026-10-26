# F05 — Password-protected portable backups specification

Date: 26 October 2026 · Priority: **P1** · Status: **DRAFT — NOT IMPLEMENTED / NOT VALIDATED**

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](2026-10-26-roadmap-05-encrypted-backups.md) · [Plan](../plans/2026-10-26-roadmap-05-encrypted-backups.md) · [QA scenarios](../../feature-roadmap/05-encrypted-backups-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Intent and dependencies

Export a portable authenticated encrypted backup that can be restored with its password on a different device.

Dependencies: Task 01; coordinate completion metadata with task 09.

Rank and scope come from [the feature proposal](../../FEATURE_IMPROVEMENT_PROPOSAL_2026-10-26.md). Specific new defaults in this document are proposed implementation choices, not claims of user approval. Read the existing implementation at execution time; this document does not certify its current runtime behavior.

## Architecture and alternatives

Recommended approach: Wrap the existing validated archive in an encrypted envelope; decryption authenticates fully into bounded private staging before legacy archive validation or preview. Use a maintained streaming-AEAD implementation rather than inventing a chunk/nonce protocol. Select its pinned version and serialized envelope during source design, and record compatibility before code integration.

Considered alternatives: retain the current behavior and document its limitations, or introduce an independent subsystem. Retaining behavior is acceptable only for an explicitly documented investigation/deferred outcome; otherwise it does not meet the intent above. An independent replacement would duplicate existing persistence/navigation and recovery paths; extend the current module contracts unless a specific requirement makes reuse impossible. The recommendation is proposed, not a recorded user selection.

## Existing source and handoff material

- [core/domain/src/main/kotlin/com/luma/lifeos/domain/backup/BackupService.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/backup/BackupService.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupArchive.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupArchive.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupValidation.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupValidation.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/backup/RoomBackupService.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/backup/RoomBackupService.kt)
- [app/src/main/java/com/luma/lifeos/ui/backup/BackupViewModel.kt](../../../app/src/main/java/com/luma/lifeos/ui/backup/BackupViewModel.kt)
- [app/src/main/java/com/luma/lifeos/ui/backup/BackupSettingsCard.kt](../../../app/src/main/java/com/luma/lifeos/ui/backup/BackupSettingsCard.kt)
- [gradle/libs.versions.toml](../../../gradle/libs.versions.toml)
- [core/data/build.gradle.kts](../../../core/data/build.gradle.kts)
- [docs/BACKUP_FORMAT.md](../../BACKUP_FORMAT.md)

Source-design references checked 26 October 2026: [Tink streaming AEAD guidance](https://developers.google.com/tink/encrypt-large-files-or-data-streams) supports using a maintained primitive for files larger than memory. [OWASP PBKDF2 guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) lists 600,000 iterations for HMAC-SHA256 in its password-storage context. Adopting that cost for this envelope is a proposed design choice, not a complete archive-encryption specification or proof of performance; the source-format design and QA review remain required.

## Contracts and ownership

Names below are proposed interfaces to create or adapt, not claims that these declarations already exist. Keep existing public callers working or migrate every caller explicitly in the owning change. Resolve exact current types while preserving these behaviors.

- Proposed `BackupEncryptionOptions(password: CharArray)` is transient; no password in saved state/Bundle/preferences/logging.
- Proposed `EncryptedBackupEnvelope.encrypt(input: InputStream, output: OutputStream, password: CharArray)` and `decryptToStaging(input: InputStream, password: CharArray): AuthenticatedArchive`; only authenticated completion returns a handle.
- Use a maintained streaming-AEAD API with 256-bit authenticated encryption and a separate password-derived key. Envelope version, library key format, KDF identifier/parameters, random salt and all public header fields must be authenticated; library-specific protocol is fixed in the format note before implementation.

## Requirements

### F05-R01 — Portable format contract

Source-design deliverable fixes one maintained streaming-AEAD library/version, format identifier/version and byte-level header using its supported format. Proposed password KDF is PBKDF2-HMAC-SHA256 with random salt >=16 bytes and >=600,000 iterations, 256-bit key. QA verifies device cost; parameters cannot silently be reduced. No device-only Keystore key is required to restore.

### F05-R02 — Authentication boundary

Do not expose record preview or invoke PreparedBackup publication until every ciphertext segment and end-of-stream/truncation condition authenticates. Wrong password, modified header/body, reordered or missing segments and extra trailing data fail closed. Apply input, expanded-content, disk and KDF-parameter bounds before costly work.

### F05-R03 — User interaction

Encryption is explicit opt-in; exporting requires nonempty password of at least 12 Unicode code points and exact confirmation, with no trimming/normalization. Show/hide choice is local. Explain lost-password non-recoverability and lack of encryption for legacy plain archives.

### F05-R04 — Secret lifetime and failures

Keep password/key material transient and clear mutable buffers best-effort on completion/cancel. Rotation may ask for the password again; it never persists it. All decrypted staging remains app-private, bounded, and removed on close/failure or safe startup cleanup. Never report success before output close.

### F05-R05 — Compatibility

Keep supported plaintext archives readable. Encrypted envelope version is separate from inner portable schema and Room schema; unknown versions reject safely. Exported names and public headers contain no record titles/photo names. Publish interoperability fixtures without real secrets.

## Two-phase delivery contract

**Phase 1 — Implementation:** implement the source/configuration/resources/documentation described in the plan, review the source and diff, and record the handoff. **No Gradle command, sync, dependency resolution, build, schema generation, lint, automated test execution, emulator launch or device test. Test authoring is deferred to QA.** Source checks do not prove compilation or correctness.

**Phase 2 — QA:** author tests/fixtures, generate and inspect schemas where relevant, run Gradle/build/lint/automated suites, execute native/manual cases, correct discovered defects and record evidence. Begin only after this task’s implementation source handoff; all initial outcomes are NOT RUN. Follow the [shared workflow](../../feature-roadmap/WORKFLOW.md) for isolation, matrix, commands, evidence and completion gates.

## Scope boundaries

No password recovery service, custom streaming nonce protocol, database encryption, or automatic cloud upload.

## Acceptance and traceability

All five requirements must map to implementation steps and QA outcomes. [The QA document](../../feature-roadmap/05-encrypted-backups-qa.md) provides executable scenarios with fixtures/actions/oracles; [the plan](../plans/2026-10-26-roadmap-05-encrypted-backups.md) owns concrete files and implementation/QA checklists. Both phases start pending. A source-complete task is not feature-complete. For assessment/deferred branches, report the delivered investigation separately from feature/runtime acceptance.
