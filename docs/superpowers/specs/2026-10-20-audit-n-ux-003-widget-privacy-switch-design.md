# UX-003 — The widget privacy switch has no accessible name

**Status:** Draft specification; implementation has not started. Proposed decisions below are reviewable defaults, not previously approved product policy.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-003](../../UI_UX_AUDIT-20261020N.md#ux-003--the-widget-privacy-switch-has-no-accessible-name)
**Priority / effort / confidence:** P1 — High / Low / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Widget configuration
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-003-widget-privacy-switch.md)
**Delivery phase:** 1 — P1 corrections

## Intent and success

Expose the widget details opt-in as one labelled, operable switch with its privacy explanation. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

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

1. Merge Show names and row actions and its existing privacy explanation into one accessible control.
2. Expose checked and disabled state on the row. Keep the per-instance default false and existing save/consent behavior.
3. Both text and visual-switch taps activate the same callback exactly once. Avoid an extra contentDescription that duplicates merged visible text.

## Design and contract

Apply merged semantics and toggleable Role.Switch to the existing labelled row. Make the visual child Switch non-interactive by itself.

ConfigurationContent keeps its existing checked/enabled callbacks. The privacy row uses Modifier.toggleable(value = showDetails, enabled = enabled, role = Role.Switch, onValueChange = onShowDetailsChanged); child Switch uses onCheckedChange = null.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `privacyToggleHasName` | Inspect the merged semantics tree. | Exactly one Role.Switch node contains the visible opt-in label and explanation. |
| AC-2 | `tapLabelTogglesOnce` | Tap the label row from unchecked state. | One callback receives true; no duplicate interactive switch is present. |
| AC-3 | `checkedStateIsExposed` | Render details enabled. | The single switch node is checked and names the privacy consequence. |
| AC-4 | `disabledToggleDoesNothing` | Render configuration busy/disabled and attempt activation. | No callback fires and the node reports disabled. |
| AC-5 | `localizedReadingOrder` | Read EN/FR/FA at 200% font and activate with TalkBack. | Label, consequence and state are understandable; touch targets remain at least 48 dp. |

## Scope boundary

No privacy wording expansion, default change or launcher rendering change.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

