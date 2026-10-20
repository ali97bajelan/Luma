# UX-016 — Review source page labels reverse chronological direction

**Status:** Implementation complete; acceptance pending. Gradle and emulator checks were not run per user instruction.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-016](../../UI_UX_AUDIT-20261020N.md#ux-016--review-source-page-labels-reverse-chronological-direction)
**Priority / effort / confidence:** P2 — Medium / Low / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Weekly Review → contributing sources
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-016-review-source-pagination.md)
**Delivery phase:** 2 — recovery and truthful feedback

## Intent and success

Make Review source page controls describe their actual movement without changing Search ordering. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

The audit is source evidence, not runtime acceptance. Implementation is in place, but the specified test runs, runtime exercises and commit remain pending.

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

1. Use Previous page when decreasing offset and Next page when increasing offset, independent of chronological interpretation.
2. Preserve oldest-first explanatory text, page size 50, boundaries, stable event keys and Search's descending labels.
3. A language change updates both controls; result count and no-sources/error behavior remain as implemented.

## Design and contract

Introduce dedicated source-pagination resources and keep the existing oldest-first repository query and 50-record offsets.

Add R.string.review_source_previous_page = Previous page and R.string.review_source_next_page = Next page with EN/FR/FA translations. Existing review_source_previous and review_source_next test tags and offset arithmetic remain.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `firstPageHasNextOnly` | Open 101 oldest-first source records at offset 0. | Next page exists; Previous page is absent; entries 1–50 are shown. |
| AC-2 | `middlePageDirectionsAgree` | Go to offset 50 then use Previous page. | Records 51–100 then 1–50 appear, with correctly named controls. |
| AC-3 | `lastPageHasPreviousOnly` | Go to offset 100. | Record 101 is shown; Next page is absent. |
| AC-4 | `searchWordingIsUnchanged` | Open Search after navigating Review sources. | Search retains its existing descending-order labels and behavior. |
| AC-5 | `localizedLabelsFit` | Inspect EN/FR/FA at 200% font. | Previous/Next page are translated, reachable and do not rely on unmirrored direction icons. |

## Scope boundary

No SQL ordering change, repository API change or shared Search string replacement.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

