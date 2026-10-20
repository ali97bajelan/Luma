# UX-026 — Weekly activity can render a nonzero value as zero

**Status:** Draft specification; implementation has not started. Proposed decisions below are reviewable defaults, not previously approved product policy.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-026](../../UI_UX_AUDIT-20261020N.md#ux-026--weekly-activity-can-render-a-nonzero-value-as-zero)
**Priority / effort / confidence:** P2 — Medium / Low / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Weekly Review activity overview and comparisons
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-026-activity-number-precision.md)
**Delivery phase:** 3 — navigation, scale and consistency

## Intent and success

Keep small positive and negative activity values distinct from zero in Review and Daily Summary. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

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

1. 0.001 remains visibly nonzero in totals and comparisons; explicit zero remains zero and missing remains absent.
2. Use the same formatter for Daily Summary and Review activity. Positive/negative deltas must not round into 0 or signed zero.
3. Keep rating averages limited to their existing two fraction digits. Do not change duration, sample count or integer-step semantics.
4. Use locale decimal/group/digit conventions and finite supported values; preserve source/unit labels and stored quantities.

## Design and contract

Extract Daily Summary's existing precision-preserving NumberFormat policy into a pure shared activity formatter. Apply it to activity totals/deltas only and retain rating-average formatting.

fun formatActivityQuantity(value: Double, locale: Locale): String uses NumberFormat.getNumberInstance(locale) with maximumFractionDigits = 340. fun formatActivityChange(current: Double?, previous: Double?, locale: Locale): String? preserves nonzero deltas, prepends localized positive sign convention and returns null if either sample is missing. Rating reviewNumber/reviewChange remain separate.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `smallActivityIsNotZero` | Format 0.001 activity in Daily Summary and Review. | Both retain a visibly nonzero localized quantity. |
| AC-2 | `zeroAndMissingStayDistinct` | Format explicit 0 and an absent metric/delta input. | Zero renders zero; absent metric keeps its absence copy and absent comparison returns null. |
| AC-3 | `smallDeltasKeepSign` | Compare 0.001 versus 0 and the reverse. | The localized deltas remain nonzero with the correct positive/negative direction. |
| AC-4 | `ratingPrecisionIsUnchanged` | Format rating average 3.456 alongside activity 0.001. | Rating remains two fraction digits; activity keeps necessary precision. |
| AC-5 | `localeAndRangeArePreserved` | Format 0.0000001 and 1234567.125 in EN/FR/FA. | Digits/separators follow locale, values stay nonzero and no quantity conversion or storage write occurs. |

## Scope boundary

No precision change for mood ratings, new unit conversions, scientific-chart feature or data-model migration.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

