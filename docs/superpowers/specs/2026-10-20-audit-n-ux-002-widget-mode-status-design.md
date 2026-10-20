# UX-002 — Unknown widget privacy mode is reported as Summary

**Status:** Draft specification; implementation has not started. Proposed decisions below are reviewable defaults, not previously approved product policy.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-002](../../UI_UX_AUDIT-20261020N.md#ux-002--unknown-widget-privacy-mode-is-reported-as-summary)
**Priority / effort / confidence:** P1 — High / Low / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Settings → Home-screen widgets
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-002-widget-mode-status.md)
**Delivery phase:** 1 — P1 corrections

## Intent and success

Report widget privacy configuration as known, unconfigured or unavailable without inventing Summary mode. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

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

1. Render Summary/Details only for Known configuration. Render Not configured for a successful null read and Mode unavailable for an exception or kind mismatch.
2. Unavailable offers Retry and Configure for the same live ID. Retry rereads storage; opening configuration alone never changes the displayed mode to Summary.
3. Resume and configuration-result refresh use the same resolver. Do not write synthetic WidgetInstance defaults into storage or change the launcher rendering fallback.

## Design and contract

Keep launcher enumeration separate from configuration-read outcomes. Store the live widget ID/kind even when storage cannot provide configuration.

Add WidgetModeState: Known(instance: WidgetInstance), Unconfigured, Unavailable; add WidgetSettingsItem(id: Int, kind: WidgetKind, mode: WidgetModeState). configuration read catches Exception except CancellationException; null means Unconfigured and a kind mismatch means Unavailable.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `failureDoesNotClaimSummary` | A live details-enabled widget throws on configuration read. | Mode unavailable appears; neither Summary nor a hidden-details assurance is shown. |
| AC-2 | `successfulNullIsUnconfigured` | A live widget read returns null. | Not configured appears with Configure for that live widget. |
| AC-3 | `retryRecoversKnownDetails` | Retry changes a throwing read into showDetails=true. | Details appears for the same ID without a synthetic write. |
| AC-4 | `kindMismatchIsUnavailable` | Stored kind differs from launcher provider kind. | Mode unavailable appears; Configure retains launcher identity. |
| AC-5 | `cancelDoesNotBecomeFailure` | The read coroutine is cancelled during navigation. | Cancellation propagates and no fabricated privacy status is committed. |

## Scope boundary

No widget renderer, privacy default, storage schema or pinning-flow change.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

