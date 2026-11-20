# IP-I02 — Backup and recovery Implementation Plan

> **For agentic workers** Use the applicable Superpowers implementation skill when this plan is authorized for execution. Steps use checkbox syntax for tracking.

**Goal** Make the consequences of export, restore and forgotten lock credentials clear, and provide a portable encrypted archive choice.

**Architecture** A compatible authenticated outer envelope preserves existing archive validation, staging and migration logic. Disclosure-only is the first shippable slice and leaves encrypted portability outstanding. Freeze one maintained streaming AEAD format in BACKUP_FORMAT.md under the existing F05 requirements before integrating the codec.

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

Disclosure before archive integration. Reuse the existing F05 portable encryption design and existing backup validation and App Lock flows. Apply the [shared implementation contract](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md#shared-implementation-contract). Record source handoff before IP-Q01.

## Review focus

Failures at stream finalization or before data commit must leave the previous archive/records intact where storage-provider capabilities permit cleanup. Capture provider limits explicitly.

## Implementation checklist

- [ ] **1.** Trace actual archive members, exclusions, restore boundaries and App Lock prompts. Add the disclosure-only UX using the current BackupPreview and protected operation flow.
- [ ] **2.** Freeze the [F05 design](../specs/2026-10-26-roadmap-05-encrypted-backups.md)’s envelope identifier/version, byte layout, authenticated header, maintained streaming AEAD dependency/version, portable password encoding, KDF parameters, accepted input and staging bounds, and forward-version behavior in BACKUP_FORMAT.md. Use F05’s proposed PBKDF2-HMAC-SHA256 floor (600,000 iterations, fresh random salt of at least 16 bytes and 256-bit key) as the draft compatibility contract and verify runtime cost in QA.
- [ ] **3.** Implement stream dispatch and typed password-required/invalid outcomes at the backup boundary. Keep existing plaintext export/prepare callers compatible. Produce a PreparedBackup only after complete authentication when encrypted and existing ZIP/integrity validation for both formats.
- [ ] **4.** Wire the format choice, transient password entry/re-entry and sanitized errors. Use generic export metadata, and clear secrets and staging on success/failure/cancel/departure as applicable. Preserve old archive support, Focus choices and fresh restore authorization.
- [ ] **5.** Review source and localization against IP-I02-R1, IP-I02-R2, IP-I02-R3, IP-I02-R4. Record the changed files, interface decisions, risks and source evidence for IP-Q01.

## QA handoff

- [ ] Map `encryptedInputAuthenticatesBeforePreview`, `legacyAndEncryptedRoundTripsOnIndependentInstalls`, `failureLeavesSentinelAndCleansStaging` to [QA-02](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-02) for the final verification task. Extend the existing test surfaces where appropriate. BackupViewModelTest; BackupSettingsFlowTest; BackupPersistenceTest; BackupCodecTest.
- [ ] Map the predecessor [F05 verification cases](../../feature-roadmap/05-encrypted-backups-qa.md) to QA-02 and record source status as **SOURCE COMPLETE — QA PENDING** after handoff and leave the QA result **NOT RUN** until IP-Q01 executes.

**Acceptance evidence** Round-trip Unicode text, photos, task families, habits and Focus on two disposable installs. Compare counts, content and relationships. Preserve sentinel data on wrong password, tamper/truncation, unknown versions, cancelled picker/confirmation, staging exhaustion and output failure. Exercise protected restore and running/paused Focus rules.
