# UX-022 — Visible headings are missing semantic heading roles

**Status:** Draft specification; implementation has not started. Proposed decisions below are reviewable defaults, not previously approved product policy.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-022](../../UI_UX_AUDIT-20261020N.md#ux-022--visible-headings-are-missing-semantic-heading-roles)
**Priority / effort / confidence:** P2 — Medium / Low / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Today, Timeline, Search, Habit history, Daily summary
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-022-semantic-headings.md)
**Delivery phase:** 3 — navigation, scale and consistency

## Intent and success

Make meaningful visual page and section headings available to assistive heading navigation. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

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

1. Mark Today/Timeline/Search page titles and meaningful Recent entries/Habit history/Daily summary sections.
2. Preserve existing headings in More, Settings, editor/detail bars and Review. Never create duplicate heading nodes for one visible title.
3. Do not mark each metric, row date, card title or value as a heading. Heading order follows visual reading order in RTL as well as LTR.
4. This can ship before UX-021; later page migration must reuse heading semantics instead of regressing them.

## Design and contract

Add a small heading wrapper using existing Material typography and heading semantics, then migrate the audited title/section sites.

@Composable AppSectionHeading(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.titleLarge): Unit renders Text with Modifier.semantics { heading() }. Existing test tags pass through modifier. Shared PageHeader from UX-021 should use the same heading semantics when present.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `pageTitlesAreHeadings` | Inspect Today, Timeline and Search. | Each visible page title exposes one heading node. |
| AC-2 | `meaningfulSectionsAreHeadings` | Inspect Recent entries, Habit history and Daily summary. | Section titles participate in heading traversal in visual order. |
| AC-3 | `metricsAreNotOvermarked` | Inspect individual metric values, entry cards and dates. | These are not added as headings solely because they use bold/larger text. |
| AC-4 | `collapsedSectionsHaveNoGhostHeadings` | Collapse summary/history disclosures where applicable. | Only visible meaningful headings remain navigable. |
| AC-5 | `rtlHeadingTraversalIsUsable` | Use TalkBack heading navigation in Persian at 200% font. | Traversal follows the visible hierarchy without duplicate or skipped page titles. |

## Scope boundary

No wholesale typography redesign, invented numeric heading levels or marking every card as a section.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

