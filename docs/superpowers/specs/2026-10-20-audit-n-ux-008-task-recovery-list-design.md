# UX-008 — Retained task errors can crowd out the task list

**Status:** Implemented; AC-1 verified 1/1 on API 36 (23 November 2026). AC-2–AC-5 and accessibility/device-matrix acceptance remain pending. Proposed decisions below remain reviewable defaults, not previously approved product policy.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-008](../../UI_UX_AUDIT-20261020N.md#ux-008--retained-task-errors-can-crowd-out-the-task-list)
**Priority / effort / confidence:** P2 — Medium / Low / Medium (copied from the static audit; not a delivery estimate).
**Affected surface:** Full Tasks list
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-008-task-recovery-list.md)
**Delivery phase:** 2 — recovery and truthful feedback

## Intent and success

Keep tasks and every retained recovery action reachable when many task operations fail. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

The audit is source evidence, not reproduced runtime acceptance. Its recommendations are input to this specification, not executable instructions. AC-1 now passes its exact connected regression after the narrow-landscape, large-text failure was fixed. AC-2–AC-5 and device/accessibility exercises remain pending; no commit was created.

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

Keep search, filters, loading feedback, recovery cards and task rows in a scrollable content region when fixed controls would leave no usable list viewport. At constrained viewports and large text, the controls may scroll with the rows in one LazyColumn. While the list is loading with no data, render the controls in a separate scrollable fallback and leave the keyed LazyList uncomposed so its saved position is not clamped. Keep off-page recovery and Undo as stable lazy items.

Use lazy item keys prefixed recovery:<existing-action-id>, undo:<existing-undo-id>, bucket:<bucket>, task:<task-id>. Include any leading controls item in focus-restoration indexes. Reuse existing Retry/Open/Dismiss callbacks; do not generate replacement operation IDs during recomposition.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result | Current result |
|---|---|---|---|---|
| AC-1 | `manyFailuresDoNotHideTasks` | Render 12 off-page failures at 200% text in landscape. | Scroll reaches every recovery action and at least one task without clipped pinned content. | PASS 1/1 on API 36; [rerun JUnit](../../validation/product-improvements/2026-11-23/qa-01-TodoAccessibilitySemanticsTest-manyFailuresDoNotHideTasks-rerun.junit.xml). |
| AC-2 | `undoRemainsDiscoverable` | A completed task moves off-page and exposes Undo. | Undo is a reachable stable lazy item with the original token. | NOT RUN |
| AC-3 | `filterChangeKeepsActions` | Switch Open/Completed and due filters with retained failures. | No command disappears or changes identity. | NOT RUN |
| AC-4 | `recompositionKeepsScroll` | Emit an unrelated task change while scrolled. | Stable item identity preserves the current content position and focus. | NOT RUN |
| AC-5 | `feedbackDoesNotAdvancePaging` | Insert recovery items before a 50-record task page. | Load-more requests use task page boundaries, without skipped or duplicate tasks. | NOT RUN |

## Scope boundary

No command retention cap, task paging rewrite or time-limited replacement of persistent recovery.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. The exact AC-1 connected test passes on API 36 in the recorded 640×320 dp, font-scale-2.0 layout. AC-2–AC-5, actual TalkBack speech/focus, and the broader locale/theme/viewport matrix remain pending and are not implied by AC-1. A corrective task closes only after its remaining behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

