# UX-014 — Habit management sits after all loaded history

**Status:** Draft specification; implementation has not started. Proposed decisions below are reviewable defaults, not previously approved product policy.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-014](../../UI_UX_AUDIT-20261020N.md#ux-014--habit-management-sits-after-all-loaded-history)
**Priority / effort / confidence:** P2 — Medium / Medium / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Habit detail
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-014-habit-detail-management.md)
**Delivery phase:** 3 — navigation, scale and consistency

## Intent and success

Keep habit management near the start of detail and render long history as individual lazy records. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

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

1. Pinned Edit remains reachable independent of loaded history. Manage/schedule follows progress/summary and precedes history.
2. Pause/Archive/Delete retain confirmations, disabled/busy guards and tracking-date semantics. Do not place destructive one-tap actions in the pinned bar.
3. Each repetition and load-more state is its own lazy item. Preserve row identities, correction callbacks and page offset/total.
4. UX-013 can later add selected-target items to the same list; avoid a second nested vertical scrolling history.

## Design and contract

Place Edit in the existing pinned detail bar and a compact schedule/Manage disclosure before history. Convert HabitHistory content into LazyListScope items with stable keys.

Extract fun LazyListScope.habitHistoryItems(logs: List<HabitLog>, total: Int, selectedLogId: UUID?, screenZone: ZoneId, onEdit: (HabitLog) -> Unit, onDelete: (UUID) -> Unit, onLoadMore: () -> Unit, historyError: Boolean = false): Unit. It replaces the eager history Column with a heading item, per-log UUID-keyed items, an error item, and a load-more item; retain existing row callbacks and tags. HabitDetailScreen supplies onEdit: () -> Unit to DetailTopBar actions and keeps management commands in HabitManagementSection.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `editStaysPinned` | Load and scroll through 500 historical repetitions. | Edit remains reachable in the pinned header without scrolling to the end. |
| AC-2 | `managePrecedesHistory` | Open detail with at least 50 logs. | Manage/schedule appears before the historical list. |
| AC-3 | `historyUsesStableLazyRows` | Load another history page while a row has focus. | Existing row identities/position stay stable and off-screen rows are not all composed eagerly. |
| AC-4 | `destructiveActionsStayConfirmed` | Open Manage then Delete/Pause/Archive. | Existing confirmation and busy guards remain; pinned Edit does not bypass them. |
| AC-5 | `correctionStillTargetsExactLog` | Correct a record after loading older pages. | The original log UUID is used; paging counts and Undo behavior remain correct. |

## Scope boundary

No broad habit screen redesign, change to scheduling rules or eager load of lifetime history.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

