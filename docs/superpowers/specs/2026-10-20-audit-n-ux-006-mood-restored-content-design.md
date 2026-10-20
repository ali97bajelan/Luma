# UX-006 — Restored Mood text disappears behind a load error

**Status:** Draft specification; implementation has not started. Proposed decisions below are reviewable defaults, not previously approved product policy.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-006](../../UI_UX_AUDIT-20261020N.md#ux-006--restored-mood-text-disappears-behind-a-load-error)
**Priority / effort / confidence:** P2 — Medium / Low / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Mood editor
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-006-mood-restored-content.md)
**Delivery phase:** 2 — recovery and truthful feedback

## Intent and success

Keep restored Mood content visible and copyable when editor preparation is loading or fails. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

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

1. Render retained note, scales and time with a recovery explanation during load/error. Unknown emotion/person IDs have honest unavailable labels; do not substitute different records.
2. Save, delete, timestamp and context mutations remain disabled until dependencies are ready. Retry must retain the current draft and must not overwrite it with baseline data.
3. Read-only text supports selecting/copying even when preparation failed. Close uses existing dirty-discard protection; do not claim inaccessible content was lost.

## Design and contract

Separate content visibility from mutation readiness in MoodEditorScreen. Use the already reconstructed draft as read-only recovery content while ready is false.

MoodEditorState gains hasRestoredDraft: Boolean, initialized from an actual recovered draft rather than empty defaults. Existing ready: Boolean still gates mutations and save. Recovery note text is selectable/readOnly, not disabled in a way that prevents copying.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `restoredNoteSurvivesLoadFailure` | Restore note text while prepareEmotions throws. | The complete retained note and a recovery message are visible. |
| AC-2 | `recoveryNoteCanBeCopied` | Focus/select the note while ready=false. | Copy remains available; editing and save remain unavailable. |
| AC-3 | `retryDoesNotResetDraft` | Retry preparation after a restored note diverges from baseline. | The restored note remains, and successful readiness enables editing. |
| AC-4 | `emptyDraftIsNotInventedRecovery` | A new capture has no saved draft and loading fails. | Show loading failure without asserting a restored draft exists. |
| AC-5 | `closeStillProtectsDirtyDraft` | Close or system Back from a failed-load restored draft. | Existing discard protection runs and no mutation is dispatched. |

## Scope boundary

No bypass of dependency loading, validation or dirty-close protection; no separate draft-storage implementation.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

