# UX-010 — Deleting an entry can return to its missing detail page

**Status:** Implementation complete; verification pending at the user's request. The implementation follows the design decisions below.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-010](../../UI_UX_AUDIT-20261020N.md#ux-010--deleting-an-entry-can-return-to-its-missing-detail-page)
**Priority / effort / confidence:** P2 — Medium / Medium / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Event detail → Mood/Memory/Journal editor
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-010-deleted-detail-navigation.md)
**Delivery phase:** 3 — navigation, scale and consistency

## Intent and success

Return deletion to the originating collection while preserving the source detail after memory unmarking. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

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

1. Translate Mood deleted, Journal deleted and direct Memory removal to Deleted. Promoted-memory marker removal produces Unmarked and leaves its underlying event intact.
2. Deleted removes matching generic/specialized detail destinations and returns to the original Timeline/Search/Review/catalog source with its saved state.
3. Saved and Unmarked leave a surviving source detail available. A different event's detail, primary-tab state and unrelated navigation entries remain intact.
4. Completion is delivered once per editor back-stack entry and survives recreation through primitive saved result fields; do not encode opaque Kotlin objects directly into SavedStateHandle.

## Design and contract

Introduce typed editor outcomes carrying EventId and use identity-aware back-stack cleanup. Handle generic event_detail and specialized journal_detail destinations through the same result handler.

sealed interface EntryEditorResult has Saved(id: EventId), Deleted(id: EventId), Unmarked(id: EventId). Editor completion callbacks become (EntryEditorResult) -> Unit. A navigation helper handles the result after popping the editor and removes only a matching source-detail destination exposed beneath it.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `deletedGenericDetailIsRemoved` | From Timeline/Search/Review open event_detail, edit and delete. | The source collection returns and Back never reveals that deleted detail. |
| AC-2 | `journalSpecializedDetailIsRemoved` | Delete from journal_detail or generic event_detail. | Both paths remove only the matching source detail. |
| AC-3 | `unmarkKeepsUnderlyingDetail` | Remove a promoted Mood/Journal memory marker. | Unmarked is returned; the existing source detail remains readable. |
| AC-4 | `savedOrUnrelatedDetailSurvives` | Save without deletion or expose a detail with another EventId. | No unrelated or surviving source destination is removed. |
| AC-5 | `completionIsConsumedOnce` | Recreate after a committed deletion before navigation finishes. | One outcome is consumed; navigation does not pop a second unrelated destination. |

## Scope boundary

No whole-stack reset, source-list refresh rewrite or deletion of the source event when only a memory marker is removed.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.
