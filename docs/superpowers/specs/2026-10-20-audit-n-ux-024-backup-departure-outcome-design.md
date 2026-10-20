# UX-024 — Leaving during backup has no explicit operation outcome

**Status:** Draft specification; implementation has not started. Proposed decisions below are reviewable defaults, not previously approved product policy.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-024](../../UI_UX_AUDIT-20261020N.md#ux-024--leaving-during-backup-has-no-explicit-operation-outcome)
**Priority / effort / confidence:** P2 — Medium / Medium / Medium (copied from the static audit; not a delivery estimate).
**Affected surface:** Backup and restore
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-024-backup-departure-outcome.md)
**Delivery phase:** 3 — navigation, scale and consistency

## Intent and success

Give export, restore preparation and protected restore an explicit navigation/cancellation outcome. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

The audit is source evidence, not reproduced runtime acceptance. Its recommendations are input to this draft, not executable instructions. All tests, runtime exercises and commits in the plan are future work.

## Dependencies

None. Coordinate overlapping files using the index's integration order.

## Global constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Behavior and policy

1. Toolbar Back and system/predictive Back use the same guard. Export/preparation offers Stay or Cancel and leave. Dialog copy explains cancellation and that some document providers may retain an incomplete file.
2. After confirmed cancellation, cancel/join the export/preparation job and close streams/candidates before navigating. Attempt provider-supported deletion of the newly created export document; a failed/unsupported cleanup yields an explicit incomplete-file outcome.
3. Restore commit remains NonCancellable. During RESTORING, Back reports Restore in progress and stays; no cancel/leave action claims to cancel the transaction.
4. If work completes while the leave dialog is open, preserve actual completion and avoid deleting a completed export. Retain cancellation/completion feedback at the source settings entry; never report a cancelled export as success.
5. Process death is distinct from supported in-app cancellation: do not promise that route-owned work survives it. Reopening must not display stale success; destination may require user inspection after OS termination.

## Design and contract

Keep route-owned work but intercept Back before leaving. Cancellable export/preparation requires an explicit cancel-and-leave decision; await closure/cleanup before navigation. Committed restore remains protected and reports completion before leaving.

BackupOperation { IDLE, EXPORTING, PREPARING, RESTORING, CANCELLING }; BackupUiState gains operation. Track the active Job. export(open: () -> OutputStream, discardOutput: suspend () -> Boolean) records cleanup. cancelAndAwait(): BackupLeaveResult suspends until the active cancellable job/stream closes and cleanup completes; results CANCELLED_CLEAN, CANCELLED_OUTPUT_MAY_REMAIN, COMPLETED. Navigation serializes the leave result into the source entry's saved-state feedback.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `backDuringExportAsks` | Use toolbar or system Back while an export stream is open. | Both show Stay/Cancel and leave; navigation has not popped the work owner. |
| AC-2 | `confirmedCancellationClosesAndCleans` | Confirm leave with deletion-supported provider. | Job is cancelled/joined, stream closes, destination cleanup finishes and cancellation outcome is retained. |
| AC-3 | `cleanupFailureIsExplicit` | Provider cannot delete the incomplete document. | Departure feedback says an incomplete backup may remain; no success is reported. |
| AC-4 | `restoreCannotBeCancelledByBack` | Press Back during protected restore commit. | The route remains, the commit continues and its real result is shown. |
| AC-5 | `completionRacePreservesBackup` | Export succeeds while leave confirmation is open; then confirm leave. | Completed file is not deleted; completion result wins and prepared resources are closed exactly once. |

## Scope boundary

No new background worker, process-death survival promise, silent cancellation or weakening of restore consent/NonCancellable commit.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

