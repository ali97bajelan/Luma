# UX-008 — Retained task errors can crowd out the task list

**Status:** Draft specification; implementation has not started. Proposed decisions below are reviewable defaults, not previously approved product policy.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-008](../../UI_UX_AUDIT-20261020N.md#ux-008--retained-task-errors-can-crowd-out-the-task-list)
**Priority / effort / confidence:** P2 — Medium / Low / Medium (copied from the static audit; not a delivery estimate).
**Affected surface:** Full Tasks list
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-008-task-recovery-list.md)
**Delivery phase:** 2 — recovery and truthful feedback

## Intent and success

Keep tasks and every retained recovery action reachable when many task operations fail. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

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

1. All retained feedback remains available in the task list's scrolling region, including off-filter failures. No count-based dropping or automatic dismissal.
2. Keep Undo as a named stable item near the start of the list. Scroll/focus to newly available recovery when needed without resetting scroll on every emission.
3. Filters and tab changes preserve command identities. Task paging triggers remain based on task records rather than inserted recovery-item indexes.

## Design and contract

Place off-page recovery cards and Undo as stable items in the existing LazyColumn. Keep only bounded controls such as tabs and filters outside scrolling content.

Use lazy item keys prefixed recovery:<existing-action-id>, undo:<existing-undo-id>, bucket:<bucket>, task:<task-id>. Reuse existing Retry/Open/Dismiss callbacks; do not generate replacement operation IDs during recomposition.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `manyFailuresDoNotHideTasks` | Render 12 off-page failures at 200% text in landscape. | Scroll reaches every recovery action and at least one task without clipped pinned content. |
| AC-2 | `undoRemainsDiscoverable` | A completed task moves off-page and exposes Undo. | Undo is a reachable stable lazy item with the original token. |
| AC-3 | `filterChangeKeepsActions` | Switch Open/Completed and due filters with retained failures. | No command disappears or changes identity. |
| AC-4 | `recompositionKeepsScroll` | Emit an unrelated task change while scrolled. | Stable item identity preserves the current content position and focus. |
| AC-5 | `feedbackDoesNotAdvancePaging` | Insert recovery items before a 50-record task page. | Load-more requests use task page boundaries, without skipped or duplicate tasks. |

## Scope boundary

No command retention cap, task paging rewrite or time-limited replacement of persistent recovery.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

