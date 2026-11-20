# IP-I03 — Dependable capture drafts specification

**Priority** P1 · **Horizon** Now · **Status** SOURCE COMPLETE — QA PENDING

[Task index](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md) · [Proposal](../../20261118-Proposal.md) · [Implementation plan](../plans/2026-11-20-product-improvements-i03-capture-drafts.md) · [Final emulator QA](../plans/2026-11-20-product-improvements-q01-emulator-qa.md#qa-03)

## Intent and current behavior

Preserve an interrupted Mood or Memory draft with its original date and valid attachments until save or explicit discard.

Mood already has a durable draft store and flags for write/restore/storage failure. Memory uses SavedStateHandle for fields and PhotoDraftSession for attachment identity. Restricted capture is a separate encrypted local store and policy boundary. Source shows that Mood and Memory writes lack expected-baseline checks and that restored Memory state can adopt a newly loaded baseline. Runtime recovery still needs an isolated comparison.

## Scope

Trace new and edit flows from the normal capture routes through save/discard. Use the same draft/session identity on restoration. Add or correct only gaps found in source or in the final QA reproduction. Expose understandable restored/unsaved/saved feedback based on actual persistence or commit state.

Out of scope. This task excludes multi-draft inbox, cross-device draft sync, autosave into published records or redesign of restricted capture.

## Requirements

- **IP-I03-R1** On configuration and locale recreation, picker return/cancel and supported background process recreation from Recents, restore user-entered fields and original occurrence instant. Restore valid Memory photos in order, including crop/order metadata where already stored. Clearly indicate unavailable temporary media without removing committed media.
- **IP-I03-R2** Failed saves preserve retryable input. Double taps, interrupted saves and retry of an already applied create do not duplicate records. Persist the original authorized baseline for an edit draft. Reject stale or deleted sources transactionally for Mood, direct Memory and promoted markers, keeping input available with a conflict/reload action. Reconcile an interrupted create against its original capture ID and payload through the idempotent create path.
- **IP-I03-R3** Explicit discard clears only the active draft and exclusively owned uncommitted photos. Draft content remains behind existing access gates and authorization boundaries. Storage write failures cannot be described as durable drafts.

## Implementation approach

Align existing state paths and add bounded app-private persistence only if the Memory restoration guarantee needs it. A new general draft service would duplicate the Mood and restricted-capture mechanisms. Make cleanup conditional on actual ownership.

## Acceptance

**QA-03** Use new and edit Mood/Memory, non-current dates, valid/shared/missing photos and a sentinel saved record. Run rotate, language change, task-background process kill with Recents recreation, picker cancel, low storage, failed/duplicate save, stale edit, discard and lock/relock. Keep force-stop, task removal and device reboot outside the general SavedStateHandle recovery promise unless separately evidenced.

**Product validation** Measure restoration, lost input and duplicate events across the controlled interruption cases.

Apply the [shared implementation contract](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md#shared-implementation-contract). Runtime acceptance is owned by IP-Q01 after all handoffs.
