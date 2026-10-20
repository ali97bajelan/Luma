# UX-007 — A conflicted Today task cannot be opened for review

**Status:** Implemented; acceptance pending. Regression coverage has been added but not run. Proposed decisions below remain reviewable defaults, not previously approved product policy.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-007](../../UI_UX_AUDIT-20261020N.md#ux-007--a-conflicted-today-task-cannot-be-opened-for-review)
**Priority / effort / confidence:** P2 — Medium / Low / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Today tasks
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-007-task-conflict-opening.md)
**Delivery phase:** 2 — recovery and truthful feedback

## Intent and success

Let users open an existing task in conflict while keeping unsafe completion actions disabled. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

The audit is source evidence, not reproduced runtime acceptance. Its recommendations are input to this specification, not executable instructions. Gradle/emulator checks and runtime exercises remain pending; no commit was created.

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

1. An existing CONFLICT task has an enabled opening control and disabled completion checkbox. Visible conflict feedback includes the same Open affordance as retained off-preview conflicts.
2. MISSING tasks cannot be opened or mutated. Pending writes continue to block duplicate completion; do not relax revision checks.
3. Dismiss only removes feedback; it never silently retries or changes completion. Give focus to the editor/detail destination using the existing navigation route.

## Design and contract

Split task-opening eligibility from mutation eligibility in TodoRow and all callers. Use the same Open recovery action for visible and off-preview conflicts.

TodoRow replaces enabled: Boolean with canOpen: Boolean and canMutate: Boolean; pending remains an independent guard. onOpen is available for existing conflict targets, while onSetCompleted remains blocked by conflict/pending. Missing targets have canOpen=false.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `existingConflictCanOpen` | Render an existing task with CONFLICT and tap Open. | The matching editor opens; completion remains disabled. |
| AC-2 | `missingTaskCannotOpen` | Render MISSING feedback. | Neither task open nor completion can dispatch. |
| AC-3 | `pendingDoesNotDuplicateCompletion` | Tap completion repeatedly during a pending request. | No extra mutation is issued. |
| AC-4 | `visibleAndRetainedRecoveryAgree` | Move a conflicted task out of the preview. | Both presentations navigate to the same task ID. |
| AC-5 | `dismissDoesNotWrite` | Dismiss CONFLICT without opening. | Only feedback changes; repository mutation call count stays unchanged. |

## Scope boundary

No optimistic conflict overwrite, task revision-policy change or repository mutation changes.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

