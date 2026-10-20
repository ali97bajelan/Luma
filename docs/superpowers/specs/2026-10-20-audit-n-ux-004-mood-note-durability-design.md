# UX-004 — Unbounded Mood notes are copied into saved instance state

**Status:** Draft specification; implementation has not started. Proposed decisions below are reviewable defaults, not previously approved product policy.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-004](../../UI_UX_AUDIT-20261020N.md#ux-004--unbounded-mood-notes-are-copied-into-saved-instance-state)
**Priority / effort / confidence:** P1 — High / Medium / Medium (copied from the static audit; not a delivery estimate).
**Affected surface:** Mood editor
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-004-mood-note-durability.md)
**Delivery phase:** 1 — P1 corrections

## Intent and success

Bound new Mood notes while keeping accepted and legacy long text recoverable outside the instance-state Bundle. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

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

1. 5,000 UTF-16 units is a proposed policy for this draft spec, not an existing requirement. Reject an entire over-limit edit with inline feedback; retain previous accepted text and never silently truncate.
2. Persist full draft snapshots through serialized atomic private-file writes; mark a revision durable only after the write succeeds. Failed writes retain visible text, expose Retry and block a misleading safe-close claim.
3. Existing/restored longer notes remain readable and copyable; allow unchanged legacy notes and progressive shortening, reject growth above the limit. Apply the same change policy at repository create/update boundaries; do not cap MoodDetails or backup reads.
4. Migrate a legacy SavedStateHandle note once into the store and clear the Bundle payload only after success. Explicit discard removes the matching draft. Save/delete retain a durable completion snapshot until the caller acknowledges the result, then remove only that snapshot; lifecycle cancellation alone does not delete it.

## Design and contract

Introduce a pure domain note-change policy and an app-private atomic draft store. Keep only draft identity and small navigation metadata in SavedStateHandle; preserve full text in the private store.

Proposed MoodNotePolicy.LIMIT = 5_000 (UTF-16 units); canAcceptMoodNote(previous: String, next: String): Boolean accepts next.length <= LIMIT, unchanged text, or strictly shorter legacy text. MoodDraftStore has suspend read(id: UUID): MoodDraftSnapshot?, write(id: UUID, snapshot: MoodDraftSnapshot): Unit, remove(id: UUID): Unit. MoodDraftSnapshot contains draft: MoodDraft, revision: Long, pendingDeleteId: EventId? = null, completion: MoodDraftCompletion? = null. MoodDraftCompletion(eventId: EventId, deleted: Boolean) records the durable terminal result. Load checks completion before other dependencies; save/delete retain this snapshot until caller acknowledgement, then remove it.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `noteLimitRetainsAcceptedText` | Accept 5,000 UTF-16 units, then attempt 5,001. | 5,000 units remain unchanged and an inline limit error appears; a surrogate pair counts as two units. |
| AC-2 | `legacyNoteCanBeReadAndShortened` | Load 12,000 units, save unchanged or shorten to 10,000; try expanding to 12,001. | Read/copy and allowed edits succeed; growth is rejected without truncation. |
| AC-3 | `restorationUsesPrivateSnapshot` | Persist 100,000 legacy units, recreate the ViewModel with only draft ID metadata. | Full text restores; the new saved-state payload has no note string. |
| AC-4 | `failedWriteDoesNotLoseDraft` | The atomic write fails, or revision N finishes after an N+1 request. | Visible latest text is retained, Retry is offered and no older snapshot replaces a newer durable revision. |
| AC-5 | `draftLifecycleIsScoped` | Discard one draft, or save/delete and acknowledge its result, while another exists; interrupt migration before write success. | Only the discarded or acknowledged completed draft is removed; unacknowledged completion remains recoverable, old Bundle text survives failed migration and the other draft remains. |

## Scope boundary

No limit on legacy read models, destructive truncation, cloud draft storage or general editor-storage rewrite.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

