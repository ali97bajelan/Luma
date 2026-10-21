# UX-023 — Expanded disclosures lose their subject and state

**Status:** Implementation complete; automated, Gradle, and emulator verification pending at the user's request. The implementation follows the design decisions below.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-023](../../UI_UX_AUDIT-20261020N.md#ux-023--expanded-disclosures-lose-their-subject-and-state)
**Priority / effort / confidence:** P2 — Medium / Low / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Usage/Health explanations and Daily summary details
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-023-named-stateful-disclosures.md)
**Delivery phase:** 3 — navigation, scale and consistency

## Intent and success

Keep every disclosure's subject and expanded/collapsed state available in both states. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

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

1. Expanded label is Hide: [subject] using localized composition; collapsed label keeps its subject. Set expanded/collapsed stateDescription consistently.
2. Retain rememberSaveable expansion and existing tags; do not put child content into the toggle's merged semantic node.
3. Inventory every DetailsDisclosure call, remove keepTitle arguments and preserve expanded content, independent toggles and focus.
4. Subject copy remains useful for usage/health import explanations and summary details when several disclosures are on screen.

## Design and contract

Make the existing DetailsDisclosure named-state behavior unconditional and migrate any explicit callers relying on the generic Hide details variant.

DetailsDisclosure(@StringRes title: Int, tag: String, content: @Composable () -> Unit): Unit removes keepTitle and always sets localized stateDescription. Expanded label uses existing review_hide_details with the subject; collapsed label uses title.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `collapsedHasSubjectAndState` | Render Import details collapsed. | The toggle names Import details and exposes collapsed state. |
| AC-2 | `expandedKeepsSubject` | Expand Import details. | The toggle names its subject in Hide text and exposes expanded state. |
| AC-3 | `multipleDisclosuresAreDistinct` | Render usage, health and summary disclosures together. | Each toggle has its own subject; no ambiguous generic Hide details control remains. |
| AC-4 | `rotationPreservesExpansion` | Expand one disclosure then recreate. | The same disclosure stays expanded and other disclosures keep their own state. |
| AC-5 | `focusAndTranslationsRemainUsable` | Toggle using keyboard/TalkBack in EN/FR/FA. | Focus stays on the named toggle; child content remains independently navigable. |

## Scope boundary

No new disclosure subsystem, page-wide live region or change to stored integration preferences.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.
