# UX-015 — Habit failures display the wrong operation's message

**Status:** Draft specification; implementation has not started. Proposed decisions below are reviewable defaults, not previously approved product policy.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-015](../../UI_UX_AUDIT-20261020N.md#ux-015--habit-failures-display-the-wrong-operations-message)
**Priority / effort / confidence:** P2 — Medium / Low / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Habit detail
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-015-habit-operation-errors.md)
**Delivery phase:** 2 — recovery and truthful feedback

## Intent and success

Give history, correction and whole-habit deletion failures accurate operation-specific messages and recovery. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

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

1. History failure says Couldn't load older repetitions beside Load older and retries the same offset without dropping loaded rows.
2. Delete failure uses habit-deletion wording. Reconcile get(habitId): missing completes deletion, present offers explicit retry, read failure leaves outcome unknown.
3. Only a real correction failure uses correction copy and retains the correction draft. Clear an error on success of its own operation, never because an unrelated request succeeds.
4. Prevent stale asynchronous failures from overwriting a newer operation/target. Errors must not contain raw exception text or claim private data survived an unknown result.

## Design and contract

Replace HabitDetailUiState.actionError's untyped string with a typed operation/target. Render errors beside their owning actions rather than a universal correction error.

HabitDetailOperation { HISTORY, DELETE }; HabitDetailActionError is sealed: History(offset: Int), Delete(habitId: UUID, outcomeUnknown: Boolean). Replace detail.actionError with actionErrors: Map<HabitDetailOperation, HabitDetailActionError>. Keep correctionError: HabitCorrectionError? as the correction-only channel; remove correction failures from the generic detail channel.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `historyErrorNamesHistory` | Loading offset 50 throws. | Older-repetitions copy appears at Load older; existing history stays and retry uses offset 50. |
| AC-2 | `deleteErrorNamesDeletion` | Whole-habit delete throws with the habit still present. | Deletion-specific recovery appears with no claim that a correction was retained. |
| AC-3 | `committedDeleteReconciles` | deleteHabit commits then throws; get returns null. | Deletion completes; no repeated destructive write is required. |
| AC-4 | `correctionErrorKeepsDraft` | A repetition correction fails. | Correction-specific error appears and selected correction values remain. |
| AC-5 | `unrelatedSuccessCannotEraseError` | History fails while a management action succeeds, or an old failure arrives late. | Only its matching operation clears/replaces feedback; unknown deletion outcome remains honest. |

## Scope boundary

No generic exception-to-user messaging, scheduling-rule changes or unconditional assumption that failed writes did not commit.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

