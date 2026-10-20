# UX-005 — Interrupted Mood deletion has no reconciliation path

**Status:** Draft specification; implementation has not started. Proposed decisions below are reviewable defaults, not previously approved product policy.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-005](../../UI_UX_AUDIT-20261020N.md#ux-005--interrupted-mood-deletion-has-no-reconciliation-path)
**Priority / effort / confidence:** P2 — Medium / Medium / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Mood editor
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-005-mood-delete-reconciliation.md)
**Delivery phase:** 2 — recovery and truthful feedback

## Intent and success

Resolve interrupted Mood deletion to an honest deleted, still-present or unavailable outcome. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

The audit is source evidence, not reproduced runtime acceptance. Its recommendations are input to this draft, not executable instructions. All tests, runtime exercises and commits in the plan are future work.

## Dependencies

[UX-004](./2026-10-20-audit-n-ux-004-mood-note-durability-design.md)

## Global constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Behavior and policy

1. If intent persistence fails, do not call delete. Show a retryable deletion-preparation failure with retained content.
2. If get(id) returns null after retained intent, complete as deleted. A successful read returning a record permits explicit Retry delete; do not auto-repeat a destructive command merely because the process restarted.
3. Read failure means unknown outcome with Retry status check. Missing data without a matching deletion intent is an honest missing-record state, never deletion success.
4. Persist completion = MoodDraftCompletion(id, deleted = true) before clearing pendingDeleteId. Retain the completion snapshot until the caller acknowledges its result; load checks it before normal dependencies. Repeated restoration produces the same result and cannot recreate the deleted entry.

## Design and contract

Record pendingDeleteId in the durable Mood draft snapshot before repository.delete. On restoration reconcile that intent before emotion preparation or normal record loading.

Reuse UX-004 MoodDraftSnapshot.pendingDeleteId: EventId?. MoodEditorViewModel.delete(): Unit durably writes intent before deletion; load(): Unit checks pending intent via MoodRepository.get(id) before prepareEmotions(). Completion keeps existing completed/deleted state for UX-010 to translate.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `committedDeleteIsReconciled` | Delete commits but completion delivery is lost; recreate with intent. | The editor completes deleted without LOAD error or a second delete. |
| AC-2 | `intentFailurePreventsDelete` | Draft-store intent persistence throws. | Repository.delete is never called and note content stays available. |
| AC-3 | `stillPresentRequiresExplicitRetry` | Restore matching intent while repository.get returns the record. | Show retryable deletion state; no automatic second delete occurs. |
| AC-4 | `unrelatedMissingIsNotDeleted` | Load a missing record without deletion intent, or with intent for another ID. | Show missing source; do not report successful deletion. |
| AC-5 | `statusReadFailureRemainsUnknown` | Reconciliation read throws, then retry returns null; emotions also fail. | Unknown outcome is recoverable; successful null reconciliation completes deletion independently of emotions. |

## Scope boundary

No automatic recreation, broad repository deletion rewrite or assumption that exceptions imply an unchanged database.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

