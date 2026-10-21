# UX-025 — Restore confirmation has an unbounded warning body

**Status:** Source implementation complete; automated, Gradle, and emulator verification were not run at the user's request.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-025](../../UI_UX_AUDIT-20261020N.md#ux-025--restore-confirmation-has-an-unbounded-warning-body)
**Priority / effort / confidence:** P2 — Medium / Low / Medium (copied from the static audit; not a delivery estimate).
**Affected surface:** Restore replacement preview
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-025-restore-dialog-reachability.md)
**Delivery phase:** 2 — recovery and truthful feedback

## Intent and success

Keep complete restore-replacement warnings and confirm/cancel controls reachable in short or large-text layouts. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

The audit is source evidence, not reproduced runtime acceptance. Its recommendations are input to this draft, not executable instructions. Source implementation is complete; automated tests, runtime exercises and commits in the plan remain pending at the user's request.

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

1. Preserve record counts/date, complete replacement warning and conditional zero-task warning with their existing meaning.
2. Use available window height rather than a fixed absolute maximum that assumes portrait. Let action labels wrap/stack; never reduce text size to fit.
3. Confirm/cancel remain distinct reachable targets of at least 48 dp; Back/outside dismissal keeps existing safe cancel semantics.
4. Reachability does not require a forced scroll-to-end gate. The task must prove every warning can be inspected before users choose confirmation.

## Design and contract

Constrain the existing AlertDialog content body and make its warning/count content vertically scrollable. Keep actions in dialog action slots outside the scrolling body.

BackupCardContent retains existing onConfirmRestore/onCancelRestore callbacks. Dialog body uses a remembered vertical scroll state within dialog-measured available height; warnings keep separate semantics nodes and actions remain outside the body scroll.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `allWarningsReachable` | Restore preview includes counts/date, replacement warning and zero-task warning. | Every warning can be scrolled into view. |
| AC-2 | `actionsStayReachable` | Use a short landscape viewport with 200% font. | Confirm and Cancel remain visible/reachable outside body scroll. |
| AC-3 | `longTranslationsFit` | Show French and Persian preview warnings. | Text wraps without clipping or shrinking and traversal order remains logical. |
| AC-4 | `dismissDoesNotRestore` | Press Back, tap Cancel or dismiss as currently supported. | Prepared restore is cancelled safely and no replacement commit occurs. |
| AC-5 | `warningMeaningIsPreserved` | Compare nonzero-task and zero-task preview fixtures. | All original consequences and conditional warnings remain accurate; no destructive action is implied by scrolling. |

## Scope boundary

No replacement-policy change, warning removal, forced read acknowledgement or automatic restore.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.
