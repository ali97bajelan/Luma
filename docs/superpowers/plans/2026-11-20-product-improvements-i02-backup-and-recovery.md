# IP-I02 — Backup and recovery Implementation Plan

> **For agentic workers** Use the applicable Superpowers implementation skill when this plan is authorized for execution. Steps use checkbox syntax for tracking.

**Goal** Make the consequences of export, restore and forgotten lock credentials clear, and provide a portable encrypted archive choice.

**Architecture** Two ordered slices are implemented: explicit plaintext/encrypted export disclosure, followed by a compatible authenticated outer envelope that preserves existing archive validation, staging and migration logic. The LUMAENC v1 streaming-AEAD contract is frozen in BACKUP_FORMAT.md before codec integration.

**Tech stack** Kotlin, Jetpack Compose, Luma components and existing route/state/repository contracts. Android SAF, Room backup staging and the approved F05 archive dependency also apply.

**Spec** [IP-I02 specification](../specs/2026-11-20-product-improvements-i02-backup-and-recovery-design.md) · [Task index](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md) · [IP-Q01 scenario](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-02)

## Files and interfaces

Paths are from the project root. Unqualified sibling filenames share the preceding directory.

- `app/src/main/java/com/luma/lifeos/ui/backup/BackupSettingsCard.kt + BackupViewModel.kt` — Format selection, transient password UI, preview and result orchestration.
- `app/src/main/java/com/luma/lifeos/ui/privacy/AppLockSettingsRoute.kt + AppLockRecoveryRoute.kt` — Consistent erase-only disclosure.
- `core/domain/src/main/kotlin/com/luma/lifeos/domain/backup/BackupService.kt` — Compatible export/prepare contract with explicit format, prompt and outcome.
- `core/data/src/main/kotlin/com/luma/lifeos/data/backup/RoomBackupService.kt + BackupArchive.kt + BackupValidation.kt` — Envelope dispatch, bounded private staging, inner validation and existing restore lifecycle.
- `docs/BACKUP_FORMAT.md` — Versioned format and exclusions.
- Update string resources and plurals in `app/src/main/res/values/`, `values-fr/` and `values-fa/` for affected copy.

BackupService currently uses export(OutputStream, BackupFocusExportChoice?) and prepare(InputStream): PreparedBackup. Add a compatible encrypted path at this boundary, retain PreparedBackup.preview/restore/close(), and record concrete types with the format freeze.

## Global constraints

Disclose format consequences before archive export. Reuse the existing F05 portable encryption design, backup validation and App Lock flows. Apply the [shared implementation contract](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md#shared-implementation-contract). Record source handoff before IP-Q01.

## Review focus

Failures at stream finalization or before data commit must leave the previous archive/records intact where storage-provider capabilities permit cleanup. Capture provider limits explicitly.

## Implementation checklist

- [x] **1.** Trace actual archive members, exclusions, restore boundaries and App Lock prompts. Add format disclosure using the current BackupPreview and protected operation flow.
- [x] **2.** Freeze the [F05 design](../specs/2026-10-26-roadmap-05-encrypted-backups.md)’s envelope identifier/version, byte layout, authenticated header, Tink streaming AEAD dependency/version, portable password encoding, KDF parameters, input/staging bounds and forward-version behavior in BACKUP_FORMAT.md. The frozen contract uses PBKDF2-HMAC-SHA256 (600,000 iterations, fresh 16-byte salt and 256-bit key); runtime cost remains QA.
- [x] **3.** Implement stream dispatch and typed password-required/invalid outcomes at the backup boundary. Keep existing plaintext export/prepare callers compatible. Produce PreparedBackup only after complete encrypted-stream authentication and existing ZIP/integrity validation for both formats.
- [x] **4.** Wire format choice, transient password entry/re-entry and sanitized errors. Use generic export metadata; clear secrets and staging on success/failure/cancel/departure. Preserve old archive support, Focus choices and fresh restore authorization.
- [x] **5.** Review source and localization against IP-I02-R1–R4. Record changed files, interface decisions, risks and source evidence for IP-Q01.

## Source handoff

**Status:** SOURCE COMPLETE — QA PENDING

**Base revision:** `e4dbcf991337910215f69121a37a7c562b68b45c`

**Source patch SHA-256 (tracked diff excluding this handoff and AGENTS.md):** `2eeebe36c8e5ca6f22c46ee6b56c036d2ba47987c73096cfc6b2998ad5e0ee2e`

**Additional untracked source SHA-256:** `EncryptedBackupEnvelope.kt` — `8a3b933fc64743e7672cb6f1530c49ec6614816cf55ad7dd86c05d251507453f`

Changed source/configuration/resources/docs: `BackupService.kt`, `RoomBackupService.kt`,
`EncryptedBackupEnvelope.kt`, `BackupViewModel.kt`, `BackupSettingsCard.kt`,
`libs.versions.toml`, `core/data/build.gradle.kts`, EN/FR/FA string resources and
`docs/BACKUP_FORMAT.md`.

The service retains legacy plaintext callers and the existing inner ZIP/Room schemas.
Encrypted imports are authenticated into private staging before `BackupArchive.read`;
the existing staged Room validation and replacement transaction remain the publication
boundary. LUMAENC v1 uses Tink 1.24.0 Streaming AEAD, PBKDF2-HMAC-SHA256 and fixed
resource bounds documented in BACKUP_FORMAT.md. Passwords use transient memory only.
The existing App Lock recovery route already disclosed erase-only recovery; export
format selection repeats that consequence before export.

No test files were authored in this implementation pass; IP-Q01 owns the mapped
regression cases and independent-device acceptance.

**Requested source verification:** `bash gradlew check` — **PASS**, exit code 0
(`BUILD SUCCESSFUL in 3m 46s`, 252 actionable tasks: 218 executed, 34 from
cache). The check completed on 22 November 2026. It ran existing unit-test
tasks as dependencies; no test files were authored in this implementation pass.
This source check does not change the separate IP-Q01 result, which remains NOT RUN.

Known QA items: confirm Tink runtime behavior and KDF cost on supported devices,
cross-install archive portability, wrong-password/tamper/truncation/trailing-data
failure atomicity, output-provider cleanup limits, route cancellation, password
lifetime, App Lock reauthorization, Focus rules, locale/RTL/large-text rendering and
private staging cleanup after process interruption.

## QA handoff

- [x] Map `encryptedInputAuthenticatesBeforePreview`, `legacyAndEncryptedRoundTripsOnIndependentInstalls`, `failureLeavesSentinelAndCleansStaging` to [QA-02](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-02) for the final verification task. Extend existing test surfaces where appropriate: BackupViewModelTest; BackupSettingsFlowTest; BackupPersistenceTest; BackupCodecTest.
- [x] Map the predecessor [F05 verification cases](../../feature-roadmap/05-encrypted-backups-qa.md) to QA-02 and record source status as **SOURCE COMPLETE — QA PENDING**. Leave the QA result **NOT RUN** until IP-Q01 executes.

**Acceptance evidence** Round-trip Unicode text, photos, task families, habits and Focus on two disposable installs. Compare counts, content and relationships. Preserve sentinel data on wrong password, tamper/truncation, unknown versions, cancelled picker/confirmation, staging exhaustion and output failure. Exercise protected restore and running/paused Focus rules.
