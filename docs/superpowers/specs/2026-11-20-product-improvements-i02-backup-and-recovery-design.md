# IP-I02 — Backup and recovery specification

**Priority** P1 · **Horizon** Now disclosure → Next encrypted archive · **Status** SOURCE COMPLETE — QA PENDING

[Task index](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md) · [Proposal](../../20261118-Proposal.md) · [Implementation plan](../plans/2026-11-20-product-improvements-i02-backup-and-recovery.md) · [Final emulator QA](../plans/2026-11-20-product-improvements-q01-emulator-qa.md#qa-02)

## Intent and baseline before IP-I02

Make the consequences of export, restore and forgotten lock credentials clear, and provide a portable encrypted archive choice.

Before IP-I02, BackupService exported a plain ZIP, validated it before producing PreparedBackup, and separated preview from replacement. BackupSettingsCard already handled sensitive-action gates and paused-Focus choices. IP-I02 is the implementation owner for the existing [F05 encrypted archive design](2026-10-26-roadmap-05-encrypted-backups.md). Its applicable verification requirements roll into IP-Q01. Treat app/source behavior and the current portable archive contract as the compatibility baseline.

## Scope

Deliver two ordered slices in this task. First clarify existing plaintext export, disabled automatic backup, local replacement, actual portable exclusions and erase-only forgotten App Lock recovery. Then add a password-protected portable envelope around the existing validated archive. Require explicit format selection, with encrypted export recommended and plaintext clearly labeled.

Out of scope. This task excludes accounts, synchronization, escrow, automatic upload or change to App Lock credential recovery.

## Requirements

- **IP-I02-R1** Before export, explain plaintext exposure and password-loss consequences for encrypted files. Show that App Lock recovery for forgotten credentials erases local data. Before restore confirmation, show validated format/counts, state that local portable data is replaced, and list exclusions verified against BACKUP_FORMAT.md. Preserve cancel and fresh App Lock authorization.
- **IP-I02-R2** Export requires an exact password confirmation for encrypted mode and follows the F05 minimum of 12 Unicode code points. Leading/trailing spaces are meaningful. Support legacy plaintext archives and portable encrypted imports on an independent install. Keep the inner ZIP schema and Room schema as they are.
- **IP-I02-R3** Authenticate the entire bounded encrypted input in private staging before ZIP validation or preview. Wrong password, corruption, unsupported format, cancellation before commit and failed staging preserve existing records. Reuse the existing replacement transaction and failure cleanup. Bound staging resources and remove temporary data on cancellation/failure/restart. Keep passwords out of saved state, logs and artifacts.
- **IP-I02-R4** Keep running/paused Focus export rules and choices, current replacement notifications and restore-generation invalidation. Label preview success as validation and report restore success only on committed replacement.

## Implementation approach

The two ordered slices are implemented: clear export, restore and recovery disclosure, followed by a compatible authenticated outer envelope that preserves existing archive validation, staging and migration logic. The frozen LUMAENC v1 streaming-AEAD contract is documented in BACKUP_FORMAT.md. Runtime and interoperability acceptance remain owned by IP-Q01.before integrating the codec.

## Acceptance

**QA-02** Round-trip Unicode text, photos, task families, habits and Focus on two disposable installs. Compare counts, content and relationships. Preserve sentinel data on wrong password, tamper/truncation, unknown versions, cancelled picker/confirmation, staging exhaustion and output failure. Exercise protected restore and running/paused Focus rules.

**Product validation** Observe independent export/restore success and whether users can explain each format, lost-password consequences and replacement before confirming.

Apply the [shared implementation contract](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md#shared-implementation-contract). Runtime acceptance is owned by IP-Q01 after all handoffs.
