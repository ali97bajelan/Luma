# UX-020 — Entry card accessibility actions promise Edit but open details

**Status:** Draft specification; implementation has not started. Proposed decisions below are reviewable defaults, not previously approved product policy.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-020](../../UI_UX_AUDIT-20261020N.md#ux-020--entry-card-accessibility-actions-promise-edit-but-open-details)
**Priority / effort / confidence:** P2 — Medium / Low / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Timeline, Search and Review sources
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-020-entry-card-action-labels.md)
**Delivery phase:** 2 — recovery and truthful feedback

## Intent and success

Make an entry card's announced action match its supplied navigation destination. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

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

1. Timeline/Search/Review-source callers that navigate to detail provide Open entry or their existing specific Open label.
2. Direct-editor callers provide the existing Mood/Memory Edit label. Do not change destination routing merely to fit a label.
3. Bookmark/promote/edit-marker controls keep independent names and state. A card label must not swallow their separate actions.
4. The required parameter prevents future implicit record-type assumptions; no fallback that infers destination from editable content.

## Design and contract

Require callers to provide the card action label instead of deriving it solely from record type. Preserve separate bookmark/marker actions.

EventCard gains required @StringRes openActionLabel: Int adjacent to onOpen. Use that resource for clickable(onClickLabel = stringResource(openActionLabel), onClick = onOpen). Migrate every EventCard call found by rg, including any additional callers beyond the listed starting files.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `detailCardSaysOpen` | Render editable Mood/Memory in Timeline, Search and Review sources. | Card click action says Open and opens details. |
| AC-2 | `directEditorCardSaysEdit` | Render a Today card wired to its editor. | Action says Edit and opens that editor. |
| AC-3 | `markerActionRemainsSeparate` | Card also offers a bookmark/memory action. | The marker control retains its own label and callback. |
| AC-4 | `nonEditableEntryIsAccurate` | Render usage/activity or another read-only event. | Action describes its detail opening; it never promises content editing. |
| AC-5 | `allCallersSupplyLocalizedLabels` | Compile every EventCard caller and inspect EN/FR/FA semantics. | No caller relies on inferred default action text and labels match their destinations. |

## Scope boundary

No navigation hierarchy change, removal of direct-edit shortcuts or merging marker actions into card clicks.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

