# UX-012 — Context selection requires scanning the entire catalog

**Status:** Draft specification; implementation has not started. Proposed decisions below are reviewable defaults, not previously approved product policy.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-012](../../UI_UX_AUDIT-20261020N.md#ux-012--context-selection-requires-scanning-the-entire-catalog)
**Priority / effort / confidence:** P2 — Medium / Medium / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Capture person/place selectors; People & Places
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-012-searchable-context-selection.md)
**Delivery phase:** 3 — navigation, scale and consistency

## Intent and success

Make people/place selection and catalog browsing searchable without composing the entire catalog as chips. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

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

1. Query matches case-insensitive names and already-available descriptive context. Duplicate names retain distinct UUIDs and show an existing non-sensitive distinguishing field when available; never fabricate context.
2. A modal keeps working selection until Done; Cancel/Back discards modal-only changes. Clear empties working selection. Selected items remain visible regardless of query.
3. People permit 100 selections, places one; prevent new selections past the limit and keep removal available. New creation uses the existing onCreate flow; if UX-011 is present its returned attachment joins the durable draft.
4. Use LazyColumn stable UUID keys, empty/no-match/error states, saveable query and modal focus restoration. Initially filter the existing observed lists locally; database paging/indexing requires later measured need.

5. New first applies the current working selection with Done semantics and dismisses the picker, then opens creation. A returned created attachment therefore cannot be overwritten by stale modal working state.

## Design and contract

Replace expanded option FlowRows with a shared searchable lazy picker. Retain selected chips outside filtered results and reuse query/filter behavior in People and Places lists.

ContextOption(id: UUID, label: String, secondary: String?) and @Composable ContextOptionPicker(title: String, options: List<ContextOption>, selectedIds: Set<UUID>, maxSelections: Int, onApply: (Set<UUID>) -> Unit, onCreate: () -> Unit, onDismiss: () -> Unit): Unit. PersonPicker adapts onApply to existing per-ID callbacks; PlacePicker uses maxSelections = 1.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `largeCatalogIsSearchable` | Render 2,000 entries and query an item near the end. | Only matching lazy rows are shown; selecting uses its UUID without eagerly composing every option. |
| AC-2 | `duplicateNamesStayDistinct` | Two matching entries share a name. | Each has a distinct stable selection identity and available contextual text. |
| AC-3 | `queryDoesNotDropSelection` | Select an item, change the query, Clear then Cancel; reopen and Done. | Query preserves working selections; Cancel restores original draft; Done applies working IDs. |
| AC-4 | `limitsAndMissingSelectionAreSafe` | Select 100 people or one place; include a selected unavailable ID. | Limit blocks additions but allows removal; unavailable IDs remain honestly labelled until explicitly removed. |
| AC-5 | `catalogAndModalRecovery` | Recreate with query active, open New, return and dismiss with TalkBack. | Query/tab persist, creation remains reachable and focus returns to the invoking picker. |

## Scope boundary

No search-index expansion, copied Search filter limits, whole-catalog database migration or speculative multi-pane UI.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

